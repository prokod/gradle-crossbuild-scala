package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildPlugin
import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.ScalaVersions
import com.github.prokod.gradle.crossbuild.model.DependencyInsight
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.SourceSet

import javax.inject.Inject

/**
 * A collection of Dependency related methods
 *
 */
class DependencyInsights {

    final DependencyInsightsContext diContext

    DependencyInsights(DependencyInsightsContext diContext) {
        this.diContext = diContext
    }

    /**
     * todo refactor as this holds true accurately for specific methods in this class only:
     * {@link #addDefaultConfigurationsToCrossBuildConfigurationRecursive}
     * {@link #generateAndWireCrossBuildProjectTypeDependencies}
     *
     * @param project
     * @param sourceSet
     * @return
     */
    static DependencyInsights from(Project project, SourceSet sourceSet) {
        def crossBuildConfiguration = project.configurations.findByName(sourceSet.compileConfigurationName)

        def diContext = new DependencyInsightsContext(project:project,
                dependencies:[crossBuildConfiguration.allDependencies, project.configurations.compile.allDependencies],
                configurations:[current:crossBuildConfiguration, parent:project.configurations.compile])

        new DependencyInsights(diContext)
    }

    /**
     * The dependencySet is being searched for projects {@link Project}
     * that are used as a dependency of type {@link ProjectDependency}, which the cross build plugin
     * ({@link com.github.prokod.gradle.crossbuild.CrossBuildPlugin}) been applied on.
     * After being found, all the related (direct) dependencies for the specified configuration within those projects
     * are being returned.
     *
     * @param configurationName - configuration to have the dependencies extracted from
     * @return List of {@link Dependency} from relevant projects that are themselves defined as dependencies and share
     *          the same dependency graph with the ones originated from within initialDependencySet
     *
     * todo tuple configuration names should be part of diContext ...
     */
    Set<Dependency> findAllCrossBuildProjectTypeDependenciesDependenciesFor(Set<String> configurationNames) {
        def projectTypeDependencies = extractCrossBuildProjectTypeDependencies()

        def dependenciesOfProjectDependencies = projectTypeDependencies.collectMany { prjDep ->
            extractCrossBuildProjectTypeDependencyDependencies(prjDep, configurationNames)
        }

        dependenciesOfProjectDependencies.toSet()
    }

    /**
     * This method returns stable result (No matter how the user decides to use the plugin DSL in build.gradle)
     * when executed from within {@code Gradle.projectsEvaluated {}} block.
     *
     * NOTE: In certain cases of build.gradle composition together with this method being used from within
     * {@code project.afterEvaluate {}} block the result will be the same.
     *
     * @return Set of {@link Project}s with {@link CrossBuildPlugin} applied.
     *
     * TODO: Find a way to achieve the same result in a stable way from {@code project.afterEvaluate {}} block
     */
    Set<Project> findAllCrossBuildPluginAppliedProjects() {
        def project = diContext.project
        def configuration = diContext.configurations?.current
        def parentConfiguration = diContext.configurations?.parent
        def moduleNames = project.gradle.rootProject.allprojects.findAll { it.plugins.hasPlugin(CrossBuildPlugin) }

        project.logger.debug(LoggerUtils.logTemplate(project,
                lifecycle:'afterEvaluate',
                configuration:configuration?.name,
                parentConfiguration:parentConfiguration?.name,
                msg:"Found the following crossbuild modules ${moduleNames.join(', ')}."))
        moduleNames
    }

    void addCompileOnlyConfigurationToCrossBuildCounterPart(SourceSet sourceSet, ScalaVersions scalaVersions) {
        def project = diContext.project

        def consumerConfiguration = project.configurations[sourceSet.compileOnlyConfigurationName]

        def modules = findAllCrossBuildPluginAppliedProjects()

        def nonCrossBuildModules = { Dependency dependency -> !modules*.name.contains(dependency.name) }

        def consumerConfigurationDependenciesGroupName = consumerConfiguration.allDependencies.collect { dependency ->
            DependencyInsight.parse(dependency, scalaVersions).groupAndBaseName
        }.toSet()

        def nonSameExternalDependencies = { Dependency dependency ->
            def parsedGroupBaseName = DependencyInsight.parse(dependency, scalaVersions).groupAndBaseName
            !isProjectDependency(dependency) &&
            !consumerConfigurationDependenciesGroupName.contains(parsedGroupBaseName)
        }

        def producerConfigurationDependencies = project.configurations.compileOnly.allDependencies

        def producerConfigurationFilteredDependencies = producerConfigurationDependencies
                .findAll(nonCrossBuildModules).findAll(nonSameExternalDependencies)

        consumerConfiguration.dependencies.addAll(producerConfigurationFilteredDependencies)
    }

