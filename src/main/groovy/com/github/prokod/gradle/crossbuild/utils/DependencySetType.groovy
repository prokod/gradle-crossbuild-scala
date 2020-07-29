package com.github.prokod.gradle.crossbuild.utils

/**
 * ALL is intended for {@link org.gradle.api.artifacts.Configuration#getAllDependencies()}
 * SINGLE is intended for {@link org.gradle.api.artifacts.Configuration#getDependencies()}
 */
enum DependencySetType {
    ALL, SINGLE
}