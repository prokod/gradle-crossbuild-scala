package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySet

/**
 * Crossbuild Configuration resolution strategy configurer
 */
class ResolutionStrategyConfigurer {
    private final Project project
    private final ScalaVersions scalaVersions
    private final ScalaVersionInsights scalaVersionInsights

    ResolutionStrategyConfigurer(Project project,
                                         ScalaVersions scalaVersions,
                                         ScalaVersionInsights scalaVersionInsights) {
        this.project = project
        this.scalaVersions = scalaVersions
        this.scalaVersionInsights = scalaVersionInsights
    }

    /**
     * Resolve dependencies with place holder version '?' for a crossbuild configuration and
     * try to convert mismatched scala version in dependencies coming from parent configuration to matching ones.
     *
     * @param configurations A list of tuple of the form (String, Configuration) each holds:
     *                       - A specific {@link org.gradle.api.tasks.SourceSet} configuration name to use as
     *                          a source for dependencies
     *                       - A {@link Configuration} to link as extendedFrom
     */
    void applyFor(List<Tuple2<String, Configuration>> configurations) {
        configurations.findAll { configTuple ->
            def crossBuildConfigurationName = configTuple.first
            def parentConfiguration = configTuple.second

            def config = project.configurations[crossBuildConfigurationName]
            config.extendsFrom(parentConfiguration)
            def allDependencies = config.allDependencies
            project.logger.info(LoggerUtils.logTemplate(project,
                    "Inherited dependendencies to consider while resolving ${crossBuildConfigurationName} " +
                            'configuration dependencies: ' +
                            "[${allDependencies.collect { "${it.group}:${it.name}" }.join(', ')}]"
            ))

            def allDependenciesAsDisplayNameSet = allDependencies.collect {
                "${it.group}:${it.name}:${it.version}"
            }.toSet()

            project.configurations.all { c ->
                c.resolutionStrategy.eachDependency { details ->
                    def requested = details.requested
                    if (allDependenciesAsDisplayNameSet
                            .contains("${requested.group}:${requested.name}:${requested.version}")) {
                        String supposedScalaVersion = DependencyInsights.parseDependencyName(requested.name)[1]
                        if (c.name == crossBuildConfigurationName) {
                            strategyForCrossBuildConfiguration(
                                    crossBuildConfigurationName, supposedScalaVersion, details)
                        } else if (c.name == parentConfiguration.name) {
                            strategyForNonCrossBuildConfiguration(parentConfiguration, supposedScalaVersion, details)
                        }
                    }
                }
            }

            // Cover the cases where two sub projects, for instance, are using cross build plugin and one is being used
            //  as dependency for the other.
            def crossBuildProjectDependencySet = DependencyInsights.extractCrossBuildProjectDependencySet(
                    project.gradle, parentConfiguration.allDependencies)

            crossBuildProjectDependencySet.collect { projectDep ->
                def configuration = project.configurations[parentConfiguration.name]
                configuration.resolutionStrategy.eachDependency { details ->
                    def requested = details.requested
                    String supposedScalaVersion = DependencyInsights.parseDependencyName(requested.name)[1]
                    strategyForNonCrossBuildConfiguration(configuration, supposedScalaVersion, details)
                }
            }
        }
    }

    private void strategyForCrossBuildConfiguration(String crossBuildConfigurationName,
                                                    String supposedScalaVersion,
                                                    DependencyResolveDetails details) {
        def requested = details.requested

        // Not a cross built dependency
        if (supposedScalaVersion == null) {
            if (requested.group == 'org.scala-lang' && requested.name == 'scala-library') {
                details.useVersion(scalaVersionInsights.compilerVersion)
            }
        }
        // A cross built dependency - globbed (implicit)
        else if (supposedScalaVersion == '?') {
            resolveQMarkDep(details, scalaVersionInsights.artifactInlinedVersion)
            project.logger.info(LoggerUtils.logTemplate(project,
                    "${crossBuildConfigurationName} | " +
                            "Found crossbuild glob '?' in dependency name ${requested.name}. " +
                            "Subtituted with [${details.target.name}]"
            ))
            // Replace 3d party scala dependency which ends with '_?' in cross build config scope
        } else {
            // A cross built dependency - explicit
            // Try updating target name version only if contains wrong scala version
            //   and only in cross build config context.
            if (supposedScalaVersion != scalaVersionInsights.artifactInlinedVersion) {
                def updated = tryForceTargetDependencyName(details, scalaVersionInsights)
                if (updated) {
                    project.logger.info(LoggerUtils.logTemplate(project,
                            "${crossBuildConfigurationName} | Dependency Scan " +
                                    "| Replaced ${requested.name}:${requested.version} => " +
                                    "${details.target.name}:${details.target.version}"
                    ))
                }
            }
        }
    }

