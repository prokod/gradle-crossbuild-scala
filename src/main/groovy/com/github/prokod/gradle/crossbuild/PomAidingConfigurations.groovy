package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.utils.CrossBuildPluginUtils
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.DependencyInsightsContext
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.SourceSet

/**
 * {@link org.gradle.api.artifacts.ConfigurationContainer} manipulation class to set Pom aiding Configuration
 *  that aids in pom creation while using publish-maven plugin
 *
 */
class PomAidingConfigurations {
    private final Project project
    private final SourceSet sourceSet
    private final ScalaVersionInsights scalaVersionInsights
    private final String archiveAppendix

    /**
     * Constructor
     *
     * @param project Project space {@link org.gradle.api.Project}
     * @param sourceSet A specific {@link org.gradle.api.tasks.SourceSet} that provides a configuration
     *                   to use as source of dependencies for the new configuration
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @param archiveAppendix {@link com.github.prokod.gradle.crossbuild.model.ResolvedArchiveNaming} archive appendix
     *                         to aid with the replacement of non cross build
     *                        {@link org.gradle.api.artifacts.ProjectDependency} with its cross build counterpart
     *
     */
    PomAidingConfigurations(Project project,
                            SourceSet sourceSet,
                            ScalaVersionInsights scalaVersionInsights,
                            String archiveAppendix) {
        this.project = project
        this.sourceSet = sourceSet
        this.scalaVersionInsights = scalaVersionInsights
        this.archiveAppendix = archiveAppendix
    }

    PomAidingConfigurations(Project project,
                            SourceSet sourceSet,
                            ScalaVersionInsights scalaVersionInsights) {
        this.project = project
        this.sourceSet = sourceSet
        this.scalaVersionInsights = scalaVersionInsights
        this.archiveAppendix = null
    }

    /**
     * Creates a Compile Scope configuration that should be used by the user for generating the dependencies
     *  for a cross build artifact's pom file.
     *
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @return Created configuration
     * @throws org.gradle.api.InvalidUserDataException if an object with the given name already exists in this
     *         container.
     */
    Configuration createAndSetForMavenScope(ScopeType scopeType) {
        def mavenToGradleScope = { ScopeType scope ->
            switch (scope) {
                case ScopeType.COMPILE:
                    return project.configurations[sourceSet.compileConfigurationName]
                case ScopeType.PROVIDED:
                    return project.configurations[sourceSet.compileOnlyConfigurationName]
            }
        }

        def dependencySetFunction = { ScopeType scope ->
            def configuration = mavenToGradleScope(scope)
            switch (scope) {
                case ScopeType.COMPILE:
                    return configuration.allDependencies
                case ScopeType.PROVIDED:
                    def compileOnlySet = configuration.allDependencies
                    def compileSet = project.configurations[sourceSet.compileConfigurationName].allDependencies
                    return (compileOnlySet - compileSet)
            }
        }

        def createdTargetMavenScopeConfig =
                project.configurations.create(mavenScopeConfigurationNameFor(scopeType))

        project.logger.info(LoggerUtils.logTemplate(project,
                lifecycle:'afterEvaluate',
                sourceset:sourceSet.name,
                configuration:mavenToGradleScope(scopeType).name,
                msg:"Created Maven scope ${scopeType} related configuration: ${createdTargetMavenScopeConfig.name}"
        ))

        set(createdTargetMavenScopeConfig, dependencySetFunction(scopeType))
        createdTargetMavenScopeConfig
    }

    private void set(Configuration target, Set<Dependency> sourceDependencies) {
        target.dependencies.addAll(assembleProjectTypeDependencies(sourceDependencies))
        target.dependencies.addAll(assemble3rdPartyDependencies(sourceDependencies))
        target.dependencies.addAll(assemble3rdPartyScalaLibDependencies(sourceDependencies))
        target.dependencies.addAll(assemble3rdPartyGlobedScalaLibDependencies(sourceDependencies))
    }

    String mavenScopeConfigurationNameFor(ScopeType scopeType) {
        "${sourceSet.name}${MAVEN_SCOPE_SUFFIX_FUNC(scopeType)}".toString()
    }

    @SuppressWarnings('FieldTypeRequired')
    private static final MAVEN_SCOPE_SUFFIX_FUNC = { ScopeType scopeType ->
        "Maven${scopeType.toString().toLowerCase().capitalize()}Scope"
    }

    /**
     * Assembles Project type dependencies {@link ProjectDependency} that are cross built
     *
     * @param sourceConfig A specific {@link SourceSet} configuration to use as source for dependencies
     * @return List of generated dependencies based on the original assembled ones
     */
    private Set<Dependency> assembleProjectTypeDependencies(Set<Dependency> sourceDependencies) {
        assert archiveAppendix != null:'archiveAppendix must be set'

        def allDependencies = sourceDependencies
        def diContext = new DependencyInsightsContext(project:project)
        def di = new DependencyInsights(diContext)

        def moduleNames = di.findAllCrossBuildPluginAppliedProjects()*.name
        def crossBuildProjectDependencySet = allDependencies.findAll(DependencyInsights.isProjectDependency).findAll {
            moduleNames.contains(it.name)
        }

        // Internal dependencies(modules) as qualified dependencies
        def crossBuildProjectDependencySetTransformed = crossBuildProjectDependencySet.collect {
            project.dependencies.create(
                    group:it.group,
                    name:it.name +
                            CrossBuildPluginUtils.qmarkReplace(
                                    archiveAppendix, scalaVersionInsights.artifactInlinedVersion),
                    version:it.version)
        }
        crossBuildProjectDependencySetTransformed.toSet()
    }

