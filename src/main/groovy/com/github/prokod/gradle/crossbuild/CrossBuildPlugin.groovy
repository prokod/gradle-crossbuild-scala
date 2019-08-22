/*
 * Copyright 2016-2019 the original author or authors
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
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildPomTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildsReportTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildsClasspathResolvedConfigurationReportTask
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsights
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsights.ViewType
import org.gradle.api.publish.maven.tasks.GenerateMavenPom

import com.github.prokod.gradle.crossbuild.utils.ScalaCompileTasks
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

/**
 * Crossbuild plugin entry point
 */
class CrossBuildPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.apply('scala')

        def extension = project.extensions.create('crossBuild', CrossBuildExtension, project)

        project.task(type:CrossBuildsReportTask,
                "${AbstractCrossBuildsReportTask.BASE_TASK_NAME}ResolvedDsl") { CrossBuildsReportTask t ->
            t.resolvedBuilds = extension.resolvedBuilds

            t.description = 'Summary report for ross building resolved Dsl'
        }

        project.task(type:CrossBuildsClasspathResolvedConfigurationReportTask,
                "${AbstractCrossBuildsReportTask.BASE_TASK_NAME}ResolvedConfigs") { t ->
            t.extension = extension

            t.description = 'Summary report for cross building resolved Configurations'
        }

        project.afterEvaluate {
            updateSourceBuildSourceSets(extension)
        }

        project.gradle.projectsEvaluated {
            assignCrossBuildDependencyResolutionStrategy(extension)

            generateNonDefaultProjectTypeDependencies(extension)

            project.pluginManager.withPlugin('maven-publish') {
                generateCrossBuildPomTasks(extension)
            }

            alterCrossBuildCompileTasks(extension)
        }
    }

    private static void updateSourceBuildSourceSets(CrossBuildExtension extension) {
        extension.resolvedBuilds.findAll { rb ->
            def scalaVersionInsights = rb.scalaVersionInsights

            def sourceSet = getAndUpdateSourceSetFor(rb, extension.crossBuildSourceSets)

            //todo maybe not needed anymore (might not be as helpful as expected)
            // Mainly here to help with creation of the correct dependencies for pom creation
            // see ResolutionStrategyConfigurer::assemble3rdPartyDependencies
            extension.project.dependencies.add(sourceSet.compileConfigurationName,
                    "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")
        }
    }

    private static void assignCrossBuildDependencyResolutionStrategy(CrossBuildExtension extension) {
        def main = extension.crossBuildSourceSets.container.findByName('main')

        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def sourceSetInsights = new SourceSetInsights(sourceSet, main, extension.project)
            def configurer =
                    new ResolutionStrategyConfigurer(sourceSetInsights, extension.scalaVersionsCatalog,
                            rb.scalaVersionInsights)

            configurer.applyForLinkWith(
                    ViewType.COMPILE,
                    ViewType.IMPLEMENTATION,
                    ViewType.COMPILE_CLASSPATH,
                    ViewType.RUNTIME_CLASSPATH,
                    ViewType.COMPILE_ONLY,
                    ViewType.RUNTIME,
                    ViewType.RUNTIME_ONLY)

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

    private static void generateCrossBuildPomTasks(CrossBuildExtension extension) {
        extension.resolvedBuilds.findAll { rb ->
            extension.project.tasks.withType(GenerateMavenPom).all { GenerateMavenPom pomTask ->
                def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

                def foundRelated =
                        CrossBuildPomTask.probablyRelatedPublicationTask(pomTask.name, sourceSetId)
                if (foundRelated) {
                    def taskName = sourceSet.getTaskName('update', 'pom')
                    def task = extension.project.tasks.create(taskName, CrossBuildPomTask) {
                        resolvedBuild = rb
                    }
                    pomTask.dependsOn(task)
                }
            }
        }
    }

    /**
     * Both 'compile' and 'compileOnly' contribute to 'compileClasspath'. Because they are not in extendsFrom relations
     * between themselves, Both should be treated.
     *
     * @param extension
     */
    private static void generateNonDefaultProjectTypeDependencies(CrossBuildExtension extension) {
        def main = extension.crossBuildSourceSets.container.findByName('main')
        def sv = ScalaVersions.withDefaultsAsFallback(extension.scalaVersionsCatalog)
        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def sourceSetInsights = new SourceSetInsights(sourceSet, main, extension.project)
            def di = new DependencyInsights(sourceSetInsights)

            //todo add other needed configuration types (except COMPILE)
            // for 'implementation' configuration
            di.addMainConfigurationToCrossBuildCounterPart(ViewType.IMPLEMENTATION, sv)
            // for 'compileOnly' configuration
            di.addMainConfigurationToCrossBuildCounterPart(ViewType.COMPILE_ONLY, sv)

            // for 'compile' configuration
            di.addDefaultConfigurationsToCrossBuildConfigurationRecursive(ViewType.COMPILE)
            di.generateAndWireCrossBuildProjectTypeDependencies(ViewType.COMPILE, ViewType.IMPLEMENTATION)
            di.generateAndWireCrossBuildProjectTypeDependencies(ViewType.IMPLEMENTATION, ViewType.IMPLEMENTATION)
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

            ScalaCompileTasks.tuneCrossBuildScalaCompileTask(extension.project, sourceSet)
        }
    }
}