    private void strategyForNonCrossBuildConfiguration(Configuration parentConfiguration,
                                                       String supposedScalaVersion,
                                                       DependencyResolveDetails details) {
        def requested = details.requested

        // Replace 3d party scala dependency which ends with '_?' in parent configuration scope
        if (supposedScalaVersion == '?') {
            if (tryForceTargetDependencyName(details, parentConfiguration, scalaVersions)) {
                project.logger.info(LoggerUtils.logTemplate(project,
                        "${parentConfiguration.name} | Found crossbuild glob '?' in " +
                                "dependency name ${requested.name}." +
                                " Subtituted with [${details.target.name}]"
                ))
            } else {
                project.logger.info(LoggerUtils.logTemplate(project,
                        "${parentConfiguration.name} | Could not infer Scala version " +
                                "to be applied to dependency '$requested.group:$requested.name'. " +
                                'Reason: scala-library dependency version not found or ' +
                                'multiple versions'
                ))
                updateTargetName(details, parentConfiguration, scalaVersions)
            }
        }
    }

    void applyFor(Map<String, Configuration> map) {
        applyFor(map.collect { new Tuple2<>(it.key, it.value) })
    }

    /**
     * Resolve dependencies with place holder scala version '?' for testCompile configuration.
     *
     * @param project Project space {@link Project}
     * @param scalaVersions Scala version catalog
     */
    void applyForTest() {
        project.configurations.all { c ->
            if (c.name.startsWith('test')) {
                c.resolutionStrategy.eachDependency { details ->
                    def requested = details.requested
                    // Replace 3d party scala dependency which contains '_?'
                    def probableScalaVersion = DependencyInsights.parseDependencyName(requested.name)[1]
                    if (probableScalaVersion == '?') {
                        updateTargetName(details, c, scalaVersions)
                        project.logger.info(LoggerUtils.logTemplate(project,
                                "${c.name} | Found crossbuild glob '?' in dependency name ${requested.name}. " +
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
    private static void resolveQMarkDep(
            DependencyResolveDetails details,
            String replacementScalaVersion) {
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
    private boolean tryForceTargetDependencyName(
            DependencyResolveDetails details,
            Configuration configuration,
            ScalaVersions scalaVersions) {
        def dependencySet = configuration.allDependencies

        def crossBuildProjectDependencySet = DependencyInsights.extractCrossBuildProjectDependencyDependencies(
                project.gradle, dependencySet, configuration.name)

        def allDependencySet = (crossBuildProjectDependencySet + dependencySet.collect())

        def scalaDeps = DependencyInsights.findScalaDependencies(allDependencySet, scalaVersions)

        def versions = scalaDeps.collect { it[1].toString() }.toSet()
        if (versions.size() == 1) {
            def scalaVersion = versions.head()

            resolveQMarkDep(details, scalaVersion)
            return true
        }
        false
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
     * @return true when target was updated, false when not needed.
     */
    private static boolean tryForceTargetDependencyName(
            DependencyResolveDetails details,
            ScalaVersionInsights scalaVersionInsights) {
        def requested = details.requested
        def (baseName, supposedRequestedScalaVersion, nameSuffix) =
                DependencyInsights.parseDependencyName(requested.name)
        if (supposedRequestedScalaVersion != scalaVersionInsights.artifactInlinedVersion) {
            if (nameSuffix != null) {
                return false
            }
            def correctName = "${baseName}_${scalaVersionInsights.artifactInlinedVersion}"
            details.useTarget requested.group + ':' + correctName + ':' + requested.version
            return true
        }
        false
    }

    /**
     * Resolve dependency names containing question mark to the actual scala version,
     *  based on provided {@link DependencySet} (of the configuration being handled) and {@link ScalaVersions}.
     * The resolution is going over the tree of dependencies and tries to figure out the scala version being used
     *  by consensus of Scala 3rd lib dependencies found and their Scala base version
     *
     * @param details {@link DependencyResolveDetails} from resolution strategy
     * @param configuration Specified configuration to retrieve all dependencies from.
     * @param scalaVersions A set of Scala versions that serve as input for the plugin.
     * @throws AssertionError if Scala version cannot be inferred
     */
    private void updateTargetName(
            DependencyResolveDetails details,
            Configuration configuration,
            ScalaVersions scalaVersions) {
        def dependencySet = configuration.allDependencies

        def crossBuildProjectDependencySet = DependencyInsights.extractCrossBuildProjectDependencyDependencies(
                project.gradle, dependencySet, configuration.name)

        def allDependencySet = (crossBuildProjectDependencySet + dependencySet.collect())

        def probableScalaVersionRaw = scalaVersions.catalog*.key.collect { String scalaVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(scalaVersion, scalaVersions)
            def deps = DependencyInsights.findAllNonMatchingScalaVersionDependenciesQMarksExcluded(
                    allDependencySet, scalaVersionInsights.artifactInlinedVersion)
            new Tuple2(scalaVersionInsights.artifactInlinedVersion, deps.size())
        }.findAll { tuple ->
            tuple.second == 0
        }
        def requested = details.requested
        assert probableScalaVersionRaw.size() == 1 : 'Could not infer Scala version to be applied to dependency ' +
                "'$requested.group:$requested.name'"
        def probableScalaVersion = probableScalaVersionRaw.head().first.toString()
        resolveQMarkDep(details, probableScalaVersion)
    }
}
