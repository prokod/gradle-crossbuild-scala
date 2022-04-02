package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.ScalaVersions
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency

/**
 * A collection of Dependency related methods
 *
 */
class UniDependencyInsights {

    final UniSourceSetInsights sourceSetInsights

    UniDependencyInsights(UniSourceSetInsights sourceSetInsights) {
        this.sourceSetInsights = sourceSetInsights
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
    Set<String> findScalaVersions(ScalaVersions scalaVersions) {
        ViewType.filterViewsBy { tags -> tags.contains('canBeConsumed') }.collectMany { viewType ->
            def mainConfig = sourceSetInsights.getConfigurationFor(viewType)

            def insightsView = UniSourceSetInsightsView.from(mainConfig, sourceSetInsights)

            def dependencySet = [mainConfig.allDependencies]

            def configurationNames = [mainConfig.name] as Set
            def crossBuildProjectDependencySet =
                    findAllCrossBuildProjectTypeDependenciesDependenciesFor(configurationNames, insightsView.viewType)

            def allDependencySet = (crossBuildProjectDependencySet + dependencySet.collectMany { it.toSet() })

            def scalaDeps = DependencyInsights.findScalaDependencies(allDependencySet, scalaVersions)

            def versions = scalaDeps*.supposedScalaVersion.toSet()
            versions
        }
    }

    /**
     * The dependencySet is being searched for projects {@link Project}
     * that are used as a dependency of type {@link ProjectDependency}, which the cross build plugin
     * ({@link com.github.prokod.gradle.crossbuild.CrossBuildPlugin}) been applied on.
     * After being found, all the related (direct) dependencies for the specified configuration within those projects
     * are being returned.
     *
     * @param configurationNames Configurations to have the dependencies extracted from
     * @param referenceView Configuration type as context for this method to operate from.
     * @return List of {@link Dependency} from relevant projects that are themselves defined as dependencies and share
     *         the same dependency graph with the ones originated from within initialDependencySet
     *
     */
    Set<Dependency> findAllCrossBuildProjectTypeDependenciesDependenciesFor(Set<String> configurationNames,
                                                                            ViewType referenceView) {
        def projectTypeDependencies = extractCrossBuildProjectTypeDependencies(referenceView)

        def dependenciesOfProjectDependencies = projectTypeDependencies.collectMany { prjDep ->
            extractCrossBuildProjectTypeDependencyDependencies(prjDep, configurationNames)
        }

        dependenciesOfProjectDependencies.toSet()
    }

    static Closure isProjectDependency = { Dependency dependency -> dependency instanceof ProjectDependency }

    /**
     * The dependencySet is being searched for projects {@link Project}
     * that are used as a dependency of type {@link ProjectDependency}, which the cross build plugin
     * ({@link com.github.prokod.gradle.crossbuild.CrossBuildPlugin}) has been applied on.
     * After being found, The dependency graph is being searched for all the related (direct and transient) project
     * type dependencies for the specified configuration.
     *
     * NOTE: The outcome is dependent on the lifecycle stage this method is called in. It is complete and stable
     * after {@code gradle.projectsEvaluated} and gives a limited visibility (of one level only)
     * in {@code project.afterEvaluated} see {@link CrossBuildPluginUtils#findAllCrossBuildPluginAppliedProjects}
     *
     * @return A set of {@link ProjectDependency} that belong to the dependency graph originated from the initial
     *         project type dependencies found in the initial dependency set
     */
    Set<ProjectDependency> extractCrossBuildProjectTypeDependencies(ViewType viewType) {
        def insightsView = new UniSourceSetInsightsView(sourceSetInsights, viewType)
        def modules = CrossBuildPluginUtils.findAllCrossBuildPluginAppliedProjectsFor(sourceSetInsights.project)

        def inputDependencySet = insightsView.getDependencySets(DependencySetType.ALL).flatMapped().toSet()
        def configurationNames = insightsView.names.flatMapped().toSet()

        def dependencies = extractCrossBuildProjectTypeDependenciesRecursively(modules, inputDependencySet,
                configurationNames)

        dependencies
    }

    /**
     * Valid Project type dependencies to descend the dependency graph for in this method are those with
     * targetConfiguration as 'default' only.
     * Bound by the visibility to the modules that the cross build plugin is applied to.
     * In a lazy applied plugin type of build.gradle, the full set of modules is visible in {code projectsEvaluated{}}
     * and later.
     *
     * @param modules Set of modules cross build plugin was applied to.
     *                see {@link #extractCrossBuildProjectTypeDependencies}
     * @param inputDependencySet
     * @param configurationNames
     * @return Set of {@link ProjectDependency} that are either part of the inputDependencySet or directly linked or
     *         transitively linked as dependency according to
     *         {@link #extractCrossBuildProjectTypeDependencyDependencies}
     */
    private Set<ProjectDependency> extractCrossBuildProjectTypeDependenciesRecursively(Set<Project> modules,
                                                                                    Set<Dependency> inDependencySet,
                                                                                    Set<String> configurationNames,
                                                                                    Set<ProjectDependency> accum = []) {

        def currentProjectTypDeps = inDependencySet.findAll(isProjectDependency).findAll { isValid(it, modules) }
                .findAll { isNotAccumulated(it, accum) }.collect { it as ProjectDependency }
        def currentProjectTypDepsForDefault = currentProjectTypDeps.findAll { it.targetConfiguration == null }
        if (currentProjectTypDepsForDefault.size() > 0) {
            accum.addAll(currentProjectTypDepsForDefault)
            def currentProjectTypeDependenciesDependencies = currentProjectTypDepsForDefault.collectMany { prjDep ->
                extractCrossBuildProjectTypeDependencyDependencies(prjDep, configurationNames)
            }
            extractCrossBuildProjectTypeDependenciesRecursively(
                    modules,
                    currentProjectTypeDependenciesDependencies.findAll { isNotAccumulated(it, accum) }.toSet(),
                    configurationNames,
                    accum)
        }
        accum
    }

    private static boolean isValid(Dependency dependency, Set<Project> modules) {
        modules*.name.contains(dependency.name)
    }

    private static boolean isNotAccumulated(Dependency dependency, Set<ProjectDependency> accum) {
        !accum*.name.contains(dependency.name)
    }

    private static Set<Dependency> extractCrossBuildProjectTypeDependencyDependencies(ProjectDependency dependency,
                                                                                      Set<String> configurationNames) {
        def crossBuildProjectTypeDependencyDependencySets = configurationNames.collect {
            dependency.dependencyProject.configurations.findByName(it)?.allDependencies
        }.findAll { it != null }

        crossBuildProjectTypeDependencyDependencySets.size() > 0 ?
                crossBuildProjectTypeDependencyDependencySets.collectMany { it.toSet() } : [] as Set
    }
}
