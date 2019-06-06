package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import groovy.json.JsonOutput

/**
 * Immutable representation of build item inside {@code builds} DSL block after resolving has been made in Gradle
 * config lifecycle.
 *
 * @see com.github.prokod.gradle.crossbuild.BuildResolver
 */
class ResolvedBuildConfigLifecycle {
    final Build delegate

    final String name

    // TODO: rename to version or scalaVersion
    final String scala

    final ScalaVersionInsights scalaVersionInsights

    ResolvedBuildConfigLifecycle(Build build, ScalaVersionInsights scalaVersionInsights) {
        this.delegate = build
        this.name = build.name
        this.scala = build.scala
        this.scalaVersionInsights = scalaVersionInsights
    }

    String toString() {
        JsonOutput.toJson([name:name,
                           scala:scala,
                           scalaVersionInsights:[baseVersion:scalaVersionInsights.baseVersion,
                                                  compilerVersion:scalaVersionInsights.compilerVersion,
                                                  artifactInlinedVersion:scalaVersionInsights.artifactInlinedVersion,
                                                  strippedArtifactInlinedVersion:scalaVersionInsights.
                                                          strippedArtifactInlinedVersion,
                                                  underscoredBaseVersion:scalaVersionInsights.underscoredBaseVersion,
                                                  underscoredCompilerVersion:scalaVersionInsights.
                                                          underscoredCompilerVersion,
                                                  underscoredArtifactInlinedVersion:scalaVersionInsights.
                                                          underscoredArtifactInlinedVersion],
                           archive:[appendixPattern:delegate.archive.appendixPattern]])
    }
}
