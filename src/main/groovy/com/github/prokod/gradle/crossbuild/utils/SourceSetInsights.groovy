package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.utils.SourceSetInsightsView.DependencySetType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.tasks.SourceSet

/**
 * Used within {@link DependencyInsights} mainly
 *
 * Semantically binding main source set and cross build source set over the same configuration type see
 * {@link ViewType}
 *
 * For each such pair the following are available respectively: Configuration names, Configurations and dependency sets
 *
 */
class SourceSetInsights {
    final UniSourceSetInsights crossBuild
    final UniSourceSetInsights main
    final Project project

    SourceSetInsights(SourceSet crossBuild, SourceSet main, Project project) {
        this.crossBuild = new UniSourceSetInsights(crossBuild, project)
        this.main = new UniSourceSetInsights(main,project)
        this.project = project
    }

    Tuple2<String, String> getNamesFor(ViewType configurationType) {
        new Tuple2<String, String>(crossBuild.getNameFor(configurationType), main.getNameFor(configurationType))
    }

    Tuple2<Configuration, Configuration> getConfigurationsFor(ViewType configurationType) {
        new Tuple2<Configuration, Configuration>(
                crossBuild.getConfigurationFor(configurationType),
                main.getConfigurationFor(configurationType))
    }

    Tuple2<DependencySet, DependencySet> getDependencySetsFor(ViewType configurationType,
                                                              DependencySetType dependencySetType) {
        new Tuple2<DependencySet, DependencySet>(
                crossBuild.getDependencySetFor(configurationType, dependencySetType),
                main.getDependencySetFor(configurationType, dependencySetType))
    }
}
