package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.model.DependencyLimitedInsight
import com.github.prokod.gradle.crossbuild.utils.CrossBuildPluginUtils
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsights
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsights.ViewType
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsightsView
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsightsView.DependencySetType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySet

/**
 * Crossbuild Configuration resolution strategy configurer
 */
class ResolutionStrategyConfigurer {
    private final SourceSetInsights sourceSetInsights
    private final ScalaVersions scalaVersions
    private final ScalaVersionInsights scalaVersionInsights

    ResolutionStrategyConfigurer(SourceSetInsights sourceSetInsights,
                                 ScalaVersions scalaVersions,
                                 ScalaVersionInsights scalaVersionInsights) {
        this.sourceSetInsights = sourceSetInsights
        this.scalaVersions = scalaVersions
        this.scalaVersionInsights = scalaVersionInsights
    }

    ResolutionStrategyConfigurer(SourceSetInsights sourceSetInsights,
                                 Map<String, String> catalog,
                                 ScalaVersionInsights scalaVersionInsights) {
        this(sourceSetInsights, ScalaVersions.withDefaultsAsFallback(catalog), scalaVersionInsights)
    }

    /**
     * Resolve dependencies with place holder version '?' for a crossbuild configuration and
     * try to convert mismatched scala version in dependencies coming from parent configuration to matching ones.
     *
     * @param configurations A list of tuple of the form (String, Configuration) each holds:
     *                       - A specific crossbuild {@link org.gradle.api.tasks.SourceSet}
     *                         derived {@link Configuration} name to use as a source for dependencies
     *                       - A {@link Configuration} to link as {@code extendedFrom}
     */
    void applyForLinkWith(ViewType... views) {
        def project = sourceSetInsights.project

        views.findAll { view ->
            def insightsView = new SourceSetInsightsView(sourceSetInsights, view)
            def names = insightsView.names
            def crossBuildConfigurationName = names.crossBuild
            def configs = insightsView.configurations
            def dependencySets = insightsView.getDependencySets(DependencySetType.ALL)

            def allDependencies = dependencySets.flatMapped()
            project.logger.info(LoggerUtils.logTemplate(project,
                    lifecycle:'afterEvaluate',
                    configuration:crossBuildConfigurationName,
                    parentConfiguration:names.main,
                    msg:"Inherited dependendencies to consider while resolving ${crossBuildConfigurationName} " +
                            'configuration dependencies: [' +
                            "${allDependencies.collect { "${it.group}:${it.name}" }.join(', ')}]"
            ))

            def di = new DependencyInsights(sourceSetInsights)

            def crossBuildProjects = CrossBuildPluginUtils.findAllCrossBuildPluginAppliedProjects(insightsView)

            def projectDependencies =
                    di.findAllCrossBuildProjectTypeDependenciesDependenciesFor([configs.main.name] as Set, view)
            def unionOfAllDependencies = allDependencies + projectDependencies
            def unionOfAllDependenciesAsDisplayNameSet =
                    unionOfAllDependencies.collect { dep -> "${dep.group}:${dep.name}:${dep.version}" }.toSet()

            crossBuildProjects.each {
                it.configurations.all { Configuration c ->
                    c.resolutionStrategy.eachDependency { details ->
                        resolutionStrategyHandler(c, details, unionOfAllDependenciesAsDisplayNameSet, view)
                    }
                }
            }
        }
    }

    private void resolutionStrategyHandler(Configuration targetConfiguration,
                                   DependencyResolveDetails details,
                                   Set<String> allDependenciesAsDisplayNameSet,
                                   ViewType referenceView) {
        def insightsView = new SourceSetInsightsView(sourceSetInsights, referenceView)
        def crossBuildConfiguration = insightsView.configurations.crossBuild
        def parentConfiguration = insightsView.configurations.main

        def crossBuildConfigurationName = crossBuildConfiguration.name
        def requested = details.requested
        if (allDependenciesAsDisplayNameSet
                .contains("${requested.group}:${requested.name}:${requested.version}")) {
            def dependencyInsight = DependencyLimitedInsight.parseByDependencyName(requested.name, scalaVersions)
            def supposedScalaVersion = dependencyInsight.supposedScalaVersion
            if (targetConfiguration.name == crossBuildConfigurationName) {
                strategyForCrossBuildConfiguration(details, supposedScalaVersion, insightsView)
            } else if (targetConfiguration.name == parentConfiguration.name) {
                strategyForNonCrossBuildConfiguration(details, supposedScalaVersion, insightsView)
            }
        }
    }

