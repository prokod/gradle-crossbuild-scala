package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet

/**
 * A wrapper on top of {@link SourceSetInsights} giving a limited more detailed view on a specific config type
 * see {@link SourceSetInsights.ViewType}
 */
class SourceSetInsightsView {
    private final SourceSetInsights sourceSetInsights
    private final SourceSetInsights.ViewType viewType

    SourceSetInsightsView(SourceSetInsights sourceSetInsights,
                          SourceSetInsights.ViewType viewType) {
        this.sourceSetInsights = sourceSetInsights
        this.viewType = viewType
    }

    static SourceSetInsightsView from(Configuration configuration, SourceSetInsights sourceSetInsights) {
        def view = SourceSetInsights.ViewType.from(configuration.name)
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
        def container = CrossBuildSourceSets.getSourceSetContainer(project)
        def newCrossBuild = container.getByName(this.sourceSetInsights.crossBuild.name)
        def newMain = container.getByName(this.sourceSetInsights.main.name)
        def newSourceSetInsights = new SourceSetInsights(newCrossBuild, newMain, project)
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

    SourceSetInsights.ViewType getViewType() {
        this.viewType
    }

    /**
     * ALL is intended for {@link Configuration#getAllDependencies()}
     * SINGLE is intended for {@link Configuration#getDependencies()}
     */
    static enum DependencySetType {
        ALL, SINGLE
    }

    static class SourceSetInsight<R, S> {
        private final Tuple2<R, R> rawInsight
        private final Closure<S> flatMapFunc

        SourceSetInsight(Tuple2<R, R> rawInsight, Closure<Collection<S>> flatMapFunc) {
            this.rawInsight = rawInsight
            this.flatMapFunc = flatMapFunc
        }

        List<S> flatMapped() {
            rawInsight.findAll { it != null }.collectMany(flatMapFunc)
        }

        R getCrossBuild() {
            rawInsight.first
        }

        R getMain() {
            rawInsight.second
        }
    }
}
