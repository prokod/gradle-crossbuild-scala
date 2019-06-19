package com.github.prokod.gradle.crossbuild.model

/**
 * Event being fired when `onScalaVersionsUpdate` is triggered
 */
class ScalaVersionsUpdateEvent {
    final String name
    final Build source

    ScalaVersionsUpdateEvent(Build source) {
        this.name = source.name
        this.source = new Build(source)
    }
}