    void addDefaultConfigurationsToCrossBuildConfigurationRecursive(SourceSet sourceSet) {
        def project = diContext.project

        def defaultConfigurations = generateDetachedDefaultConfigurationsRecursively()

        def consumerConfiguration = project.configurations[sourceSet.compileConfigurationName]

        defaultConfigurations.each { configuration ->
            consumerConfiguration.dependencies.addAll(configuration.dependencies)
        }
    }

    /**
     * todo together with {@link #from} indicates that this class should be refactored. sourceSet as an input -redundant
     *
     * @param sourceSet
     */
    void generateAndWireCrossBuildProjectTypeDependencies(SourceSet sourceSet) {
        def project = diContext.project

        def consumerConfiguration = project.configurations[sourceSet.implementationConfigurationName]

        def projectLibDependencies = extractCrossBuildProjectTypeDependencies()

        projectLibDependencies.each { dependency ->
            def subProject = dependency.dependencyProject

            def targetTask = subProject.tasks[sourceSet.jarTaskName]

            def producerConfigurationName = "${sourceSet.name}Producer"

            def alreadyCreatedProducerConfiguration = subProject.configurations.findByName(producerConfigurationName)
            def producerConfiguration =
                    alreadyCreatedProducerConfiguration ?: subProject.configurations.create(producerConfigurationName) {
                canBeResolved = false
                canBeConsumed = true

                outgoing.artifact(targetTask)
            }

            def dep = project.dependencies.project(path:subProject.path, configuration:producerConfiguration.name)

            project.dependencies.attributesSchema.with {
                // Added to support correct Dependency resolution for Gradle 4.X
                attribute(Usage.USAGE_ATTRIBUTE).disambiguationRules.add(DisRule) {
                    it.params(sourceSet.name)
                }
            }

            project.dependencies.add(consumerConfiguration.name, dep)

            project.logger.info(LoggerUtils.logTemplate(project,
                    lifecycle:'projectsEvaluated',
                    configuration:sourceSet.name,
                    msg:"Created Custom project lib dependency: [$dep] linked to jar Task: [$targetTask]"
            ))
        }
    }

