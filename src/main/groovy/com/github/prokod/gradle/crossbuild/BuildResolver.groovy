package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.model.Build
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildConfigLifecycle
import com.github.prokod.gradle.crossbuild.model.ResolvedArchiveNaming
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import com.github.prokod.gradle.crossbuild.utils.CrossBuildPluginUtils

/**
 * Utility class for resolving a {@link Build} -> ResolvedBuild
 */
class BuildResolver {
    /**
     * Resolve build suitable for Gradle config lifecycle.
     * Possibly one {@link Build} -> many {@link ResolvedBuildConfigLifecycle}
     *
     * @param build Input build as was created from plugin DSL
     * @param scalaVersions Scala versions catalog
     * @return A set of resolved builds
     */
    static Set<ResolvedBuildConfigLifecycle> resolve(Build build, ScalaVersions scalaVersions) {
        build.scalaVersions.collect { new ScalaVersionInsights(it, scalaVersions) }.collect { svi ->
            new ResolvedBuildConfigLifecycle(build, svi)
        }.toSet()
    }

    /**
     * resolve build suitable for Gradle afterEvaluate lifecycle
     * {@link ResolvedBuildConfigLifecycle} -> {@link ResolvedBuildAfterEvalLifeCycle}
     *
     * @param build
     * @return
     */
    static ResolvedBuildAfterEvalLifeCycle resolve(ResolvedBuildConfigLifecycle build) {
        def resolvedAppendix = resolveAppendix(build)
        def resolvedArchiveNaming = new ResolvedArchiveNaming(build.delegate.archive.appendixPattern, resolvedAppendix)

        new ResolvedBuildAfterEvalLifeCycle(build, resolvedArchiveNaming)
    }

    private static String resolveAppendix(ResolvedBuildConfigLifecycle build) {
        generateCrossArchivesNameAppndix(build.delegate.archive.appendixPattern,
                build.scalaVersionInsights.artifactInlinedVersion)
    }

    /**
     * Generates archives base name based on 'archivesBaseName', archiveAppendix which might include '?' placeholder and
     *  'artifactInlinedVersion' which will be used to fill '?' placeholder.
     *
     * @param archivesBaseName Name of archive prefixing '_' For example in lib... => 'lib'
     * @param archiveAppendix For example in lib_? => '_?'
     * @param artifactInlinedVersion Scala convention inlined version For example '2.11'
     * @return Interpreted archivesBaseName
     */
    private static String generateCrossArchivesNameAppndix(String archiveAppendix,
                                                           String artifactInlinedVersion) {
        CrossBuildPluginUtils.qmarkReplace(archiveAppendix, artifactInlinedVersion)
    }
}
