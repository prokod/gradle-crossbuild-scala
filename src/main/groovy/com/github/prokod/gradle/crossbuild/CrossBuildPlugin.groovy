/*
 * Copyright 2016-2022 the original author or authors
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

import com.github.prokod.gradle.crossbuild.model.DependencyLimitedInsight
import com.github.prokod.gradle.crossbuild.tasks.AbstractCrossBuildsReportTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildPomTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildsReportTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildsClasspathResolvedConfigurationReportTask
import com.github.prokod.gradle.crossbuild.utils.CrossBuildPluginUtils
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.UniDependencyInsights
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsights
import com.github.prokod.gradle.crossbuild.utils.ViewType
import com.github.prokod.gradle.crossbuild.utils.UniSourceSetInsights
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.publish.maven.tasks.GenerateMavenPom

import com.github.prokod.gradle.crossbuild.utils.ScalaCompileTasks
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

import javax.inject.Inject

/**
 * Crossbuild plugin entry point
 */
class CrossBuildPlugin implements Plugin<Project> {
    private final ObjectFactory objectFactory

    @Inject
    CrossBuildPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory
    }

    void apply(Project project) {
        project.pluginManager.apply('scala')
        project.pluginManager.apply('java-library')

        def extension = project.extensions.create('crossBuild',
                CrossBuildExtension,
                project,
                objectFactory)

        project.task(type:CrossBuildsReportTask,
                "${AbstractCrossBuildsReportTask.BASE_TASK_NAME}ResolvedDsl") { CrossBuildsReportTask t ->
            t.resolvedBuilds = extension.resolvedBuilds

            t.description = 'Summary report for cross building resolved Dsl'
        }

        project.task(type:CrossBuildsClasspathResolvedConfigurationReportTask,
                "${AbstractCrossBuildsReportTask.BASE_TASK_NAME}ResolvedConfigs") { t ->
            t.extension = extension

            t.description = 'Summary report for cross building resolved Configurations'
        }

        project.task(type:DefaultTask,
                "${AbstractCrossBuildsReportTask.BASE_TASK_NAME}Assemble") { DefaultTask t ->
            t.description = 'Assembles all the ' +
                    AbstractCrossBuildsReportTask.BASE_TASK_NAME + ' outputs of this project'
            t.group = AbstractCrossBuildsReportTask.TASK_GROUP
        }

        project.afterEvaluate {
            updateSourceBuildSourceSets(extension)

            globDependencyTranslationForMainSourceSetsConfigurations(extension)
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
        extension.resolvedBuilds.each { rb ->
            def scalaVersionInsights = rb.scalaVersionInsights

            def sourceSet = getAndUpdateSourceSetFor(rb, extension.crossBuildSourceSets)

            def sourceSetInsight = new UniSourceSetInsights(sourceSet, extension.project)

            // 1. Adds scala-lib dependency to all sub projects (User convenience)
            // 2. Helps with the creation of the correct dependencies for pom creation
            // see ResolutionStrategyConfigurer::assemble3rdPartyDependencies
            extension.project.dependencies.add(sourceSetInsight.getCompileName(),
                    "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")
        }
    }

    @SuppressWarnings(['ClosureAsLastMethodParameter'])
    private static void globDependencyTranslationForMainSourceSetsConfigurations(CrossBuildExtension extension) {
        // Find main source set
        def main = extension.crossBuildSourceSets.container.findByName('main')

        def mainSourceSetInsights = new UniSourceSetInsights(main, extension.project)

        def allNonTestRelatedUserFacingViews =
                ViewType.filterViewsBy({ tags -> tags.contains('canBeConsumed') }, { tags -> !tags.contains('test') })

        // Create scalaVersions to be used in parseByDependencyName and more
        def scalaVersions = ScalaVersions.withDefaultsAsFallback(extension.scalaVersionsCatalog)
        def di = new UniDependencyInsights(mainSourceSetInsights)
        def possibleDefaultScalaVersions = di.findScalaVersions(scalaVersions)

        ViewType.filterViewsBy({ tags -> tags.contains('canBeConsumed') }).each { viewType ->
            def mainConfig = mainSourceSetInsights.getConfigurationFor(viewType)
            // Iterate dependencies in compile configuration
            def globTypeDeps = mainConfig.dependencies.findAll { dep ->
                def dependencyInsight = DependencyLimitedInsight.parseByDependencyName(dep.name, scalaVersions)
                dependencyInsight.supposedScalaVersion == '?'
            }

            // Flatten each glob type dependency
            globTypeDeps.each { dep ->
                def dependencyInsight = DependencyLimitedInsight.parseByDependencyName(dep.name, scalaVersions)

                def origDepConfiguration =
                        "${dep.group}:${dep.name}:${dep.version}"

                // For each cross build version (for non test related configurations)
                if (allNonTestRelatedUserFacingViews.contains(viewType)) {
                    extension.resolvedBuilds.each { rb ->
                        def (String sourceSetId, SourceSet crossBuild) =
                        extension.crossBuildSourceSets.findByName(rb.name)

                        def crossBuildSourceSetInsights =
                                new UniSourceSetInsights(crossBuild, extension.project)
                        def crossBuildConfig = crossBuildSourceSetInsights.getConfigurationFor(viewType)

                        def translatedDepConfiguration = crossBuildConfig.name
                        def translatedDepNotation =
                                "${dep.group}:${dependencyInsight.baseName}_${rb.scalaVersion}:${dep.version}"

                        // Add explicit dependency to crossbuild sourcesets
                        extension.project.dependencies.add(translatedDepConfiguration, translatedDepNotation)

                        extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                                lifecycle:'afterEvaluate',
                                parentConfiguration:mainConfig.name,
                                configuration:crossBuildConfig.name,
                                msg:"Glob type dependency translation | glob '?' type dependency" +
                                        " ${origDepConfiguration} partially translated to: " +
                                        "[${translatedDepConfiguration} ${translatedDepNotation}]"
                        ))
                    }
                }

                // Add explicit dependency to main source set default version
                CrossBuildPluginUtils.assertWithMsg('Could not discover ' +
                        'default scala version. ' +
                        'Scala library dependency is missing ?') { assert possibleDefaultScalaVersions.size() > 0 }

                def defaultScalaVersion = possibleDefaultScalaVersions.head()

                def translatedDepConfiguration = mainConfig.name
                def translatedDepNotation =
                        "${dep.group}:${dependencyInsight.baseName}_${defaultScalaVersion}:${dep.version}"

                extension.project.dependencies.add(mainConfig.name,
                        "${dep.group}:${dependencyInsight.baseName}_${defaultScalaVersion}:${dep.version}")

                extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                        lifecycle:'afterEvaluate',
                        parentConfiguration:mainConfig.name,
                        msg:"Glob type dependency translation | glob '?' type dependency " +
                                "${origDepConfiguration} partially translated to: " +
                                "[${translatedDepConfiguration} ${translatedDepNotation}]"
                ))

                // Remove original glob type dep
                mainConfig.dependencies.remove(dep)
            }
        }
    }

    private static void assignCrossBuildDependencyResolutionStrategy(CrossBuildExtension extension) {
        def main = extension.crossBuildSourceSets.container.findByName('main')

        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def sourceSetInsights = new SourceSetInsights(sourceSet, main, extension.project)
            def configurer = new ResolutionStrategyConfigurer(sourceSetInsights, extension.scalaVersionsCatalog,
                    rb.scalaVersionInsights)

            configurer.applyForLinkWith(
                    ViewType.COMPILE_FRONTEND,
                    ViewType.COMPILE_BACKEND,
                    ViewType.IMPLEMENTATION,
                    ViewType.COMPILE_CLASSPATH,
                    ViewType.RUNTIME_CLASSPATH,
                    ViewType.COMPILE_ONLY,
                    ViewType.RUNTIME,
                    ViewType.RUNTIME_ONLY)
        }
    }

    private static SourceSet getAndUpdateSourceSetFor(ResolvedBuildAfterEvalLifeCycle rb,
                                      CrossBuildSourceSets crossBuildSourceSets) {
        def main = crossBuildSourceSets.container.findByName('main')

        def (String sourceSetId, SourceSet sourceSet) = crossBuildSourceSets.findByName(rb.name)

        // Assign main java source-set to cross-build java source-set
        sourceSet.java.srcDirs(main.java.getSrcDirs())

        // Assign main scala source-set to cross-build scala source-set
        sourceSet.scala.srcDirs(main.scala.getSrcDirs())

        // Assign main resources source-set to cross-build resources source-set
        sourceSet.resources.srcDirs(main.resources.getSrcDirs())

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

            di.with {
                // for 'api' configuration
                addMainConfigurationToCrossBuildCounterPart(ViewType.COMPILE_FRONTEND, sv)
                // for 'implementation' configuration
                addMainConfigurationToCrossBuildCounterPart(ViewType.IMPLEMENTATION, sv)
                // for 'compileOnly' configuration
                addMainConfigurationToCrossBuildCounterPart(ViewType.COMPILE_ONLY, sv)
                // for 'runtimeOnly' configuration
                addMainConfigurationToCrossBuildCounterPart(ViewType.RUNTIME_ONLY, sv)

                // for 'compile' configuration
                addDefaultConfigurationsToCrossBuildConfigurationRecursive(ViewType.COMPILE_FRONTEND)
                generateAndWireCrossBuildProjectTypeDependencies(ViewType.COMPILE_FRONTEND)
                generateAndWireCrossBuildProjectTypeDependencies(ViewType.IMPLEMENTATION)
            }
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
        def main = extension.crossBuildSourceSets.container.findByName('main')

        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)
            def sourceSetInsights = new SourceSetInsights(sourceSet, main, extension.project)

            ScalaCompileTasks.tuneCrossBuildScalaCompileTask(extension.project, sourceSetInsights)
        }
    }
}
