/*
 * Copyright 2016-2017 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.prokod.gradle.crossbuild.rules

import static com.github.prokod.gradle.crossbuild.PomAidingConfigurations.*

import com.github.prokod.gradle.crossbuild.BridgingExtension
import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import com.github.prokod.gradle.crossbuild.PomAidingConfigurations
import com.github.prokod.gradle.crossbuild.ResolutionStrategyConfigurer
import com.github.prokod.gradle.crossbuild.ScalaVersions
import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.model.CrossBuild
import com.github.prokod.gradle.crossbuild.model.TargetVerItem
import com.github.prokod.gradle.crossbuild.utils.CrossBuildPluginUtils
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.model.*

/**
 * Plugin's rules
 */
class CrossBuildPluginRules extends RuleSource {

    @SuppressWarnings(['EmptyMethod', 'UnusedMethodParameter'])
    @Model
    void crossBuild(CrossBuild crossBuild) {
    }

    @Defaults
    void setDefaultVersions(CrossBuild crossBuild) {
        crossBuild.scalaVersions = ScalaVersions.DEFAULT_SCALA_VERSIONS
    }

    @Defaults
    void setDefaultArchiveAppendix(@Each TargetVerItem item) {
        item.archiveAppendix = '_?'
    }

    @Mutate
    void setProjectViaBridge(CrossBuild crossBuild, ExtensionContainer extensions) {
        def extension = (BridgingExtension) extensions.bridging
        def project = extension.project
        crossBuild.project = project
        crossBuild.archivesBaseName = project.archivesBaseName
    }

    @Mutate
    void setTargetVerItems(
            @Each TargetVerItem targetVersion,
            @Path('crossBuild.archivesBaseName') String archivesBaseName,
            @Path('crossBuild.scalaVersions') ScalaVersions scalaVersions) {
        trySettingDefaultValue(targetVersion, scalaVersions)
        validateTargetVersion(targetVersion)
        def scalaVersionInsights = new ScalaVersionInsights(targetVersion.value, scalaVersions)
        def interpretedBaseName = generateCrossArchivesBaseName(
                archivesBaseName, targetVersion.archiveAppendix, scalaVersionInsights.artifactInlinedVersion)
        targetVersion.artifactId = interpretedBaseName
    }

    @Mutate
    void realizeCrossBuildTasks(ModelMap<Task> tasks, CrossBuild crossBuild) {
        def project = crossBuild.project

        def crossBuildSourceSets = new CrossBuildSourceSets(crossBuild)
        crossBuildSourceSets.reset()

        def main = crossBuildSourceSets.container.findByName('main')
        def mainScala = (SourceDirectorySet) main.scala

        crossBuild.targetVersions.findAll { targetVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(targetVersion.value, crossBuild.scalaVersions)

            def (String sourceSetId, SourceSet sourceSet) = crossBuildSourceSets.findByVersion(scalaVersionInsights)

            sourceSet.java.srcDirs = main.java.getSrcDirs()

            def crossBuildScalaSourceDirSetScala = (SourceDirectorySet) sourceSet.scala
            crossBuildScalaSourceDirSetScala.srcDirs = mainScala.getSrcDirs()

            sourceSet.resources.srcDirs = main.resources.getSrcDirs()

            project.dependencies.add(sourceSet.compileConfigurationName,
                    "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")

            //TODO: From gradle 3.4 runtime should be subtituted with runtimeClasspath
            def configurer = new ResolutionStrategyConfigurer(project, crossBuild.scalaVersions, scalaVersionInsights)
            configurer.applyForLinkWith([
                    (sourceSet.compileConfigurationName):project.configurations.compile,
                    (sourceSet.compileClasspathConfigurationName):project.configurations.compileClasspath,
                    (sourceSet.compileOnlyConfigurationName):project.configurations.compileOnly,
                    (sourceSet.runtimeConfigurationName):project.configurations.runtime])

            def configs = project.configurations.findAll { it.name.startsWith('test') } +
                    crossBuild.dependencyResolution?.includes
            configurer.applyFor(configs)

            def pomAidingConfigurations =
                    new PomAidingConfigurations(project, sourceSet, scalaVersionInsights, targetVersion.archiveAppendix)
            pomAidingConfigurations.createAndSetForMavenScope(ScopeType.COMPILE)
            pomAidingConfigurations.createAndSetForMavenScope(ScopeType.PROVIDED)

            def interpretedBaseName = generateCrossArchivesBaseName(
                    crossBuild.archivesBaseName, targetVersion.archiveAppendix,
                    scalaVersionInsights.artifactInlinedVersion)
            project.logger.info(LoggerUtils.logTemplate(project,
                    "Cross build jar ${sourceSetId} baseName = '${interpretedBaseName}'"))

            tasks.create(sourceSet.getJarTaskName(), Jar) {
                group = BasePlugin.BUILD_GROUP
                description = 'Assembles a jar archive containing ' +
                        "${scalaVersionInsights.strippedArtifactInlinedVersion} classes"
                baseName = interpretedBaseName
                from sourceSet.output
            }

            tasks.withType(ScalaCompile) { t ->
                if (t.name == sourceSet.getCompileTaskName('scala')) {
                    def analysisFile = t.scalaCompileOptions.incrementalOptions.analysisFile
                    if (!analysisFile) {
                        t.scalaCompileOptions.incrementalOptions.analysisFile = new File(
                                "$project.buildDir/tmp/scala/compilerAnalysis/" +
                                        "${scalaVersionInsights.compilerVersion}/${project.name}.analysis")
                    }
                }
            }
        }
    }