    /**
     * Assembles external cross built valid dependencies (3rd party Scala libs)
     * (pick latest version if the same dependency module appears multiple times with different versions)
     *
     * @param sourceConfig A specific {@link SourceSet} configuration to use as source for dependencies
     * @return List of dependencies assembled
     */
    private Set<Dependency> assemble3rdPartyScalaLibDependencies(Set<Dependency> sourceDependencies) {
        def allDependencies = sourceDependencies

        def dependenciesView = DependencyInsights.findAllNonMatchingScalaVersionDependenciesWithCounterparts(
                allDependencies.collect(), scalaVersionInsights.artifactInlinedVersion)

        // External cross built valid dependencies
        //  (pick latest version if the same dependency module appears multiple times with different versions)
        def crossBuildExternalDependencySet = dependenciesView.collect { tuple2 ->
            Set<Tuple> matchingDependencyTuples = tuple2.second
            matchingDependencyTuples.toSorted {
                t1, t2 -> t1[2].version <=> t2[2].version
            }
        }.findAll {
            !it.isEmpty()
        }.collect {
            it.last()[2]
        }.toSet()
        crossBuildExternalDependencySet
    }

    /**
     * Assembles external non cross built valid dependencies (3rd party libs)
     *
     * @param sourceConfig A specific {@link SourceSet} configuration to use as source for dependencies
     * @return List of dependencies assembled
     */
    private Set<Dependency> assemble3rdPartyDependencies(Set<Dependency> sourceDependencies) {
        def allDependencies = sourceDependencies

        def diContext = new DependencyInsightsContext(project:project)
        def di = new DependencyInsights(diContext)

        def moduleNames = di.findAllCrossBuildPluginAppliedProjects()*.name

        def crossBuildProjectDependencySet = allDependencies.findAll(DependencyInsights.isProjectDependency)findAll {
            moduleNames.contains(it.name)
        }

        def dependenciesView = DependencyInsights.findAllNonMatchingScalaVersionDependenciesWithCounterparts(
                allDependencies.collect(), scalaVersionInsights.artifactInlinedVersion)

        // External non cross built dependencies
        def crossBuiltExternalDependencySet = dependenciesView.collectMany { entry ->
            Set<Tuple> matchingDepTuples = entry.second
            matchingDepTuples.collect { it[2] } }.toSet()
        def probablyCrossBuiltExternalDependencySet = dependenciesView.collect { entry ->
            Tuple nonMatchingDepTuple = entry.first
            nonMatchingDepTuple[2] }.toSet()
        def nonCrossBuildExternalDependencySet = allDependencies - crossBuildProjectDependencySet -
                (crossBuiltExternalDependencySet + probablyCrossBuiltExternalDependencySet)
        def groupedByGroupAndName = nonCrossBuildExternalDependencySet.groupBy { dep -> dep.group + dep.name }
        def nonCrossBuildExternalDependencySetSanitized = groupedByGroupAndName.collect { entry ->
            if (entry.getValue().size() > 1) {
                entry.getValue().findAll { dep ->
                    !dep.version.contains('+')
                }.findAll { dep ->
                    if (dep.group == 'org.scala-lang' && dep.name == 'scala-library') {
                        dep.version.startsWith(scalaVersionInsights.baseVersion)
                    } else {
                        true
                    }
                }.head()
            } else {
                entry.value.head()
            }
        }
        nonCrossBuildExternalDependencySetSanitized
    }

    /**
     * Assembles external cross built valid dependencies (3rd party Scala libs) that are glob annotated (_?)
     *
     * @param sourceConfig A specific {@link SourceSet} configuration to use as source for dependencies
     * @return List of dependencies assembled
     */
    private Set<Dependency> assemble3rdPartyGlobedScalaLibDependencies(Set<Dependency> sourceDependencies) {
        def allDependencies = sourceDependencies

        def dependenciesView = DependencyInsights.findAllNonMatchingScalaVersionDependenciesWithCounterparts(
                allDependencies.collect(), scalaVersionInsights.artifactInlinedVersion)

        // External cross built globed (_?) dependencies
        def resolvedCrossBuiltExternalDependencySet = dependenciesView.collect { entry ->
            Tuple nonMatchingDepTuple = entry[0]
            nonMatchingDepTuple
        }.findAll {
            it[1] == '?'
        }.collect {
            Dependency dep = it[2]
            project.dependencies.create(
                    group:dep.group,
                    name:"${dep.name.replace('?', scalaVersionInsights.artifactInlinedVersion)}",
                    version:dep.version)
        }
        resolvedCrossBuiltExternalDependencySet
    }

    static enum ScopeType {
        COMPILE,
        PROVIDED
    }
}
