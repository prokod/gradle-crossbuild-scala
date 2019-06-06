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
     * resolve build suitable for Gradle config lifecycle {@link Build} -> {@link ResolvedBuildConfigLifecycle}
     *
     * @param build
     * @param catalog
     * @return
     */
    static ResolvedBuildConfigLifecycle resolve(Build build, ScalaVersions scalaVersions) {
        def scalaVersionInsights = new ScalaVersionInsights(build.scala, scalaVersions)

        new ResolvedBuildConfigLifecycle(build, scalaVersionInsights)
    }

    /**
     * resolve build suitable for Gradle afterEvaluate lifecycle
     * {@link ResolvedBuildConfigLifecycle} -> {@link ResolvedBuildAfterEvalLifeCycle}
     *
     * @param build
     * @param catalog
     * @return
     */
    static ResolvedBuildAfterEvalLifeCycle resolve(ResolvedBuildConfigLifecycle build, ScalaVersions scalaVersions) {
        def scalaVersionInsights = new ScalaVersionInsights(build.scala, scalaVersions)

        def resolvedAppendix = resolveAppendix(build, scalaVersionInsights)
        def resolvedArchiveNaming = new ResolvedArchiveNaming(build.delegate.archive.appendixPattern, resolvedAppendix)

        new ResolvedBuildAfterEvalLifeCycle(build.delegate, scalaVersionInsights, resolvedArchiveNaming)
    }

    private static String resolveAppendix(ResolvedBuildConfigLifecycle build,
                                          ScalaVersionInsights scalaVersionInsights) {
        generateCrossArchivesNameAppndix(build.delegate.archive.appendixPattern,
                scalaVersionInsights.artifactInlinedVersion)
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
