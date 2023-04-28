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

    final TargetCompatibility targetCompatibility

    Set<String> scalaVersions

    Map<String, Object> ext

    static Build from(BuildSnapshot snapshot) {
        new Build(snapshot.name,
                snapshot.extension,
                null,
                ArchiveNaming.from(snapshot.archive),
                TargetCompatibility.from(snapshot.targetCompatibility),
                snapshot.scalaVersions,
                snapshot.ext)
    }

    @SuppressWarnings('ParameterCount')
    Build(String name,
          CrossBuildExtension extension,
          BuildUpdateEventStore eventStore,
          ArchiveNaming archive,
          TargetCompatibility targetCompatibility,
          Set<String> scalaVersions,
          Map<String, Object> ext) {
        this.name = name
        this.extension = extension
        this.eventStore = eventStore
        this.archive = archive
        this.targetCompatibility = targetCompatibility
        this.scalaVersions = scalaVersions
        this.ext = ext
    }

    /**
     * Defaults for DSL object Build
     *
     * @param name
     * @param extension
     */
    @Inject
    Build(String name, CrossBuildExtension extension) {
        this.name = name.replaceAll('\\.', '')
        this.extension = extension
        this.eventStore = new BuildUpdateEventStore(extension.project)
        this.archive = extension.project.objects.newInstance(ArchiveNaming, name, '_?', this.eventStore)
        this.targetCompatibility =
                extension.project.objects.newInstance(TargetCompatibility, name, 'default', this.eventStore)
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

    @SuppressWarnings(['ConfusingMethodName'])
    void targetCompatibility(Action<? super TargetCompatibility> action) {
        action.execute(targetCompatibility)
    }

    /**
     * Triggered when a DSL of the following form appears:
     * <pre>
     *     crossBuild {
     *         builds {
     *             v213 {
     *                 targetCompatibility {
     *                     strategy = ...
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
    void targetCompatibility(Closure c) {
        def oldValue = targetCompatibility.strategy
        org.gradle.util.ConfigureUtil.configure(c, targetCompatibility)
        def newValue = targetCompatibility.strategy

        if (oldValue != newValue) {
            eventStore.store(new BuildUpdateEvent(this, EventType.TARGET_COMPATIBILITY_STRATEGY_UPDATE))
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

    void setExt(Map<String, Object> ext) {
        this.ext = ext
        eventStore.store(new ExtUpdateEvent(this.name, this.ext))
    }

    String toString() {
        JsonOutput.toJson([name:name,
                           scalaVersions:scalaVersions,
                           ext:ext,
                           archive:[appendixPattern:archive?.appendixPattern],
                           targetCompatibility:[strategy:targetCompatibility?.strategy]])
    }
}