    @Mutate
    void updateCrossBuildPublications(PublishingExtension publishing, CrossBuild crossBuild) {
        def project = crossBuild.project
        def crossBuildSourceSets = new CrossBuildSourceSets(crossBuild)

        crossBuild.targetVersions.findAll { targetVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(targetVersion.value, crossBuild.scalaVersions)
            def (String sourceSetId, SourceSet sourceSet) = crossBuildSourceSets.findByVersion(scalaVersionInsights)

            def pomAidingConfigurations = new PomAidingConfigurations(project, sourceSet, scalaVersionInsights)
            def pomAidingCompileScopeConfigName =
                    pomAidingConfigurations.mavenScopeConfigurationNameFor(ScopeType.COMPILE)
            def pomAidingProvidedScopeConfigName =
                    pomAidingConfigurations.mavenScopeConfigurationNameFor(ScopeType.PROVIDED)

            publishing.publications.all { MavenPublication pub ->
                if (pub instanceof MavenPublication && probablyRelatedPublication(pub, targetVersion, sourceSetId)) {
                    pub.pom.withXml {
                        withXmlHandler(it, pomAidingCompileScopeConfigName, ScopeType.COMPILE, project)
                    }
                    pub.pom.withXml {
                        withXmlHandler(it, pomAidingProvidedScopeConfigName, ScopeType.PROVIDED, project)
                    }
                }
            }
        }
    }

    private static boolean probablyRelatedPublication(MavenPublication pub,
                                                       TargetVerItem targetVersion,
                                                       String sourceSetId) {
        pub.artifactId == targetVersion.artifactId || pub.name.contains(sourceSetId)
    }

    private static void withXmlHandler(XmlProvider xmlProvider,
                                       String pomAidingConfigName,
                                       ScopeType scopeType,
                                       Project project) {
        def dependenciesNodeFunction = { XmlProvider xml ->
            def dependenciesNode = xml.asNode()['dependencies']?.getAt(0)
            if (dependenciesNode == null) {
                return xmlProvider.asNode().appendNode('dependencies')
            }
            dependenciesNode
        }

        def dependenciesNode = dependenciesNodeFunction(xmlProvider)

        project.configurations[pomAidingConfigName].allDependencies.each { dep ->
            def dependencyNode = dependenciesNode.appendNode('dependency')
            dependencyNode.appendNode('groupId', dep.group)
            dependencyNode.appendNode('artifactId', dep.name)
            dependencyNode.appendNode('version', dep.version)
            dependencyNode.appendNode('scope', scopeType.toString().toLowerCase())
        }
    }