    private void strategyForCrossBuildConfiguration(DependencyResolveDetails details,
                                                    String supposedScalaVersion,
                                                    SourceSetInsightsView insightsView) {
        def project = sourceSetInsights.project

        def crossBuildConfiguration = insightsView.configurations.crossBuild
        def crossBuildConfigurationName = crossBuildConfiguration.name
        def requested = details.requested

        // Not a cross built dependency
        if (supposedScalaVersion == null) {
            if (requested.group == 'org.scala-lang') {
                details.useVersion(scalaVersionInsights.compilerVersion)
            }
        }
        // A cross built dependency - globbed (implicit)
        else if (supposedScalaVersion == '?') {
            resolveQMarkDep(details, scalaVersionInsights.artifactInlinedVersion)
            project.logger.info(LoggerUtils.logTemplate(project,
                    lifecycle:'afterEvaluate',
                    configuration:crossBuildConfigurationName,
                    msg:"Dependency Scan | Found crossbuild glob '?' in dependency name ${requested.name}. " +
                            "Subtituted with [${details.target.name}]"
            ))
            // Replace 3d party scala dependency which ends with '_?' in cross build config scope
        } else {
            // A cross built dependency - explicit
            // Try correcting offending target dependency only if contains wrong scala version
            // and only in cross build config context.
            if (supposedScalaVersion != scalaVersionInsights.artifactInlinedVersion) {
                tryCorrectingTargetDependencyName(details, scalaVersionInsights.artifactInlinedVersion, insightsView)

                project.logger.info(LoggerUtils.logTemplate(project,
                        lifecycle:'afterEvaluate',
                        configuration:crossBuildConfigurationName,
                        msg:'Dependency Scan ' +
                                "| Found polluting dependency ${requested.name}:${requested.version}. Replacing all " +
                                "together with [${details.target.name}:${details.target.version}]"
                ))
            }
        }
    }

    private void strategyForNonCrossBuildConfiguration(DependencyResolveDetails details,
                                                       String supposedScalaVersion,
                                                       SourceSetInsightsView insightsView) {
        def project = sourceSetInsights.project

        def parentConfiguration = insightsView.configurations.main
        def requested = details.requested

        // Replace 3d party scala dependency which ends with '_?' in parent configuration scope
        if (supposedScalaVersion == '?') {
            if (tryResolvingQMarkInTargetDependencyName(details, parentConfiguration, scalaVersions, insightsView)) {
                project.logger.info(LoggerUtils.logTemplate(project,
                        lifecycle:'afterEvaluate',
                        configuration:parentConfiguration.name,
                        msg:"Found crossbuild glob '?' in dependency name ${requested.name}." +
                                " Subtituted with [${details.target.name}]"
                ))
            } else {
                project.logger.info(LoggerUtils.logTemplate(project,
                        lifecycle:'afterEvaluate',
                        configuration:parentConfiguration.name,
                        msg:'Could not infer Scala version to be applied to dependency ' +
                                "'$requested.group:$requested.name'. " +
                                'Reason: scala-library dependency version not found or multiple versions'
                ))
                resolveQMarkInTargetDependencyName(details, parentConfiguration, scalaVersions, insightsView)
            }
        }
    }