    /**
     * See {@link #generateDetachedDefaultConfigurationsRecursivelyFor} doc
     *
     * @return A set of detached {@link Configuration}s that are derived from all relevant 'default' configurations
     *         encountered in the dependency graph originated from the initial
     *         'default' configuration dependency set for the initial project in context.
     */
    Set<Configuration> generateDetachedDefaultConfigurationsRecursively() {
        def modules = findAllCrossBuildPluginAppliedProjects()

        def configurations = generateDetachedDefaultConfigurationsRecursivelyFor(diContext.project, modules)

        configurations
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
     * @param defaultConfiguration
     * @return
     */
    private Set<Configuration> extractCopiedDefaultConfigurationsRecursivelyInternal(
            Set<Project> modules,
            Configuration defaultConfiguration) {

        Set<Configuration> accum = []

        // todo maybe dependencies is enough ? (stead of allDependencies)
        def inputDependencySet = defaultConfiguration.allDependencies

        def currentProjectTypDeps = inputDependencySet.findAll(isProjectDependency).findAll { isValid(it, modules) }
                .collect { it as ProjectDependency }
        def currentProjectTypDepsForDefault = currentProjectTypDeps.findAll { it.targetConfiguration == null }
        // todo copy is sufficient ? (stead of copyRecursive)
        def copiedAndFiltered = defaultConfiguration.copyRecursive { Dependency dependency ->
            !modules*.name.contains(dependency.name)
        }
        accum.add(copiedAndFiltered)
        if (currentProjectTypDepsForDefault.size() > 0) {
            currentProjectTypDepsForDefault.each { ProjectDependency dependency ->
                def nextDefaultConfiguration = dependency.dependencyProject.configurations['default']
                accum.addAll(extractCopiedDefaultConfigurationsRecursivelyInternal(modules, nextDefaultConfiguration))
            }
        }

        accum
    }

    /**
     * Valid (= projects that cross build gradle plugin was applies to) Projects 'default' dependency set
     * is being searched for other project type dependencies by searching the dependency tree.
     * All the 'default' configurations for the found {@link ProjectDependency#getDependencyProject} are being
     * accumulated recursively and returned.
     *
     * NOTEs:
     * 1. Only those {@link ProjectDependency} with targetConfiguration as 'default' are being considered.
     * 2. This method is bound by the visibility to the modules that the cross build plugin is applied to.
     *    See modules parameter.
     *    In a lazy applied plugin type of build.gradle, the full set of modules is visible in
     *    {@code projectsEvaluated{}} and later.
     *
     * @param dependencyProject The project for which we collect it's processed 'default' configuration
     * @param modules Set of modules cross build plugin was applied to.
     *                see {@link #extractCrossBuildProjectTypeDependencies}
     * @return a set of detached configurations derived from the 'default' configuration found in the dependency tree
     */
    private Set<Configuration> generateDetachedDefaultConfigurationsRecursivelyFor(Project dependencyProject,
                                                                                   Set<Project> modules) {
        Set<Configuration> accum = []

        // todo maybe dependencies is enough ? (stead of allDependencies)
        def defaultConfiguration = dependencyProject.configurations['default']
        def inputDependencySet = defaultConfiguration.allDependencies

        def currentProjectTypDeps = inputDependencySet.findAll(isProjectDependency).findAll { isValid(it, modules) }
                .collect { it as ProjectDependency }
        def currentProjectTypDepsForDefault = currentProjectTypDeps.findAll { it.targetConfiguration == null }
        // todo copy is sufficient ? (stead of copyRecursive)

        def filteredDefaultDependencies = defaultConfiguration.allDependencies.findAll { Dependency dependency ->
            !modules*.name.contains(dependency.name)
        }
        def filteredDefaultDependenciesArray =
                filteredDefaultDependencies.toArray(new Dependency[filteredDefaultDependencies.size()]) as Dependency[]
        def detachedDefaultConfiguration =
                dependencyProject.configurations.detachedConfiguration(filteredDefaultDependenciesArray)

        accum.add(detachedDefaultConfiguration)
        if (currentProjectTypDepsForDefault.size() > 0) {
            currentProjectTypDepsForDefault.each { ProjectDependency dependency ->
                def nextDependencyProject = dependency.dependencyProject
                accum.addAll(generateDetachedDefaultConfigurationsRecursivelyFor(nextDependencyProject, modules))
            }
        }

        accum
    }

    @SuppressWarnings(['LineLength'])
    static class DisRule implements AttributeDisambiguationRule<Usage> {
        String sourceSetName

        @Inject
        DisRule(String sourceSetName) {
            this.sourceSetName = sourceSetName
        }

        void execute(MultipleCandidatesDetails<Usage> details) {
            // Needed by Gradle 4.X, otherwise Dependency resolution fails for non crossbuild configurations with
            // something like:
            // * Exception is:
            // org.gradle.api.internal.tasks.TaskDependencyResolveException: Could not determine the dependencies of task ':app:test'.
            // Caused by: org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration$ArtifactResolveException: Could not resolve all task dependencies for configuration ':app:testRuntimeClasspath'.
            //    ... 85 more
            // Caused by: org.gradle.internal.resolve.ModuleVersionResolveException: Could not resolve project :lib2.
            // Required by:
            //    project :app
            //    ... 90 more
            // Caused by: org.gradle.internal.component.AmbiguousConfigurationSelectionException: Cannot choose between the following variants of project :lib2:
            //   - crossBuildSpark160_210Producer
            //   - crossBuildSpark240_211Producer
            //   - runtimeElements
            // All of them match the consumer attributes:
            //   - Variant 'crossBuildSpark160_210Producer': Required org.gradle.usage 'java-runtime' and found compatible value 'crossBuildSpark160_210Jar-variant'.
            //   - Variant 'crossBuildSpark240_211Producer': Required org.gradle.usage 'java-runtime' and found compatible value 'crossBuildSpark240_211Jar-variant'.
            //   - Variant 'runtimeElements': Required org.gradle.usage 'java-runtime' and found compatible value 'java-runtime-jars'.
            if (!details.consumerValue.name.contains(CrossBuildSourceSets.SOURCESET_BASE_NAME)) {
                for (Usage t: details.candidateValues) {
                    if (!t.name.contains(sourceSetName)) {
                        details.closestMatch(t)
                        return
                    }
                }
            }
        }
    }

    /**
     * Parses given dependency to its groupName:baseName part and its scala version part.
     * Throws Assertion Exception if dependency name does not contain separating char '_'
     *
     * @param dep dependency to parse
     * @param scalaVersions
     * @return {@code true} if the dependency is named in scala lib convention, {@code false} otherwise
     */
    static boolean isScalaLib(Dependency dep, ScalaVersions scalaVersions) {
        def supposedlyScalaVersion = DependencyInsight.parse(dep, scalaVersions).supposedScalaVersion
        supposedlyScalaVersion != null
    }

    /**
     * Filters out all dependencies that match given 'scalaVersion' from a set of dependencies and then
     * enriches this list by converting each found dependency to a tuple containing itself and its counterparts.
     *
     * @param dependencies set of dependencies in the form of {@link org.gradle.api.artifacts.DependencySet} to scan
     * @param scalaVersion Scala Version to match against i.e '2.10', '2.11'
     * @parma scalaVersions
     * @return a list containing tuple2s  of tuple3s in the form of
     *         (groupName:baseArchiveName, scalaVersion, {@link org.gradle.api.artifacts.Dependency})
     *         For example, in case that scalaVersion = '2.10'
     *    [
     *     (('grp:lib', '2.11', ...), [('grp:lib', '2.10', ... version:'1.2'), ('grp:lib', '2.10', ... version:'1.2')]),
     *     (('grp:lib', '2.12', ...), [('grp:lib', '2.10', ... version:'1.2'), ('grp:lib', '2.10', ... version:'1.3')]),
     *     ((...), [(...), (...)]),
     *     ...
     *    ]
     */
    static
    List<Tuple2<DependencyInsight, Set<DependencyInsight>>> findAllNonMatchingScalaVersionDependenciesWithCounterparts(
            Collection<Dependency> dependencies,
            String scalaVersion,
            ScalaVersions scalaVersions) {
        def nonMatchingDependencyInsights =
                findAllNonMatchingScalaVersionDependencies(dependencies, scalaVersion, scalaVersions)
        def dependencyInsightPredicate = { DependencyInsight current,
                                           DependencyInsight nonMatching,
                                           String versionRef ->
            current.groupAndBaseName == nonMatching.groupAndBaseName && versionRef == current?.supposedScalaVersion
        }
        def dependenciesView = nonMatchingDependencyInsights.collect { nonMatchingDependencyInsight ->
            def matchingDepTupleSet = dependencies.collect { dep ->
                DependencyInsight.parse(dep, scalaVersions)
            }.findAll { dependencyInsightPredicate(it, nonMatchingDependencyInsight, scalaVersion) }.collect().toSet()
            new Tuple2(nonMatchingDependencyInsight, matchingDepTupleSet)
        }

        dependenciesView
    }

    /**
     * Filters out all dependencies that match given 'scalaVersion' from a set of dependencies.
     * Excluding dependencies with scala version placeholder '_?'
     *
     * @param dependencySet set of dependencies in the form of {@link org.gradle.api.artifacts.DependencySet} to scan
     * @param scalaVersion Scala Version to un-match against i.e '2.10', '2.11'
     * @param scalaVersions
     * @return a list of tuples in the form of
     *          (groupName:baseArchiveName, scalaVersion, {@link org.gradle.api.artifacts.Dependency})
     *          i.e. ('lib', '2.11', ...)
     */
    static List<DependencyInsight> findAllNonMatchingScalaVersionDependenciesQMarksExcluded(
            Set<Dependency> dependencySet,
            String scalaVersion,
            ScalaVersions scalaVersions) {
        findAllNonMatchingScalaVersionDependencies(dependencySet, scalaVersion, scalaVersions).findAll {
            it.supposedScalaVersion != '?' }
    }

    /**
     * Find scala-library dependencies in the given dependency set
     *
     * @param dependencySet
     * @param scalaVersions
     * @return
     */
    static List<DependencyInsight> findScalaDependencies(Set<Dependency> dependencySet, ScalaVersions scalaVersions) {
        def isScalaLibDependency = { dependency ->
            "${dependency.group}:${dependency.name}" == 'org.scala-lang:scala-library'
        }
        def scalaDeps = dependencySet.findAll(isScalaLibDependency).collect { dep ->
            def scalaVersionInsights = new ScalaVersionInsights(dep.version, scalaVersions)
            def dependencyInsight = DependencyInsight.parse(dep, scalaVersions)
            new DependencyInsight(baseName:dependencyInsight.baseName,
                    supposedScalaVersion:scalaVersionInsights.artifactInlinedVersion,
                    appendix:dependencyInsight.appendix,
                    group:dependencyInsight.group,
                    version:dependencyInsight.version,
                    dependency:dependencyInsight.dependency)
        }

        scalaDeps
    }

    /**
     * Filters out all dependencies that match given 'scalaVersion' from a set of dependencies.
     *
     * @param dependencySet set of dependencies in the form of {@link org.gradle.api.artifacts.DependencySet} to scan
     * @param scalaVersion Scala Version to un-match against i.e '2.10', '2.11'
     * @param scalaVersions
     * @return a list of tuples in the form of
     *          (baseName, scalaVersion, {@link org.gradle.api.artifacts.Dependency})
     *          i.e. ('lib', '2.11', ...)
     */
    static List<DependencyInsight> findAllNonMatchingScalaVersionDependencies(Collection<Dependency> dependencySet,
                                                                              String scalaVersion,
                                                                              ScalaVersions scalaVersions) {
        def nonMatchingDeps = dependencySet.collect { dependency ->
            DependencyInsight.parse(dependency, scalaVersions)
        }.findAll { it.supposedScalaVersion != null }.findAll { it.supposedScalaVersion != scalaVersion }

        nonMatchingDeps
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
     * in {@code project.afterEvaluated} see {@link #findAllCrossBuildPluginAppliedProjects}
     *
     * @return A set of {@link ProjectDependency} that belong to the dependency graph originated from the initial
     *         project type dependencies found in the initial dependency set
     */
    Set<ProjectDependency> extractCrossBuildProjectTypeDependencies() {
        def modules = findAllCrossBuildPluginAppliedProjects()

        def initialDependencySet = diContext.dependencies.collectMany { it.toSet() }
        def configuration = diContext.configurations.current
        def parentConfiguration = diContext.configurations.parent
        def configurationSet = [configuration.name, parentConfiguration?.name].findAll { it != null } as Set
        def dependencies = extractCrossBuildProjectTypeDependenciesRecursively(modules, initialDependencySet.toSet(),
                configurationSet)

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
    private Set<ProjectDependency> extractCrossBuildProjectTypeDependenciesRecursively(
            Set<Project> modules,
            Set<Dependency> inputDependencySet,
            Set<String> configurationNames) {

        Set<ProjectDependency> accum = []

        def currentProjectTypDeps = inputDependencySet.findAll(isProjectDependency).findAll { isValid(it, modules) }
                .findAll { isNotAccumulated(it, accum) }.collect { it as ProjectDependency }
        def currentProjectTypDepsForDefault = currentProjectTypDeps.findAll { it.targetConfiguration == null }
        if (currentProjectTypDepsForDefault.size() > 0) {
            accum.addAll(currentProjectTypDepsForDefault)
            def currentProjectTypeDependenciesDependencies = currentProjectTypDepsForDefault.collectMany { prjDep ->
                extractCrossBuildProjectTypeDependencyDependencies(prjDep, configurationNames)
            }
            accum.addAll(extractCrossBuildProjectTypeDependenciesRecursively(modules,
                    currentProjectTypeDependenciesDependencies.toSet(), configurationNames))
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