    private static boolean trySettingDefaultValue(TargetVerItem targetVersion, ScalaVersions scalaVersions) {
        if (targetVersion.value == null) {
            scalaVersions.catalog.collect { new ScalaVersionInsights(it.value) }.each { versionInsights ->
                if (targetVersion.name.toLowerCase() == "v${versionInsights.strippedArtifactInlinedVersion}") {
                    targetVersion.value = versionInsights.artifactInlinedVersion
                    return true
                }
            }
        }
        false
    }

    /**
     * Validation of the value for a given {@link TargetVerItem} instance.
     *
     * @param targetVersion Target version item created using Gradle model DSL
     */
    @SuppressWarnings('TrailingWhitespace')
    private static void validateTargetVersion(TargetVerItem targetVersion) {
        def message = """\
            Property value is not set. Set a value in the model configuration.

            Example:
            -------
            - by convention
            
            model {
                crossBuild {
                    V211(ScalaVer)
                    ...
                }
            }
            
            or
            
            model {
                crossBuild {
                    v211(ScalaVer)
                    ...
                }
            }
            
            - custom naming
          
            model {
                crossBuild {
                    A(ScalaVer) {
                        value = '2.11'
                    }
                    ...
                }
            }
            """.stripIndent()
        CrossBuildPluginUtils.assertWithMsg(message) {
            assert targetVersion.value != null
            assert targetVersion.value ==~ /^(\d+\.)?(\d+\.)?(\d+)$/
        }
    }

    /**
     * Generates archives base name based on 'archivesBaseName', archiveAppendix which might include '?' placeholder and
     *  'artifactInlinedVersion' which will be used to fill '?' placeholder.
     *
     * @param archivesBaseName Name of archive prefixing '_' For example in lib... => 'lib'
     * @param archiveAppendix For example in lib_? => '_?'
     * @param artifactInlinedVersion Scala convention inlined version For example '2.11'
     * @return Interpreted archivesBaseName
     */
    private static String generateCrossArchivesBaseName(String archivesBaseName,
                                                              String archiveAppendix,
                                                              String artifactInlinedVersion) {
        "${archivesBaseName}${CrossBuildPluginUtils.qmarkReplace(archiveAppendix, artifactInlinedVersion)}".toString()
    }

    /**
     * Tries to detect and substitute mismatched scala based dependencies.
     * This can happen when default configurations (compile, compileOnly ...) "pollute"
     * cross build configuration, which inherits from them,
     * with mismatched scala version dependencies.
     *
     * @param details {@link DependencyResolveDetails} from resolution strategy
     * @param dependencies All dependencies of the specified cross build configuration.
     * @param scalaVersionInsights Holds all version permutations for a specific Scala version
     * @return true when target was updated, false when not.
     */
    @SuppressWarnings('UnusedPrivateMethod')
    private static boolean tryUpdatingTargetNameVersion(
            DependencyResolveDetails details,
            DependencySet dependencies,
            ScalaVersionInsights scalaVersionInsights) {
        def requested = details.requested
        def underscoreIndex = requested.name.indexOf('_')
        if (underscoreIndex > 0) {
            def baseName = requested.name[0..underscoreIndex - 1]
            def matchingDeps = dependencies.findAll {
                it.group == requested.group && it.name.startsWith(baseName)
            }
            def supposedRequestedScalaVersion = requested.name[underscoreIndex + 1..-1]
            def sameVersionDeps = matchingDeps.findAll {
                it.name.endsWith("_$supposedRequestedScalaVersion")
            }
            if (matchingDeps.size() == 2 && sameVersionDeps.size() == 1 &&
                    supposedRequestedScalaVersion != scalaVersionInsights.artifactInlinedVersion) {
                def correctDep = (matchingDeps - sameVersionDeps).first()
                details.useTarget correctDep.group + ':' + correctDep.name + ':' + correctDep.version
                return true
            }
            false
        }
    }

}
