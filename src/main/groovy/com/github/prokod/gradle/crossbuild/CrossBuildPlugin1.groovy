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
package com.github.prokod.gradle.crossbuild

import static com.github.prokod.gradle.crossbuild.PomAidingConfigurations.*

import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.scala.ScalaCompile

/**
 * Crossbuild plugin entry point
 */
class CrossBuildPlugin1 implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.apply('scala')

        def extension = project.extensions.create('crossBuild', CrossBuildExtension, project)

        project.task('builds') {
            doLast {
                def msg = extension.builds*.toString().join('\n')
                project.logger.info("cross build settings for $project.path\n{$msg}")
            }
        }

        project.gradle.projectsEvaluated {
            def sv = ScalaVersions.withDefaultsAsFallback(extension.scalaVersions)

            def fullyResolvedBuilds = extension.resolvedBuilds.collect { rb -> BuildResolver.resolve(rb, sv) }

            realizeCrossBuildTasks(extension, fullyResolvedBuilds)

            project.pluginManager.withPlugin('maven-publish') {
                updateCrossBuildPublications(extension, fullyResolvedBuilds)
            }
        }
    }

    void realizeCrossBuildTasks(CrossBuildExtension extension,
                                Collection<ResolvedBuildAfterEvalLifeCycle> resolvedBuilds) {
        def main = extension.crossBuildSourceSets.container.findByName('main')
        def mainScala = (SourceDirectorySet) main.scala

        resolvedBuilds.findAll { rb ->
            def scalaVersionInsights = rb.scalaVersionInsights

            def (String sourceSetId, SourceSet sourceSet) =
                    extension.crossBuildSourceSets.findByVersion(rb.scalaVersionInsights)

            sourceSet.java.srcDirs = main.java.getSrcDirs()

            def crossBuildScalaSourceDirSetScala = (SourceDirectorySet) sourceSet.scala
            crossBuildScalaSourceDirSetScala.srcDirs = mainScala.getSrcDirs()

            sourceSet.resources.srcDirs = main.resources.getSrcDirs()

            extension.project.dependencies.add(sourceSet.compileConfigurationName,
                    "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")

            //TODO: From gradle 3.4 runtime should be subtituted with runtimeClasspath
            def configurer =
                    new ResolutionStrategyConfigurer(extension.project, extension.scalaVersions,
                            rb.scalaVersionInsights)
            configurer.applyForLinkWith([
                    (sourceSet.compileConfigurationName):extension.project.configurations.compile,
                    (sourceSet.compileClasspathConfigurationName):extension.project.configurations.compileClasspath,
                    (sourceSet.compileOnlyConfigurationName):extension.project.configurations.compileOnly,
                    (sourceSet.runtimeConfigurationName):extension.project.configurations.runtime])

            //TODO: add back possibility for adding external configurations
            def configs = extension.project.configurations.findAll { it.name.startsWith('test') }
                          //+ crossBuild.dependencyResolution?.includes
            configurer.applyFor(configs)

            def pomAidingConfigurations =
                    new PomAidingConfigurations(extension.project, sourceSet, rb.scalaVersionInsights,
                            rb.archive.appendix)
            pomAidingConfigurations.createAndSetForMavenScope(PomAidingConfigurations.ScopeType.COMPILE)
            pomAidingConfigurations.createAndSetForMavenScope(PomAidingConfigurations.ScopeType.PROVIDED)

            extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                    "Creating crossbuild Jar task for sourceSet ${sourceSetId}." +
                            " [Resolved Jar baseName appendix: ${rb.archive.appendix}]"))

            extension.project.tasks.create(sourceSet.getJarTaskName(), Jar) {
                group = BasePlugin.BUILD_GROUP
                description = 'Assembles a jar archive containing ' +
                        "${scalaVersionInsights.strippedArtifactInlinedVersion} classes"
                baseName = baseName + rb.archive.appendix
                from sourceSet.output
            }

            extension.project.tasks.withType(ScalaCompile) { ScalaCompile t ->
                if (t.name == sourceSet.getCompileTaskName('scala')) {
                    def analysisFile = t.scalaCompileOptions.incrementalOptions.analysisFile
                    if (!analysisFile) {
                        t.scalaCompileOptions.incrementalOptions.analysisFile = new File(
                                "$extension.project.buildDir/tmp/scala/compilerAnalysis/" +
                                        "${scalaVersionInsights.compilerVersion}/${extension.project.name}.analysis")
                    }
                }
            }
        }
    }

    void updateCrossBuildPublications(CrossBuildExtension extension,
                                      Collection<ResolvedBuildAfterEvalLifeCycle> resolvedBuilds) {
        def project = extension.project
        def publishing = project.extensions.findByType(PublishingExtension)

        resolvedBuilds.findAll { ResolvedBuildAfterEvalLifeCycle targetVersion ->
            def (String sourceSetId, SourceSet sourceSet) =
                    extension.crossBuildSourceSets.findByVersion(targetVersion.scalaVersionInsights)

            def pomAidingConfigurations =
                    new PomAidingConfigurations(project, sourceSet, targetVersion.scalaVersionInsights)
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
                                                      ResolvedBuildAfterEvalLifeCycle targetVersion,
                                                      String sourceSetId) {
        pub.artifactId.endsWith(targetVersion.archive.appendix) || pub.name.contains(sourceSetId)
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
}
