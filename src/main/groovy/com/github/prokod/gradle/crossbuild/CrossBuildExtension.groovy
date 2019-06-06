package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.model.ArchiveNaming
import com.github.prokod.gradle.crossbuild.model.Build
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildConfigLifecycle
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Extension class impl. for thec cross build plugin
 */
class CrossBuildExtension {

    final Project project

    // TODO: rename to catalog
    Map<String, String> scalaVersions = [:]

    ArchiveNaming archive

    Set<Configuration> configurations = []

    NamedDomainObjectContainer<Build> builds

    Collection<ResolvedBuildConfigLifecycle> resolvedBuilds = []

    CrossBuildSourceSets1 crossBuildSourceSets

    CrossBuildExtension(Project project) {
        this.project = project

        this.archive = project.objects.newInstance(ArchiveNaming)

        this.builds = project.container(Build)

        this.crossBuildSourceSets = new CrossBuildSourceSets1(project)

        builds.all { Build build ->
            updateBuild(build)
            updateExtension(build)
        }
    }

    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Action<? super ArchiveNaming> action) {
        action.execute(archive)
        builds.all { Build build ->
            applyArchiveDefaults(build)
        }
    }

    @SuppressWarnings(['ConfusingMethodName', 'BuilderMethodWithSideEffects', 'FactoryMethodName'])
    void builds(Action<? super NamedDomainObjectContainer<Build>> action) {
        action.execute(builds)
    }

    void updateBuild(Build build) {
        build.archive = project.objects.newInstance(ArchiveNaming)
        applyArchiveDefaults(build)
    }

    void applyArchiveDefaults(Build build) {
        if (build.archive.appendixPattern == null) {
            build.archive.appendixPattern = this.archive.appendixPattern
        }
    }

    void updateExtension(Build build) {
        def sv = ScalaVersions.withDefaultsAsFallback(scalaVersions)

        def resolvedBuild = BuildResolver.resolve(build, sv)
        // Create cross build source sets
        crossBuildSourceSets.fromBuilds([resolvedBuild])

        resolvedBuilds.add(resolvedBuild)
    }
}
