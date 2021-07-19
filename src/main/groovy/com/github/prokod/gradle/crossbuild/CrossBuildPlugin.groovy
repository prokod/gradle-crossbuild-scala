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

import com.github.prokod.gradle.crossbuild.model.DependencyLimitedInsight
import com.github.prokod.gradle.crossbuild.tasks.AbstractCrossBuildsReportTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildPomTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildsReportTask
import com.github.prokod.gradle.crossbuild.tasks.CrossBuildsClasspathResolvedConfigurationReportTask
import com.github.prokod.gradle.crossbuild.utils.CrossBuildPluginUtils
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights1
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsights
import com.github.prokod.gradle.crossbuild.utils.UniSourceSetInsightsView
import com.github.prokod.gradle.crossbuild.utils.ViewType
import com.github.prokod.gradle.crossbuild.utils.UniSourceSetInsights
import org.gradle.api.artifacts.Configuration
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

            t.description = 'Summary report for cross building resolved Dsl'
        }

        project.task(type:CrossBuildsClasspathResolvedConfigurationReportTask,
                "${AbstractCrossBuildsReportTask.BASE_TASK_NAME}ResolvedConfigs") { t ->
            t.extension = extension

            t.description = 'Summary report for cross building resolved Configurations'
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
        extension.resolvedBuilds.findAll { rb ->
            def scalaVersionInsights = rb.scalaVersionInsights

            def sourceSet = getAndUpdateSourceSetFor(rb, extension.crossBuildSourceSets)

            //todo maybe not needed anymore (might not be as helpful as expected)
            // Mainly here to help with creation of the correct dependencies for pom creation
            // see ResolutionStrategyConfigurer::assemble3rdPartyDependencies
//            extension.project.dependencies.add(sourceSet.compileConfigurationName,
//                    "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")
        }
    }

    @SuppressWarnings(['ClosureAsLastMethodParameter'])
    private static void globDependencyTranslationForMainSourceSetsConfigurations(CrossBuildExtension extension) {
        // Find main source set
        def main = extension.crossBuildSourceSets.container.findByName('main')

        def mainSourceSetInsights = new UniSourceSetInsights(main, extension.project)

        def allNonTestRelatedUserFacingViews =
                ViewType.filterViewsBy({ tags -> tags.contains('canBeConsumed') }, { tags -> !tags.contains('test') })

        ViewType.filterViewsBy({ tags -> tags.contains('canBeConsumed') }).each { viewType ->
            def mainConfig = mainSourceSetInsights.getConfigurationFor(viewType)
            // Create scalaVersions to be used in parseByDependencyName
            def scalaVersions = ScalaVersions.withDefaultsAsFallback(extension.scalaVersionsCatalog)
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
                                msg:"Glob type depenency translation | glob '?' type dependency" +
                                        " ${origDepConfiguration} partially translated to: " +
                                        "[${translatedDepConfiguration} ${translatedDepNotation}]"
                        ))
                    }
                }

                // Add explicit dependency to main source set default version
                def possibleDefaultScalaVersions = findScalaVersions(mainConfig, mainSourceSetInsights, scalaVersions)
                //def possibleDefaultScalaVersions = getScalaDependencies(mainConfig, scalaVersions)

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

    /**
     * Find a set of scala versions,
     * based on provided {@link org.gradle.api.artifacts.DependencySet} (of the configuration being handled)
     * and scala-library dependency version.
     *
     * @param configuration Specified configuration to retrieve all dependencies from.
     * @param sourceSetInsights Source-set Insight (representation of specific crossBuild source-set or its main
     *                          counterpart) - aids with dependencies related insights mainly
     * @param scalaVersions A set of Scala versions that serve as input for the plugin.
     */
    private static Set<String> findScalaVersions(Configuration configuration,
                                              UniSourceSetInsights sourceSetInsights,
                                              ScalaVersions scalaVersions) {
        def insightsView =  UniSourceSetInsightsView.from(configuration, sourceSetInsights)

        def dependencySet = [configuration.allDependencies]

        def di = new DependencyInsights1(sourceSetInsights)

        def configurationNames = [configuration.name] as Set
        def crossBuildProjectDependencySet =
                di.findAllCrossBuildProjectTypeDependenciesDependenciesFor(configurationNames, insightsView.viewType)

        def allDependencySet = (crossBuildProjectDependencySet + dependencySet.collectMany { it.toSet() })

        def scalaDeps = DependencyInsights.findScalaDependencies(allDependencySet, scalaVersions)

        def versions = scalaDeps*.supposedScalaVersion.toSet()
        versions
    }

    private static void assignCrossBuildDependencyResolutionStrategy(CrossBuildExtension extension) {
        def main = extension.crossBuildSourceSets.container.findByName('main')

        extension.resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

            def sourceSetInsights = new SourceSetInsights(sourceSet, main, extension.project)
            def configurer = new ResolutionStrategyConfigurer(sourceSetInsights, extension.scalaVersionsCatalog,
                    rb.scalaVersionInsights)

            configurer.applyForLinkWith(
                    ViewType.IMPLEMENTATION,
                    ViewType.COMPILE_CLASSPATH,
                    ViewType.RUNTIME_CLASSPATH,
                    ViewType.COMPILE_ONLY,
                    ViewType.RUNTIME_ONLY)
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
//            di.addDefaultConfigurationsToCrossBuildConfigurationRecursive(ViewType.COMPILE)
//            di.generateAndWireCrossBuildProjectTypeDependencies(ViewType.COMPILE, ViewType.IMPLEMENTATION)
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
