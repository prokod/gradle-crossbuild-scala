package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
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

    final String scalaVersion

    final ScalaVersionInsights scalaVersionInsights

    ResolvedBuildConfigLifecycle(Build build, ScalaVersionInsights scalaVersionInsights) {
        this.delegate = build
        this.name = createUserFriendlyUniqueName(build, scalaVersionInsights)
        this.scalaVersion = scalaVersionInsights.artifactInlinedVersion
        this.scalaVersionInsights = scalaVersionInsights
    }

    ResolvedBuildConfigLifecycle(ResolvedBuildConfigLifecycle other) {
        this.delegate = other.delegate
        this.name = other.name
        this.scalaVersion = other.scalaVersion
        this.scalaVersionInsights = other.scalaVersionInsights
    }

    private static String createUserFriendlyUniqueName(Build build, ScalaVersionInsights scalaVersionInsights) {
        def project = build.extension.project
        // Only in the following conditions do not add postfix to the build name
        if (build.name.endsWith(scalaVersionInsights.strippedArtifactInlinedVersion)) {
            if (build.scalaVersions.size() == 1) {
                def resolvedBuildName = build.name.capitalize()
                project.logger.debug(LoggerUtils.logTemplate(project,
                        lifecycle:'config',
                        msg:"Resolved build name for DSL build: ${build.name} and scala version: " +
                                "${scalaVersionInsights.artifactInlinedVersion} is: ${resolvedBuildName} (Short hand)"
                ))
                return resolvedBuildName
            }
        }
        // Otherwise
        def resolvedBuildName = createPostfixedName(build, scalaVersionInsights)
        project.logger.debug(LoggerUtils.logTemplate(project,
                lifecycle:'config',
                msg:"Resolved build name for DSL build: ${build.name} and scala version: " +
                        "${scalaVersionInsights.artifactInlinedVersion} is: ${resolvedBuildName} (Expanded)"
        ))
        resolvedBuildName
    }

    private static String createPostfixedName(Build build, ScalaVersionInsights scalaVersionInsights) {
        build.name.capitalize() + '_' + scalaVersionInsights.strippedArtifactInlinedVersion
    }

    String toString() {
        JsonOutput.toJson([name:name,
                           scalaVersion:scalaVersion,
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
