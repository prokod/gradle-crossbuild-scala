package com.github.prokod.gradle.crossbuild.model

/**
 * Event to create when {@link Build} scalaVersions is set/updated.
 *
 * Used to communicate change in observable {@link Build} to observer
 * {@link com.github.prokod.gradle.crossbuild.CrossBuildExtension}
 */
class BuildUpdateEvent {
    final String name
    final BuildSnapshot source
    final EventType eventType

    BuildUpdateEvent(Build source, EventType eventType) {
        this.name = source.name + '_' + eventType.toString()
        this.source = BuildSnapshot.from(source)
        this.eventType = eventType
    }
}
