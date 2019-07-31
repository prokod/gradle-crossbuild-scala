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

import com.github.prokod.gradle.crossbuild.tasks.AbstractCrossBuildsReportTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildsReportTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildsClasspathResolvedConfigurationReportTask

import static com.github.prokod.gradle.crossbuild.PomAidingConfigurations.*

import com.github.prokod.gradle.crossbuild.utils.ScalaCompileTasks
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
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

        project.task(type: CrossBuildsReportTask,
                "${AbstractCrossBuildsReportTask.BASE_TASK_NAME}ResolvedDsl") { CrossBuildsReportTask t ->
            t.resolvedBuilds = extension.resolvedBuilds

            t.description = 'Summary report for ross building resolved Dsl'
        }

        project.task(type: CrossBuildsClasspathResolvedConfigurationReportTask,
                "${AbstractCrossBuildsReportTask.BASE_TASK_NAME}ResolvedConfigs") { t ->
            t.extension = extension

            t.description = 'Summary report for cross building resolved Configurations'
        }

        project.afterEvaluate {
            updateSourceBuildSourceSets(extension)
//            assignCrossBuildDependencyResolutionStrategy(extension)

//            generateNonDefaultProjectTypeDependencies(extension)

        }

        project.gradle.projectsEvaluated {
            assignCrossBuildDependencyResolutionStrategy(extension)

            generateNonDefaultProjectTypeDependencies(extension)

//            generatePomAidingConfigurations(extension)

//            applyCrossBuildTasksDependencies(extension)

//            project.pluginManager.withPlugin('maven-publish') {
//                updateCrossBuildPublications(extension)
//            }

            alterCrossBuildCompileTasks(extension)
        }

        project.gradle.taskGraph.whenReady {
            project.pluginManager.withPlugin('maven-publish') {
                generatePomAidingConfigurations(extension)

                updateCrossBuildPublications(extension)
            }
//            showResolvingOutcome(extension)
        }
    }

    private static void updateSourceBuildSourceSets(CrossBuildExtension extension) {
        extension.resolvedBuilds.findAll { rb ->
            def scalaVersionInsights = rb.scalaVersionInsights

            def sourceSet = getAndUpdateSourceSetFor(rb, extension.crossBuildSourceSets)

            // Mainly here to help with creation of the correct dependencies for pom creation
            // see ResolutionStrategyConfigurer::assemble3rdPartyDependencies
            extension.project.dependencies.add(sourceSet.compileConfigurationName,
                    "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")
        }
    }

    private static void assignCrossBuildDependencyResolutionStrategy(CrossBuildExtension extension) {
        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            //TODO: From gradle 3.4 runtime should be substituted with runtimeClasspath
            def configurer =
                    new ResolutionStrategyConfigurer(extension.project, extension.scalaVersionsCatalog,
                            rb.scalaVersionInsights)
            configurer.applyForLinkWith([
                    (sourceSet.compileConfigurationName):extension.project.configurations.compile,
                    (sourceSet.implementationConfigurationName):extension.project.configurations.implementation,
                    (sourceSet.compileClasspathConfigurationName):extension.project.configurations.compileClasspath,
                    (sourceSet.runtimeClasspathConfigurationName):extension.project.configurations.runtimeClasspath,
                    (sourceSet.compileOnlyConfigurationName):extension.project.configurations.compileOnly,
                    (sourceSet.runtimeConfigurationName):extension.project.configurations.runtime,
                    (sourceSet.runtimeOnlyConfigurationName):extension.project.configurations.runtimeOnly])

            //TODO: add tests to cover adding external configurations scenarios
            def configs = extension.project.configurations.findAll { it.name.startsWith('test') } +
                    extension.configurations
            configurer.applyFor(configs)
        }
    }

    private static SourceSet getAndUpdateSourceSetFor(ResolvedBuildAfterEvalLifeCycle rb,
                                      CrossBuildSourceSets crossBuildSourceSets) {
        def main = crossBuildSourceSets.container.findByName('main')

        def (String sourceSetId, SourceSet sourceSet) = crossBuildSourceSets.findByName(rb.name)

        // Assign main java source-set to cross-build java source-set
        sourceSet.java.srcDirs = main.java.getSrcDirs()

        // Assign main scala source-set to cross-build scala source-set
        sourceSet.scala.srcDirs = main.scala.getSrcDirs()

        // Assign main resources source-set to cross-build resources source-set
        sourceSet.resources.srcDirs = main.resources.getSrcDirs()

        sourceSet
    }

    private static void generatePomAidingConfigurations(CrossBuildExtension extension) {
        def sv = ScalaVersions.withDefaultsAsFallback(extension.scalaVersionsCatalog)
        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def pomAidingConfigurations =
                    new PomAidingConfigurations(extension.project, sourceSet, rb.scalaVersionInsights, sv,
                            rb.archive.appendix)
            pomAidingConfigurations.createAndSetForMavenScope(ScopeType.COMPILE)
            pomAidingConfigurations.createAndSetForMavenScope(ScopeType.RUNTIME)

//            pomAidingConfigurations.createAndSetForMavenScope(ScopeType.PROVIDED)
        }
    }

    private static void generateNonDefaultProjectTypeDependencies(CrossBuildExtension extension) {
        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def di = DependencyInsights.from(extension.project, sourceSet)
            //di.createAndAddNonDefaultProjectTypeDependencies1(sourceSet)
            di.createAndAddNonDefaultProjectTypeDependencies1ult(sourceSet)
        }
    }

    private static void showResolvingOutcome(CrossBuildExtension extension) {
        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def crossBuildConfigurationCompile =
                    extension.project.configurations.findByName(sourceSet.compileConfigurationName)
            def crossBuildConfigurationCompileResolved = crossBuildConfigurationCompile.resolvedConfiguration
            def compileDeps = crossBuildConfigurationCompileResolved.resolvedArtifacts
            def compileFiles = crossBuildConfigurationCompileResolved.files

            extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                    lifecycle:'projectsEvaluated',
                    sourceset:sourceSet.name,
                    configuration:crossBuildConfigurationCompile.name,
                    msg:'Dependency resolution report:\n' +
                            "${crossBuildConfigurationCompile.allDependencies.join(', ')}\n" +
                            "${compileDeps*.toString().join(', ')}\n" +
                            "${compileFiles*.name.join(', ')}\n" +
                            "${crossBuildConfigurationCompileResolved.hasError()}"
            ))

            def crossBuildConfigurationCompileClasspath =
                    extension.project.configurations.findByName(sourceSet.compileClasspathConfigurationName)
            def crossBuildConfigurationCompileClasspathResolved =
                    crossBuildConfigurationCompileClasspath.resolvedConfiguration
            def compileClasspathDeps = crossBuildConfigurationCompileClasspathResolved.resolvedArtifacts
            def compileClasspathFiles = crossBuildConfigurationCompileClasspathResolved.files

            extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                    lifecycle:'projectsEvaluated',
                    sourceset:sourceSet.name,
                    configuration:crossBuildConfigurationCompileClasspath.name,
                    msg:'Dependency resolution report:\n' +
                            "${crossBuildConfigurationCompileClasspath.allDependencies.join(', ')}\n" +
                            "${compileClasspathDeps*.toString().join(', ')}\n" +
                            "${compileClasspathFiles*.name.join(', ')}\n" +
                            "${crossBuildConfigurationCompileClasspathResolved.hasError()}"

            ))

