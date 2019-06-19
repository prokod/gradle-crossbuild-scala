package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.model.ArchiveNaming
import com.github.prokod.gradle.crossbuild.model.Build
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildConfigLifecycle
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Extension class impl. for thec cross build plugin
 */
class CrossBuildExtension {

    final Project project

    final CrossBuildSourceSets crossBuildSourceSets

    Map<String, String> scalaVersionsCatalog = [:]

    ArchiveNaming archive

    Set<Configuration> configurations = []

    NamedDomainObjectContainer<Build> builds

    Collection<ResolvedBuildConfigLifecycle> resolvedBuilds = []

    CrossBuildExtension(Project project) {
        this.project = project

        this.archive = project.objects.newInstance(ArchiveNaming, '_?')

        this.crossBuildSourceSets = new CrossBuildSourceSets(project)

        this.builds = project.container(Build, buildFactory)

        builds.all { Build build ->
            updateBuild(build)
            updateExtension(build)
        }
    }

    private final Closure buildFactory = { name -> new Build(name, this) }

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
        build.archive = project.objects.newInstance(ArchiveNaming, '_?')
    }

    void applyArchiveDefaults(Build build) {
        build.archive.appendixPattern = this.archive.appendixPattern
    }

    void updateExtension(Build build) {
        def project = build.extension.project
        def sv = ScalaVersions.withDefaultsAsFallback(scalaVersionsCatalog)

        build.onScalaVersionsUpdate { event ->
            def resolvedBuilds = BuildResolver.resolve(build, sv)
            // Create cross build source sets
            project.logger.debug(LoggerUtils.logTemplate(project,
                    lifecycle:'config',
                    msg:'`onScalaVersionsUpdate` callback triggered. Going to create source-sets accordingly.\n' +
                            'Event source (build):\n-----------------\n' +
                            "${event.source.toString()}\n" +
                            "Current build:\n------------\n${build.toString()}\n" +
                            "Resolved builds:\n------------\n${resolvedBuilds*.toString().join('\n')}"
            ))
            crossBuildSourceSets.fromBuilds(resolvedBuilds)

            this.resolvedBuilds.addAll(resolvedBuilds)
        }
    }
}
