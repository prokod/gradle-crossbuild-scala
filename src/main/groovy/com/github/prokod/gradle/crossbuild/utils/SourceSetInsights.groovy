package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.tasks.SourceSet

/**
 * Used within {@link DependencyOps} mainly
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
        this.main = new UniSourceSetInsights(main, project)
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

    static class Builder {

        final String crossBuildName

        Builder(String crossBuildName) {
            this.crossBuildName = crossBuildName
        }

        ProjectTypeBuilder fromPrj(Project project) {
            new ProjectTypeBuilder(project, crossBuildName)
        }

        ExtensionTypeBuilder fromExt(CrossBuildExtension extension) {
            new ExtensionTypeBuilder(extension, crossBuildName)
        }

        static class ProjectTypeBuilder {

            final String crossBuildName
            final Project project
            private SourceSet mainSourceSet = null
            private String mainSourceSetName = null

            ProjectTypeBuilder(Project project, String crossBuildName) {
                this.project = project
                this.crossBuildName = crossBuildName
            }

            ProjectTypeBuilder withMainSourceSet(SourceSet sourceSet) {
                this.mainSourceSet = sourceSet
                this
            }

            ProjectTypeBuilder withMainSourceSetName(String mainSourceSetName) {
                this.mainSourceSetName = mainSourceSetName
                this
            }

            SourceSetInsights build() {
                def extension = project.extensions.findByType(CrossBuildExtension)
                assert extension != null: "Cannot add task ${this.name} of type AbstractCrossBuildPomTask to " +
                        "project ${project.name}. Reason: Tasks of that type can be added only to a cross build " +
                        'applied project'

                def mainName = mainSourceSetName ?: 'main'
                def main = mainSourceSet ?: extension.crossBuildSourceSets.container.findByName(mainName)
                def (String sourceSetId, SourceSet sourceSet) =
                                            extension.crossBuildSourceSets.findByName(crossBuildName)
                def sourceSetInsights = new SourceSetInsights(sourceSet, main, extension.project)

                sourceSetInsights
            }
        }

        static class ExtensionTypeBuilder {

            final String crossBuildName
            final CrossBuildExtension extension
            private SourceSet mainSourceSet = null

            ExtensionTypeBuilder(CrossBuildExtension extension, String crossBuildName) {
                this.extension = extension
                this.crossBuildName = crossBuildName
            }

            ExtensionTypeBuilder withMainSourceSet(SourceSet sourceSet) {
                this.mainSourceSet = sourceSet
                this
            }

            SourceSetInsights build() {
                def main = mainSourceSet ?: extension.crossBuildSourceSets.container.findByName('main')
                def (String sourceSetId, SourceSet sourceSet) =
                                                extension.crossBuildSourceSets.findByName(crossBuildName)
                def sourceSetInsights = new SourceSetInsights(sourceSet, main, extension.project)

                sourceSetInsights
            }
        }
    }
}
