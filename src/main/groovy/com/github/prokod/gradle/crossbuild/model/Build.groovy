package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.CrossBuildExtension
import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.ScalaVersions
import groovy.json.JsonOutput
import groovy.transform.AutoClone
import org.gradle.api.Action

import javax.inject.Inject

/**
 * cross build plugin DSL representation for individual build items in {@code builds} block
 *
 * Observable by {@link com.github.prokod.gradle.crossbuild.CrossBuildExtension} through
 * {@link BuildUpdateEventStore}
 */
@AutoClone
class Build {
    final String name

    final CrossBuildExtension extension

    final BuildUpdateEventStore eventStore

    final ArchiveNaming archive

    Set<String> scalaVersions

    static Build from(BuildSnapshot snapshot) {
        new Build(snapshot.name, snapshot.extension, null, ArchiveNaming.from(snapshot.archive), snapshot.scalaVersions)
    }

    Build(String name,
          CrossBuildExtension extension,
          BuildUpdateEventStore eventStore,
          ArchiveNaming archive,
          Set<String> scalaVersions) {
        this.name = name
        this.extension = extension
        this.eventStore = eventStore
        this.archive = archive
        this.scalaVersions = scalaVersions
    }

    @Inject
    Build(String name, CrossBuildExtension extension) {
        this.name = name
        this.extension = extension
        this.eventStore = new BuildUpdateEventStore(extension.project)
        this.archive = extension.project.objects.newInstance(ArchiveNaming, name, '_?', this.eventStore)
        trySettingScalaVersionImplicitlyFrom(name)
    }

    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Action<? super ArchiveNaming> action) {
        action.execute(archive)
    }

    /**
     * Triggered when a DSL of the following form appears:
     * <pre>
     *     crossBuild {
     *         builds {
     *             v213 {
     *                 archive {
     *                     appendixPattern = ...
     *                 }
     *             }
     *         }
     *     }
     * </pre>
     *
     * NOTE: This is needed for Gradle 4.x. Gradle 5.x already solves this and this method is not needed any more when
     * the plugin is being used with Gradle 5.x
     *
     * @param c
     */
    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Closure c) {
        def oldValue = archive.appendixPattern
        org.gradle.util.ConfigureUtil.configure(c, archive)
        def newValue = archive.appendixPattern

        if (oldValue != newValue) {
            eventStore.store(new BuildUpdateEvent(this, EventType.ARCHIVE_APPENDIX_PATTERN_UPDATE))
        }
    }

    private void trySettingScalaVersionImplicitlyFrom(String name) {
        def catalog = extension.scalaVersionsCatalog
        def resolvedCatalog = ScalaVersions.withDefaultsAsFallback(catalog)
        def versionInsights = resolvedCatalog.catalog.collect { new ScalaVersionInsights(it.value) }
                        .find { vi -> name.endsWith("${vi.strippedArtifactInlinedVersion}") }
        if (versionInsights != null) {
            setScalaVersions([versionInsights.artifactInlinedVersion] as Set)
        }
    }

    void setScalaVersions(Set<String> scalaVersions) {
        this.scalaVersions = scalaVersions
        eventStore.store(new BuildUpdateEvent(this, EventType.SCALA_VERSIONS_UPDATE))
    }

    String toString() {
        JsonOutput.toJson([name:name,
                           scalaVersions:scalaVersions,
                           archive:[appendixPattern:archive?.appendixPattern]])
    }
}
