package com.github.prokod.gradle.crossbuild.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet

/**
 * Used within {@link DependencyInsights}
 * dependencies - A set of dependencies in the form of {@link org.gradle.api.artifacts.DependencySet} to scan.
 *                Usually a {@link DependencySet} originated from a specified configuration
 * configuration - A configuration to have the dependencies extracted from.
 * project - Gradle project as a context (Usually to retrieve rootProject ({@link Project}) from).
 *
 * todo make sourceSet as a member and through convention (enum ?) get configurations, dependencies
 */
class DependencyInsightsContext {
    Set<DependencySet> dependencies
    Configurations configurations
    Project project

    static class Configurations {
        Configuration current
        Configuration parent
    }
}
