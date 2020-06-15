package com.github.prokod.gradle.crossbuild.model

import javax.inject.Inject

/**
 * cross build plugin DSL representation for {@code archive.appendixPattern}
 *
 * Observable by {@link com.github.prokod.gradle.crossbuild.CrossBuildExtension} through
 * {@link BuildUpdateEventStore}
 */
class ArchiveNaming {
    final String name
    final BuildUpdateEventStore eventStore
    String appendixPattern
    String scalaTag

    static ArchiveNaming from(ArchiveNamingSnapshot snapshot) {
        new ArchiveNaming(snapshot.name, snapshot.appendixPattern, snapshot.scalaTag, null)
    }

    @Inject
    ArchiveNaming(String name, String appendixPattern, String scalaTag, BuildUpdateEventStore eventStore) {
        this.name = name
        this.appendixPattern = appendixPattern
        this.eventStore = eventStore
        this.scalaTag = scalaTag ?: '_?'
    }

    /**
     * Triggered when a DSL of the following form appears:
     * <pre>
     *     crossBuild {
     *         builds {
     *             v213 {
     *                 archive.appendixPattern = ...
     *             }
     *         }
     *     }
     * </pre>
     *
     * @param appendixPattern
     */
    void setAppendixPattern(String appendixPattern) {
        def oldValue = this.appendixPattern
        this.appendixPattern = appendixPattern
        def newValue = appendixPattern

        if (oldValue != newValue) {
            eventStore?.store(new ArchiveNamingUpdateEvent(this))
        }
    }

    /**
     * Triggered when a DSL of the following form appears:
     * <pre>
     *     crossBuild {
     *         builds {
     *             v213 {
     *                 archive.setScalaTag = ...
     *             }
     *         }
     *     }
     * </pre>
     *
     * @param appendixPattern
     */
    void setScalaTag(String scalaTag) {
        def oldValue = this.scalaTag
        this.scalaTag = scalaTag

        if (oldValue != scalaTag) {
            eventStore?.store(new ArchiveNamingUpdateEvent(this))
        }
    }
}