//            def crossBuildConfigurationRuntime =
//                    extension.project.configurations.findByName(sourceSet.runtimeConfigurationName)
//            def crossBuildConfigurationRuntimeResolved = crossBuildConfigurationRuntime.resolvedConfiguration
//            def runtimeDeps = crossBuildConfigurationRuntimeResolved.resolvedArtifacts
//
//            extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
//                    lifecycle:'projectsEvaluated',
//                    sourceset:sourceSet.name,
//                    configuration:crossBuildConfigurationRuntime.name,
//                    msg:'Dependency resolution report:\n' +
//                            "${runtimeDeps*.toString().join(', ')} "
//            ))
//
//            def crossBuildConfigurationCompileonly =
//                    extension.project.configurations.findByName(sourceSet.compileOnlyConfigurationName)
//            def crossBuildConfigurationCompileonlyResolved = crossBuildConfigurationCompileonly.resolvedConfiguration
//            def compileonlyDeps = crossBuildConfigurationCompileonlyResolved.resolvedArtifacts
//
//            extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
//                    lifecycle:'projectsEvaluated',
//                    sourceset:sourceSet.name,
//                    configuration:crossBuildConfigurationCompileonly.name,
//                    msg:'Dependency resolution report:\n' +
//                            "${compileonlyDeps*.toString().join(', ')} "
//            ))
        }
    }

