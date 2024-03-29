package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet

/**
 * A wrapper on top of {@link SourceSetInsights} giving a limited more detailed view on a specific config type
 * see {@link ViewType}
 */
class SourceSetInsightsView {
    private final SourceSetInsights sourceSetInsights
    private final ViewType viewType

    SourceSetInsightsView(SourceSetInsights sourceSetInsights,
                          ViewType viewType) {
        this.sourceSetInsights = sourceSetInsights
        this.viewType = viewType
    }

    static SourceSetInsightsView from(Configuration configuration, SourceSetInsights sourceSetInsights) {
        def view = ViewType.from(configuration.name)
        new SourceSetInsightsView(sourceSetInsights, view)
    }

    /**
     *
     * @param project
     * @return
     *
     * @throws AssertionError in case sourceSet container is not present for the project
     */
    SourceSetInsightsView switchTo(Project project) {
        def buildName =
                CrossBuildSourceSets.convertSourceSetIdToBuildName(this.sourceSetInsights.crossBuild.sourceSet.name)
        def newSourceSetInsights = new SourceSetInsights.Builder(buildName)
                .fromPrj(project)
                .withMainSourceSetName(this.sourceSetInsights.main.sourceSet.name)
                .build()
        new SourceSetInsightsView(newSourceSetInsights, this.viewType)
    }

    SourceSetInsight<String, String> getNames() {
        def rawInsight = sourceSetInsights.getNamesFor(viewType)
        new SourceSetInsight<String, String>(rawInsight, { String name -> [name] })
    }

    SourceSetInsight<Configuration, Configuration> getConfigurations() {
        def rawInsight = sourceSetInsights.getConfigurationsFor(viewType)
        new SourceSetInsight<Configuration, Configuration>(rawInsight, { c -> [c] })
    }

    SourceSetInsight<DependencySet, Dependency> getDependencySets(DependencySetType dependencySetType) {
        def rawInsight = sourceSetInsights.getDependencySetsFor(viewType, dependencySetType)
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
