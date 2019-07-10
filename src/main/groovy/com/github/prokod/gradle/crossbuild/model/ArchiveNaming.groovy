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

    static ArchiveNaming from(ArchiveNamingSnapshot snapshot) {
        new ArchiveNaming(snapshot.name, snapshot.appendixPattern, null)
    }

    @Inject
    ArchiveNaming(String name, String appendixPattern, BuildUpdateEventStore eventStore) {
        this.name = name
        this.appendixPattern = appendixPattern
        this.eventStore = eventStore
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
}