    /**
     * Resolve dependencies with place holder scala version '?' for testCompile configuration.
     *
     * @param project Project space {@link Project}
     * @param scalaVersions Scala version catalog
     */
    void applyFor(Set<Configuration> configurations) {
        def project = sourceSetInsights.project

        project.configurations.all { Configuration c ->
            if (configurations.contains(c)) {
                c.resolutionStrategy.eachDependency { details ->
                    def requested = details.requested
                    // Replace 3d party scala dependency which contains '_?'
                    def dependencyInsight =
                            DependencyLimitedInsight.parseByDependencyName(requested.name, scalaVersions)
                    def probableScalaVersion = dependencyInsight.supposedScalaVersion
                    if (probableScalaVersion == '?') {
                        // We do not have plugin generated cross build configurations specifically dependent on test
                        // configurations like `testCompile`, `testCompileOnly`, `testImplementation` ...
                        def insightsView =  SourceSetInsightsView.from(c, sourceSetInsights)
                        strategyForNonCrossBuildConfiguration(details, probableScalaVersion, insightsView)
                        project.logger.info(LoggerUtils.logTemplate(project,
                                lifecycle:'afterEvaluate',
                                configuration:c.name,
                                msg:"Found crossbuild glob '?' in dependency name ${requested.name}. " +
                                        "Subtituted with [${details.target.name}]"
                        ))
                    }
                }
            }
        }
    }

    /**
     * Resolve dependency names containing question mark to the actual scala version,
     * based on {@link ScalaVersionInsights} provided.
     *
     * @param details {@link org.gradle.api.artifacts.DependencyResolveDetails} from resolution strategy
     * @param scalaVersionInsights Holds all version permutations for a specific Scala version
     */
    private static void resolveQMarkDep(DependencyResolveDetails details, String replacementScalaVersion) {
        def requested = details.requested
        def resolvedName = requested.name.replace('_?', "_$replacementScalaVersion")
        details.useTarget requested.group + ':' + resolvedName + ':' + requested.version
    }

    /**
     * Resolve dependency names containing question mark to the actual scala version,
     *  based on provided {@link org.gradle.api.artifacts.DependencySet} (of the configuration being handled)
     *  and scala-library dep. version.
     *
     * @param details {@link DependencyResolveDetails} from resolution strategy
     * @param configuration Specified configuration to retrieve all dependencies from.
     * @param scalaVersions A set of Scala versions that serve as input for the plugin.
     */
    private boolean tryResolvingQMarkInTargetDependencyName(DependencyResolveDetails details,
                                                            Configuration configuration,
                                                            ScalaVersions scalaVersions,
                                                            SourceSetInsightsView insightsView) {
        def dependencySet = [configuration.allDependencies]

        def di = new DependencyInsights(sourceSetInsights)

        def configurationNames = [configuration.name] as Set
        def crossBuildProjectDependencySet =
                di.findAllCrossBuildProjectTypeDependenciesDependenciesFor(configurationNames, insightsView.viewType)

        def allDependencySet = (crossBuildProjectDependencySet + dependencySet.collectMany { it.toSet() })

        def scalaDeps = DependencyInsights.findScalaDependencies(allDependencySet, scalaVersions)

        def versions = scalaDeps*.supposedScalaVersion.toSet()
        if (versions.size() == 1) {
            def scalaVersion = versions.head()

            resolveQMarkDep(details, scalaVersion)
            return true
        }
        false
    }

