package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.ScalaVersions
import groovy.json.JsonOutput
import org.gradle.api.Action

/**
 * cross build plugin DSL representation for individual build items in {@code builds} block
 */
class Build {
    final String name

    ArchiveNaming archive

    String scala

    Build(String name) {
        this.name = name
        this.scala = trySettingDefaultValue(name)
    }

    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Action<? super ArchiveNaming> action) {
        action.execute(archive)
    }

    /**
     * Needed even though it should be auto generated accordign to Gradle documentation
     * Probably it is not working per documentation because this DSL is nested.
     *
     * @param c
     */
    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Closure c) {
        org.gradle.util.ConfigureUtil.configure(c, archive)
    }

    private static String trySettingDefaultValue(String name) {
        ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog
                .collect { new ScalaVersionInsights(it.value) }
                .find { versionInsights ->
            name.toLowerCase().contains("${versionInsights.strippedArtifactInlinedVersion}") }?.artifactInlinedVersion
    }

    String toString() {
        JsonOutput.toJson([name:name,
                           scala:scala,
                           archive:[appendixPattern:archive.appendixPattern]])
    }
}
