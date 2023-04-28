package com.github.prokod.gradle.crossbuild.model

import groovy.json.JsonOutput

/**
 * Immutable representation of build item inside {@code builds} DSL block after resolving has been made
 *
 * @see com.github.prokod.gradle.crossbuild.BuildResolver
 */
class ResolvedBuildAfterEvalLifeCycle extends ResolvedBuildConfigLifecycle {

    final ResolvedArchiveNaming archive

    final ResolvedTargetCompatibility targetCompatibility

    ResolvedBuildAfterEvalLifeCycle(ResolvedBuildConfigLifecycle resolvedBuild,
                                    ResolvedArchiveNaming archive,
                                    ResolvedTargetCompatibility targetCompatibility) {
        super(resolvedBuild)
        this.archive = archive
        this.targetCompatibility = targetCompatibility
    }

    String toString() {
//        new JsonBuilder(archive).toPrettyString()

        JsonOutput.prettyPrint(JsonOutput.toJson([name:name,
                           scalaVersion:scalaVersion,
                           archive:[appendixPattern:archive.appendixPattern,
                                     appendix:archive.appendix],
                           targetCompatibility:[strategy:targetCompatibility.strategy],
                           scalaVersionInsights:[baseVersion:scalaVersionInsights.baseVersion,
                                                  compilerVersion:scalaVersionInsights.compilerVersion,
                                                  artifactInlinedVersion:scalaVersionInsights.artifactInlinedVersion,
                                                  strippedArtifactInlinedVersion:scalaVersionInsights.
                                                          strippedArtifactInlinedVersion,
                                                  underscoredBaseVersion:scalaVersionInsights.underscoredBaseVersion,
                                                  underscoredCompilerVersion:scalaVersionInsights.
                                                          underscoredCompilerVersion,
                                                  underscoredArtifactInlinedVersion:scalaVersionInsights.
                                                          underscoredArtifactInlinedVersion]]))
    }
}
