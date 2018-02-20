package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.utils.CrossBuildPluginUtils
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
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
    static final String CONFIGURATION_NAME_SUFFIX = 'MavenCompileScope'

    private final Project project
    private final SourceSet sourceSet

    /**
     * Constructor
     *
     * @param project Project space {@link org.gradle.api.Project}
     * @param sourceSet A specific {@link org.gradle.api.tasks.SourceSet} that provides a configuration
     *                   to use as source of dependencies for the new configuration
     *
     */
    PomAidingConfigurations(Project project, SourceSet sourceSet) {
        this.project = project
        this.sourceSet = sourceSet
    }
    /**
     * Creates a Compile Scope configuration that should be used by the user for generating the dependencies
     *  for a cross build artifact's pom file.
     *
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @param archiveAppendix {@link com.github.prokod.gradle.crossbuild.model.TargetVerItem} archiveAppendix
     *                         to aid with the replacement of non cross build
     *                         {@link org.gradle.api.artifacts.ProjectDependency} with its cross build counterpart
     * @throws org.gradle.api.InvalidUserDataException if an object with the given name already exists in this
     *         container.
     */
    void addCompileScopeConfiguration(
            ScalaVersionInsights scalaVersionInsights,
            String archiveAppendix) {
        def sourceConfig = project.configurations[sourceSet.runtimeConfigurationName]
        def targetCompileScopeConfig = project.configurations.create("${sourceSet.name}${CONFIGURATION_NAME_SUFFIX}")

        populatePomAidingConfiguration(
                sourceConfig, targetCompileScopeConfig, scalaVersionInsights, archiveAppendix)
    }

    /**
     * Creates a configuration that should be used by the user for generating the dependencies
     *  for a cross build artifact's pom file.
     *
     * @param sourceConfig A specific {@link SourceSet} configuration to use as source for dependencies
     * @param targetCompileScopeConfig Target pom aiding configuration
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @param archiveAppendix {@link com.github.prokod.gradle.crossbuild.model.TargetVerItem} archiveAppendix
     *                         to aid with the replacement of non cross build
     *                         {@link org.gradle.api.artifacts.ProjectDependency} with its cross build counterpart
     */
    private void populatePomAidingConfiguration(
            Configuration sourceConfig,
            Configuration targetCompileScopeConfig,
            ScalaVersionInsights scalaVersionInsights,
            String archiveAppendix) {
        def allDependencies = sourceConfig.allDependencies
        def dependenciesView = DependencyInsights.findAllNonMatchingScalaVersionDependenciesWithCounterparts(
                allDependencies, scalaVersionInsights.artifactInlinedVersion)

        def moduleNames = CrossBuildPluginUtils.findAllNamesForCrossBuildPluginAppliedProjects(project)
        def crossBuildProjectDependencySet = allDependencies.findAll {
            it instanceof ProjectDependency
        }.findAll {
            moduleNames.contains(it.name)
        }

        // Add internal dependencies(modules) as qualified dependencies
        def crossBuildProjectDependencySetTransformed = crossBuildProjectDependencySet.collect {
            project.dependencies.create(
                    group:it.group,
                    name:it.name +
                            CrossBuildPluginUtils.qmarkReplace(
                                    archiveAppendix, scalaVersionInsights.artifactInlinedVersion),
                    version:it.version)
        }
        targetCompileScopeConfig.dependencies.addAll(crossBuildProjectDependencySetTransformed)

        // Add external cross built valid dependencies
        //  (pick latest version if the same dependency module appears multiple times with different versions)
        def crossBuildExternalDependencySet = dependenciesView.collect { tuple2 ->
            Set<Tuple> matchingDepTuples = tuple2.second
            matchingDepTuples.toSorted {
                t1, t2 -> t1[2].version <=> t2[2].version
            }
        }.findAll {
            !it.isEmpty()
        }.collect {
            it.last()[2]
        }.toSet()
        targetCompileScopeConfig.dependencies.addAll(crossBuildExternalDependencySet)

        // Add external non crossed built dependencies
        def crossBuiltExternalDependencySet = dependenciesView.collectMany { entry ->
            Set<Tuple> matchingDepTuples = entry.second
            matchingDepTuples.collect { it[2] } }.toSet()
        def probablyCrossBuiltExternalDependencySet = dependenciesView.collect { entry ->
            Tuple nonMatchingDepTuple = entry.first
            nonMatchingDepTuple[2] }.toSet()
        def nonCrossBuildExternalDependencySet = allDependencies -
                crossBuildProjectDependencySet -
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
        targetCompileScopeConfig.dependencies.addAll(nonCrossBuildExternalDependencySetSanitized)

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
        targetCompileScopeConfig.dependencies.addAll(resolvedCrossBuiltExternalDependencySet)
    }
}