//    private static void applyCrossBuildTasksDependencies(CrossBuildExtension extension) {
//        extension.resolvedBuilds.findAll { rb ->
//            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)
//
//            def di = DependencyInsights.from(extension.project, sourceSet)
//            def dependencies = di.extractCrossBuildProjectTypeDependencies()
//
//            applyCrossBuildTasksDependencyPerSourceSet(extension.project, sourceSet, dependencies)
//        }
//    }

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
            //def allDependencies = di.findAllDependenciesForCurrentConfiguration()

            ScalaCompileTasks.tuneCrossBuildScalaCompileTask(extension.project,
                    sourceSet, projectTypeDependencies/*, allDependencies*/)
        }
    }

//    private static void applyCrossBuildTasksDependencyPerSourceSet(Project project, SourceSet sourceSet,
//                                                                   Set<ProjectDependency> dependencies) {
//        if (dependencies.size() > 0) {
//            def jarTask = project.tasks.findByName(sourceSet.getJarTaskName())
//            def jarTasks = dependencies.collect { it.dependencyProject.tasks.findByName(sourceSet.getJarTaskName()) }
//            jarTask?.dependsOn(jarTasks)
//
//            def scalaCompileTask = project.tasks.findByName(sourceSet.getCompileTaskName('scala'))
//            def scalaCompileTasks = dependencies.collect {
//                it.dependencyProject.tasks.findByName(sourceSet.getCompileTaskName('scala'))
//            }
//            scalaCompileTask?.dependsOn(scalaCompileTasks)
//
//            def javaCompileTask = project.tasks.findByName(sourceSet.getCompileJavaTaskName())
//            def javaCompileTasks = dependencies.collect {
//                it.dependencyProject.tasks.findByName(sourceSet.getCompileJavaTaskName())
//            }
//            javaCompileTask?.dependsOn(javaCompileTasks)
//
//            scalaCompileTask?.dependsOn(jarTasks)
//            javaCompileTask?.dependsOn(jarTasks)
//
//            project.logger.debug(LoggerUtils.logTemplate(project,
//                    lifecycle:'afterEvaluate',
//                    sourceset:sourceSet.name,
//                    msg:'Created cross build tasks inter dependencies:\n' +
//                            "$jarTask.project.name:${jarTask.name} -> " +
//                            "${jarTasks.collect { "$it.project.name:$it.name" }.join(', ')}\n" +
//                            "${scalaCompileTask.project.name}:${scalaCompileTask.name} -> " +
//                            "${scalaCompileTasks.collect { "$it.project.name:$it.name" }.join(', ')}\n" +
//                            "${scalaCompileTask.project.name}:${scalaCompileTask.name} -> " +
//                            "${jarTasks.collect { "$it.project.name:$it.name" }.join(', ')}\n" +
//                            "${javaCompileTask.project.name}:${javaCompileTask.name} -> " +
//                            "${jarTasks.collect { "$it.project.name:$it.name" }.join(', ')}"
//            ))
//        }
//    }

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
            def pomAidingRuntimeScopeConfigName =
                    pomAidingConfigurations.mavenScopeConfigurationNameFor(ScopeType.RUNTIME)
//            def pomAidingProvidedScopeConfigName =
//                    pomAidingConfigurations.mavenScopeConfigurationNameFor(ScopeType.PROVIDED)

            publishing.publications.all { MavenPublication pub ->
                if (pub instanceof MavenPublication && probablyRelatedPublication(pub, rb, sourceSetId)) {
                    def jarBaseName = project.tasks.findByName(sourceSet.jarTaskName).baseName
                    pub.artifactId = jarBaseName

                    pub.pom.withXml {
                        withXmlHandler(it, pomAidingCompileScopeConfigName, ScopeType.COMPILE, project)
                    }
                    pub.pom.withXml {
                        withXmlHandler(it, pomAidingRuntimeScopeConfigName, ScopeType.RUNTIME, project)
                    }
//                    pub.pom.withXml {
//                        withXmlHandler(it, pomAidingProvidedScopeConfigName, ScopeType.PROVIDED, project)
//                    }
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
