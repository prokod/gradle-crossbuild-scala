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

import com.github.prokod.gradle.crossbuild.utils.ScalaCompileTasks
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.artifacts.ProjectDependency

import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet

/**
 * Crossbuild plugin entry point
 */
class CrossBuildPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.apply('scala')

        def extension = project.extensions.create('crossBuild', CrossBuildExtension, project)

        project.task('builds') {
            doLast {
                def msg = extension.builds*.toString().join('\n')
                project.logger.info("cross build settings for $project.path\n{$msg}")
            }
        }

        project.afterEvaluate {
            resolveCrossBuildDependencies(extension)

            project.pluginManager.withPlugin('maven-publish') {
                updateCrossBuildPublications(extension)
            }
        }

        project.gradle.projectsEvaluated {
            generatePomAidingConfigurations(extension)
            applyCrossBuildTasksDependencies(extension)
            alterCrossBuildCompileTasks(extension)
        }
    }

    private static void resolveCrossBuildDependencies(CrossBuildExtension extension) {
        def main = extension.crossBuildSourceSets.container.findByName('main')
        def mainScala = (SourceDirectorySet) main.scala

        extension.resolvedBuilds.findAll { rb ->
            def scalaVersionInsights = rb.scalaVersionInsights

            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            sourceSet.java.srcDirs = main.java.getSrcDirs()

            sourceSet.scala.srcDirs = main.scala.getSrcDirs()

            def crossBuildScalaSourceDirSetScala = (SourceDirectorySet) sourceSet.scala
            crossBuildScalaSourceDirSetScala.srcDirs = mainScala.getSrcDirs()

            sourceSet.resources.srcDirs = main.resources.getSrcDirs()

            extension.project.dependencies.add(sourceSet.compileConfigurationName,
                    "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")

            //TODO: From gradle 3.4 runtime should be subtituted with runtimeClasspath
            def configurer =
                    new ResolutionStrategyConfigurer(extension.project, extension.scalaVersionsCatalog,
                            rb.scalaVersionInsights)
            configurer.applyForLinkWith([
                    (sourceSet.compileConfigurationName):extension.project.configurations.compile,
                    (sourceSet.compileClasspathConfigurationName):extension.project.configurations.compileClasspath,
                    (sourceSet.compileOnlyConfigurationName):extension.project.configurations.compileOnly,
                    (sourceSet.runtimeConfigurationName):extension.project.configurations.runtime])

            //TODO: add tests to cover adding external configurations scenarios
            def configs = extension.project.configurations.findAll { it.name.startsWith('test') } +
                    extension.configurations
            configurer.applyFor(configs)
        }
    }

    private static void generatePomAidingConfigurations(CrossBuildExtension extension) {
        def sv = ScalaVersions.withDefaultsAsFallback(extension.scalaVersionsCatalog)
        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def pomAidingConfigurations =
                    new PomAidingConfigurations(extension.project, sourceSet, rb.scalaVersionInsights, sv,
                            rb.archive.appendix)
            pomAidingConfigurations.createAndSetForMavenScope(ScopeType.COMPILE)
            pomAidingConfigurations.createAndSetForMavenScope(ScopeType.PROVIDED)
        }
    }

    private static void applyCrossBuildTasksDependencies(CrossBuildExtension extension) {
        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def di = DependencyInsights.from(extension.project, sourceSet)
            def dependencies = di.extractCrossBuildProjectTypeDependencies()

            applyCrossBuildTasksDependencyPerSourceSet(extension.project, sourceSet, dependencies)
        }
    }

    /**
     * Should be called in {@code projectsEvaluated} phase as {@code getCrossBuildProjectTypeDependenciesFor} output
     * is highly influenced by that and brings the intended set of dependencies to the table.
     *
     * @param extension
     * @param resolvedBuilds
     */
    private static void alterCrossBuildCompileTasks(CrossBuildExtension extension) {
        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def di = DependencyInsights.from(extension.project, sourceSet)
            def projectTypeDependencies = di.extractCrossBuildProjectTypeDependencies()
            def allDependencies = di.findAllDependenciesForCurrentConfiguration()

            ScalaCompileTasks.tuneCrossBuildScalaCompileTask(extension.project,
                    sourceSet, projectTypeDependencies, allDependencies)
        }
    }

    private static void applyCrossBuildTasksDependencyPerSourceSet(Project project, SourceSet sourceSet,
                                                                   Set<ProjectDependency> dependencies) {
        if (dependencies.size() > 0) {
            def jarTask = project.tasks.findByName(sourceSet.getJarTaskName())
            def jarTasks = dependencies.collect { it.dependencyProject.tasks.findByName(sourceSet.getJarTaskName()) }
            jarTask?.dependsOn(jarTasks)

            def scalaCompileTask = project.tasks.findByName(sourceSet.getCompileTaskName('scala'))
            def scalaCompileTasks = dependencies.collect {
                it.dependencyProject.tasks.findByName(sourceSet.getCompileTaskName('scala'))
            }
            scalaCompileTask?.dependsOn(scalaCompileTasks)

            def javaCompileTask = project.tasks.findByName(sourceSet.getCompileJavaTaskName())
            def javaCompileTasks = dependencies.collect {
                it.dependencyProject.tasks.findByName(sourceSet.getCompileJavaTaskName())
            }
            javaCompileTask?.dependsOn(javaCompileTasks)

            scalaCompileTask?.dependsOn(jarTasks)
            javaCompileTask?.dependsOn(jarTasks)

            project.logger.debug(LoggerUtils.logTemplate(project,
                    lifecycle:'afterEvaluate',
                    sourceset:sourceSet.name,
                    msg:'Created cross build tasks inter dependencies:\n' +
                            "$jarTask.project.name:${jarTask.name} -> " +
                            "${jarTasks.collect { "$it.project.name:$it.name" }.join(', ')}\n" +
                            "${scalaCompileTask.project.name}:${scalaCompileTask.name} -> " +
                            "${scalaCompileTasks.collect { "$it.project.name:$it.name" }.join(', ')}\n" +
                            "${scalaCompileTask.project.name}:${scalaCompileTask.name} -> " +
                            "${jarTasks.collect { "$it.project.name:$it.name" }.join(', ')}\n" +
                            "${javaCompileTask.project.name}:${javaCompileTask.name} -> " +
                            "${jarTasks.collect { "$it.project.name:$it.name" }.join(', ')}"
            ))
        }
    }

    private static void updateCrossBuildPublications(CrossBuildExtension extension) {
        def project = extension.project
        def publishing = project.extensions.findByType(PublishingExtension)
        def sv = ScalaVersions.withDefaultsAsFallback(extension.scalaVersionsCatalog)

        extension.resolvedBuilds.findAll { ResolvedBuildAfterEvalLifeCycle rb ->
            def (String sourceSetId, SourceSet sourceSet) =
                    extension.crossBuildSourceSets.findByName(rb.name)

            def pomAidingConfigurations =
                    new PomAidingConfigurations(project, sourceSet, rb.scalaVersionInsights, sv)
            def pomAidingCompileScopeConfigName =
                    pomAidingConfigurations.mavenScopeConfigurationNameFor(ScopeType.COMPILE)
            def pomAidingProvidedScopeConfigName =
                    pomAidingConfigurations.mavenScopeConfigurationNameFor(ScopeType.PROVIDED)

            publishing.publications.all { MavenPublication pub ->
                if (pub instanceof MavenPublication && probablyRelatedPublication(pub, rb, sourceSetId)) {
                    def jarBaseName = project.tasks.findByName(sourceSet.jarTaskName).baseName
                    pub.artifactId = jarBaseName

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
