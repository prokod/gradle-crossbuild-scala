package com.github.prokod.gradle.crossbuild.model

/**
 * Data object used as part of `onScalaVersion` listener
 */
class NamedVersion {
    final String name

    NamedVersion(String name) {
        this.name = name
    }
}
