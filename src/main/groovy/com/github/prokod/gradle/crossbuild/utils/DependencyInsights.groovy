package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildPlugin
import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.ScalaVersions
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.SourceSet

import java.util.regex.Pattern

/**
 * A collection of Dependency related methods
 *
 */
class DependencyInsights {

    private final DependencyInsightsContext diContext

    DependencyInsights(DependencyInsightsContext diContext) {
        this.diContext = diContext
    }

    static DependencyInsights from(Project project, SourceSet sourceSet) {
        def crossBuildConfiguration = project.configurations.findByName(sourceSet.compileConfigurationName)

        def diContext = new DependencyInsightsContext(project:project,
                dependencies:crossBuildConfiguration.allDependencies,
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
     */
    Set<Dependency> findAllCrossBuildProjectTypeDependenciesDependenciesFor(String configurationName) {
        def projectTypeDependencies = extractCrossBuildProjectTypeDependencies()

        def dependenciesOfProjectDependencies = projectTypeDependencies.collectMany { prjDep ->
            extractCrossBuildProjectTypeDependencyDependencies(prjDep, configurationName)
        }

        dependenciesOfProjectDependencies.toSet()
    }

    Set<Dependency> findAllCrossBuildProjectTypeDependenciesDependenciesForCurrentConfiguration() {
        def configuration = diContext.configurations.current
        findAllCrossBuildProjectTypeDependenciesDependenciesFor(configuration.name)
    }

    Set<Dependency> findAllDependenciesForCurrentConfiguration() {
        def configuration = diContext.configurations.current
        findAllCrossBuildProjectTypeDependenciesDependenciesForCurrentConfiguration() + configuration.allDependencies
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

    /**
     * Parses given dependency to its groupName:baseName part and its scala version part.
     * Throws Assertion Exception if dependency name does not contain separating char '_'
     *
     * @param dep dependency to parse
     * @param scalaVersions
     * @return {@code true} if the dependency is named in scala lib convention, {@code false} otherwise
     */
    static boolean isScalaLib(Dependency dep, ScalaVersions scalaVersions) {
        def supposedlyScalaVersion = parseDependencyName(dep, scalaVersions).second
        supposedlyScalaVersion != null
    }

    /**
     * Parses given dependency to its groupName:baseName part and its scala version part.
     * Throws Assertion Exception if dependency name does not contain separating char '_'
     *
     * @param dependency to parse
     * @return tuple in the form of (groupName:baseName, scalaVersion) i.e. ('group:lib', '2.11')
     */
    static Tuple2<String, String> parseDependencyName(Dependency dep, ScalaVersions scalaVersions) {
        def (baseName, supposedScalaVersion, nameSuffix) = parseDependencyName(dep.name, scalaVersions)
        new Tuple2("${dep.group}:$baseName", supposedScalaVersion)
    }

    /**
     * Parses given dependency name to its baseName part and its scala version part.
     * returns the dependency name unparsed if dependency name does not contain separating char '_'
     *
     * @param depName dependency name to parse
     * @parma scalaVersions
     * @return tuple in the form of (baseName, scalaVersion, appendix) i.e. ('lib', '2.11', '2.2.0')
     *         returns (name, {@code null}, {@code null}) otherwise.
     */
    static Tuple parseDependencyName(String name, ScalaVersions scalaVersions) {
        def refTargetVersions = scalaVersions.mkRefTargetVersions()
        def qMarkDelimiter = Pattern.quote('_?')
        def qMarkSplitPattern = "(?=(?!^)$qMarkDelimiter)|(?<=$qMarkDelimiter)"
        def qMarkTokens = name.split(qMarkSplitPattern)
        def qMarkParsedTuple =  parseTokens(qMarkTokens)

        def parsedTuples = refTargetVersions.collect { version ->
            def delimiter = Pattern.quote('_' + version)
            def splitPattern = "(?=(?!^)$delimiter)|(?<=$delimiter)"
            def tokens = name.split(splitPattern)
            parseTokens(tokens)
        }
        def allParsedTuples = parsedTuples + [qMarkParsedTuple]
        def filtered = allParsedTuples.findAll { it != null }
        if (filtered.size() == 1) {
            filtered.head()
        }
        else {
            new Tuple(name, null, null)
        }
    }

    private static Tuple parseTokens(String[] tokens) {
        if (tokens.size() < 2) {
            null
        }
        else if (tokens.size() == 2) {
            def baseName = tokens[0]
            def supposedScalaVersion = tokens[1].substring(1)
            new Tuple(baseName, supposedScalaVersion, null)
        }
        else if (tokens.size() == 3) {
            def baseName = tokens[0]
            def supposedScalaVersion = tokens[1].substring(1)
            def appendix = tokens[2]
            new Tuple(baseName, supposedScalaVersion, appendix)
        }
        else {
            null
        }
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
    static List<Tuple2<Tuple, Set<Tuple>>> findAllNonMatchingScalaVersionDependenciesWithCounterparts(
            Collection<Dependency> dependencies,
            String scalaVersion,
            ScalaVersions scalaVersions) {
        def nonMatchingDeps = findAllNonMatchingScalaVersionDependencies(dependencies, scalaVersion, scalaVersions)
        def dependenciesView = nonMatchingDeps.collect { nonMatchingDepTuple ->
            def matchingDepTupleSet = dependencies.collect { dep ->
                def (groupAndBaseName, supposedScalaVersion) = parseDependencyName(dep, scalaVersions)
                new Tuple(groupAndBaseName, supposedScalaVersion, dep)
            }.findAll { it[0] == nonMatchingDepTuple[0] && it[1] != null && it[1] == scalaVersion }.collect().toSet()
            new Tuple2(nonMatchingDepTuple, matchingDepTupleSet)
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
    static List<Tuple> findAllNonMatchingScalaVersionDependenciesQMarksExcluded(
            Set<Dependency> dependencySet,
            String scalaVersion,
            ScalaVersions scalaVersions) {
        findAllNonMatchingScalaVersionDependencies(dependencySet, scalaVersion, scalaVersions).findAll { tuples ->
            tuples[1] != '?' }
    }

    /**
     * Find scala-library dependencies in the given dependency set
     *
     * @param dependencySet
     * @param scalaVersions
     * @return
     */
    static List<Tuple> findScalaDependencies(Set<Dependency> dependencySet, ScalaVersions scalaVersions) {
        def scalaDeps = dependencySet
                .findAll { "${it.group}:${it.name}" == 'org.scala-lang:scala-library' }
                .collect { dep ->
            def scalaVersionInsights = new ScalaVersionInsights(dep.version, scalaVersions)
            def groupAndBaseName = parseDependencyName(dep, scalaVersions).first
            new Tuple(groupAndBaseName, scalaVersionInsights.artifactInlinedVersion, dep) }

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
    static List<Tuple> findAllNonMatchingScalaVersionDependencies(Collection<Dependency> dependencySet,
                                                                  String scalaVersion,
                                                                  ScalaVersions scalaVersions) {
        def nonMatchingDeps = dependencySet.collect {
            def (groupAndBaseName, supposedScalaVersion) = parseDependencyName(it, scalaVersions)
            new Tuple(groupAndBaseName, supposedScalaVersion, it) }
        .findAll { it[1] != null && it[1] != scalaVersion }

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
     * in {@code project.afterEvaluated}
     *
     * @return A set of {@link ProjectDependency} that belong to the dependency graph originated from the initial
     *         project type dependencies found in the initial dependency set
     */
    Set<ProjectDependency> extractCrossBuildProjectTypeDependencies() {
        def modules = findAllCrossBuildPluginAppliedProjects()

        def initialDependencySet = diContext.dependencies
        def configuration = diContext.configurations.current
        def dependencies = extractCrossBuildProjectTypeDependenciesRecursively(modules, initialDependencySet.toSet(),
                configuration.name)

        dependencies
    }

    /**
     * Valid Project type dependencies for this method are those with targetConfiguration as 'default' only
     *
     * @param modules
     * @param inputDependencySet
     * @param configurationName
     * @return
     */
    private Set<ProjectDependency> extractCrossBuildProjectTypeDependenciesRecursively(
            Set<Project> modules,
            Set<Dependency> inputDependencySet,
            String configurationName) {

        Set<ProjectDependency> accum = []

        def currentProjectTypDeps = inputDependencySet.findAll(isProjectDependency).findAll { isValid(it, modules) }
                .findAll { isNotAccumulated(it, accum) }.collect { (ProjectDependency) it }
        def currentProjectTypDepsForDefault = currentProjectTypDeps.findAll { it.targetConfiguration == null }
        if (currentProjectTypDepsForDefault.size() > 0) {
            accum.addAll(currentProjectTypDepsForDefault)
            def currentProjectTypeDependenciesDependencies = currentProjectTypDepsForDefault.collectMany { prjDep ->
                extractCrossBuildProjectTypeDependencyDependencies(prjDep, configurationName)
            }
            accum.addAll(extractCrossBuildProjectTypeDependenciesRecursively(modules,
                    currentProjectTypeDependenciesDependencies.toSet(), configurationName))
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
                                                                                      String configurationName) {
        def crossBuildProjectTypeDependencyDeps =
                dependency.dependencyProject.configurations.findByName(configurationName)?.allDependencies

        crossBuildProjectTypeDependencyDeps != null ? crossBuildProjectTypeDependencyDeps.toSet() : [] as Set
    }
}
