package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet

/**
 * A wrapper on top of {@link UniSourceSetInsights} giving a limited more detailed view on a specific config type
 * see {@link ViewType}
 */
class UniSourceSetInsightsView {
    private final UniSourceSetInsights sourceSetInsights
    private final ViewType viewType

    UniSourceSetInsightsView(UniSourceSetInsights sourceSetInsights,
                             ViewType viewType) {
        this.sourceSetInsights = sourceSetInsights
        this.viewType = viewType
    }

    static UniSourceSetInsightsView from(Configuration configuration, UniSourceSetInsights sourceSetInsights) {
        def view = ViewType.from(configuration.name)
        new UniSourceSetInsightsView(sourceSetInsights, view)
    }

    /**
     *
     * @param project
     * @return
     *
     * @throws AssertionError in case sourceSet container is not present for the project
     */
    UniSourceSetInsightsView switchTo(Project project) {
        def container = CrossBuildSourceSets.getSourceSetContainer(project)
        def newSourceSet = container.getByName(this.sourceSetInsights.sourceSet.name)
        def newSourceSetInsights = new UniSourceSetInsights(newSourceSet, project)
        new UniSourceSetInsightsView(newSourceSetInsights, this.viewType)
    }

    SourceSetInsight<String, String> getNames() {
        def rawInsight = sourceSetInsights.getNameFor(viewType)
        new SourceSetInsight<String, String>(rawInsight, { String name -> [name] })
    }

    SourceSetInsight<Configuration, Configuration> getConfigurations() {
        def rawInsight = sourceSetInsights.getConfigurationFor(viewType)
        new SourceSetInsight<Configuration, Configuration>(rawInsight, { c -> [c] })
    }

    SourceSetInsight<DependencySet, Dependency> getDependencySets(DependencySetType dependencySetType) {
        def rawInsight = sourceSetInsights.getDependencySetFor(viewType, dependencySetType)
        new SourceSetInsight<DependencySet, Dependency>(rawInsight, { DependencySet dset -> dset.toList() })
    }

    /**
     * Defaults to {@link DependencySetType#SINGLE} as it is safer.
     *
     * @return
     */
    SourceSetInsight<DependencySet, Dependency> getDependencySets() {
        getDependencySets(DependencySetType.SINGLE)
    }

    Project getProject() {
        sourceSetInsights.project
    }

    ViewType getViewType() {
        this.viewType
    }
}
