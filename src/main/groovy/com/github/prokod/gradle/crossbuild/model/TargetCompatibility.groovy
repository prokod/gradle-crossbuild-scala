package com.github.prokod.gradle.crossbuild.model

import javax.inject.Inject

/**
 * cross build plugin DSL representation for {@code targetCompatibility.strategy}
 *
 * Observable by {@link com.github.prokod.gradle.crossbuild.CrossBuildExtension} through
 * {@link BuildUpdateEventStore}
 */
class TargetCompatibility {
    final String name
    final BuildUpdateEventStore eventStore
    String strategy

    static TargetCompatibility from(TargetCompatibilitySnapshot snapshot) {
        new TargetCompatibility(snapshot.name, snapshot.strategy, null)
    }

    @Inject
    TargetCompatibility(String name, String strategy, BuildUpdateEventStore eventStore) {
        this.name = name
        this.strategy = strategy
        this.eventStore = eventStore
    }

    /**
     * Triggered when a DSL of the following form appears:
     * <pre>
     *     crossBuild {
     *         builds {
     *             v213 {
     *                 targetCompatibility.strategy = ...
     *             }
     *         }
     *     }
     * </pre>
     *
     * @param appendixPattern
     */
    void setStrategy(String strategy) {
        def oldValue = this.strategy
        this.strategy = strategy
        def newValue = strategy

        if (oldValue != newValue) {
            eventStore?.store(new ArchiveNamingUpdateEvent(this))
        }
    }
}