    /**
     * Try correcting a probable Scala dependency {@link DependencyResolveDetails} to use the sourceSet scala version,
     *  based on provided {@link DependencySet} (of the configuration being handled) and {@link ScalaVersions}.
     * The resolution is going over the tree of dependencies and collects the different scala versions
     * for the same library base name. Afterwards, based on the findings and the sourceSet designated scala version,
     * the offending dependency is being altered.
     *
     * Solves, usually, the following plugin DSL scenario {@see CrossBuildPluginCompileMultiModuleAdvancedTest}:
     * <pre>
     *     dependencies {
     *         compile 'org.scalaz:scalaz-core_2.12:7.2.28'
     *         crossBuildSpark230_211Compile 'org.scalaz:scalaz-core_2.11:7.2.28'
     *     }
     * </pre>
     *
     * @param offenderDetails {@link DependencyResolveDetails} from resolution strategy
     * @param targetScalaVersion Scala version to correct to.
     * @param configuration Specified configuration to retrieve all dependencies from.
     *
     * @throws AssertionError if Scala version cannot be inferred
     */
    private void tryCorrectingTargetDependencyName(DependencyResolveDetails offenderDetails,
                                                   String targetScalaVersion,
                                                   SourceSetInsightsView insightsView) {
        def dependencySet = insightsView.getDependencySets(DependencySetType.ALL).flatMapped().toSet()

        def di = new DependencyInsights(sourceSetInsights)

        def configurationNames = insightsView.configurations.flatMapped()*.name.toSet()
        def crossBuildProjectDependencySet =
                di.findAllCrossBuildProjectTypeDependenciesDependenciesFor(configurationNames, insightsView.viewType)

        def allDependencySet = (crossBuildProjectDependencySet + dependencySet)

        def libGrid = DependencyInsights.findAllNonMatchingScalaVersionDependenciesWithCounterparts(
                allDependencySet, targetScalaVersion, scalaVersions)

        // dependencyMap key is name of dependency and value contains suggested correct dependency/ies
        def dependencyMap = libGrid.collectEntries { tuple ->
            new AbstractMap.SimpleEntry(tuple.first.dependency.name, tuple.second)
        }

        def requested = offenderDetails.requested

        def correctDependencies = dependencyMap[requested.name]

        assert correctDependencies.size() == 1 : 'There should be one candidate to replace offending dependency ' +
                "'$requested.group:$requested.name' for target scala version $targetScalaVersion : " +
                "[${correctDependencies*.dependency.collect { "$it.name:$it.version" }.join(', ')}]"

        def correctDependencyInsight = correctDependencies.head()
        def correctDependency = correctDependencyInsight.dependency

        // Assuming group is staying the same ...
        offenderDetails.useTarget requested.group + ':' + correctDependency.name + ':' + correctDependency.version
    }

    /**
     * Resolve dependency names containing question mark to the actual scala version,
     * based on provided {@link DependencySet} (of the configuration being handled) and {@link ScalaVersions}.
     * The resolution is going over the tree of dependencies and tries to figure out the scala version being used
     * by consensus of Scala 3rd lib dependencies found and their Scala base version
     *
     * @param details {@link DependencyResolveDetails} from resolution strategy
     * @param configuration Specified configuration to retrieve all dependencies from.
     * @param scalaVersions A set of Scala versions that serve as input for the plugin.
     * @throws AssertionError if Scala version cannot be inferred
     */
    private void resolveQMarkInTargetDependencyName(DependencyResolveDetails details,
                                                    Configuration configuration,
                                                    ScalaVersions scalaVersions,
                                                    SourceSetInsightsView insightsView) {
        def dependencySet = [configuration.allDependencies]

        def di = new DependencyInsights(sourceSetInsights)

        def configurationNames = [configuration.name] as Set
        def crossBuildProjectDependencySet =
                di.findAllCrossBuildProjectTypeDependenciesDependenciesFor(configurationNames, insightsView.viewType)

        def allDependencySet = (crossBuildProjectDependencySet + dependencySet.collectMany { it.toSet() })

        def probableScalaVersionRaw = scalaVersions.catalog*.key.collect { String scalaVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(scalaVersion, scalaVersions)
            def deps = DependencyInsights.findAllNonMatchingScalaVersionDependenciesQMarksExcluded(
                    allDependencySet, scalaVersionInsights.artifactInlinedVersion, scalaVersions)
            new Tuple2(scalaVersionInsights.artifactInlinedVersion, deps.size())
        }.findAll { tuple ->
            // Means this is a sane state where the dependency to be resolved does not have any other alternatives in
            // the dependency set being examined. In that case we can build upon it to infer the scala version up the
            // stream.
            tuple.second == 0
        }
        def requested = details.requested
        assert probableScalaVersionRaw.size() == 1 : 'Could not infer Scala version to be applied to dependency ' +
                "'$requested.group:$requested.name'"
        def probableScalaVersion = probableScalaVersionRaw.head().first.toString()
        resolveQMarkDep(details, probableScalaVersion)
    }
}
