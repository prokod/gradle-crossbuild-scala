package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.ScalaVersions
import groovy.json.JsonOutput
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection

/**
 * cross build plugin DSL representation for individual build items in {@code builds} block
 */
class Build {
    final String name

    // Used as a listener impl. for `onScalaVersion` events
    final DomainObjectCollection<NamedVersion> scalaVersionListener

    ArchiveNaming archive

    String scalaVersion

    Build(String name, DomainObjectCollection<NamedVersion> scalaVersionListener) {
        this.name = name
        this.scalaVersionListener = scalaVersionListener
        trySettingScalaVersionImplicitlyFrom(name)
    }

    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Action<? super ArchiveNaming> action) {
        action.execute(archive)
    }

    /**
     * Needed even though it should be auto generated according to Gradle documentation
     * Probably it is not working per documentation because this DSL is nested.
     *
     * @param c
     */
    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Closure c) {
        org.gradle.util.ConfigureUtil.configure(c, archive)
    }

    private void trySettingScalaVersionImplicitlyFrom(String name) {
        def versionInsights =
                ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog.collect { new ScalaVersionInsights(it.value) }
                        .find { vi -> name.toLowerCase().contains("${vi.strippedArtifactInlinedVersion}") }
        if (versionInsights != null) {
            setScalaVersion(versionInsights.artifactInlinedVersion)
        }
    }

    /**
     * Fire an onScalaVersion event
     * Firing event is done only if collection backed listener {@code isEmpty()}
     *
     * @param scalaVersion Scala version value passed to onScalaVersion callback
     */
    private void fireEvent(String scalaVersion) {
        def nv = new NamedVersion(scalaVersion)
        if (!this.scalaVersionListener.isEmpty() && !this.scalaVersionListener.contains(nv)) {
            throw new IllegalArgumentException('Conflicting implicit/explicit scala versions for build: ' +
                    "${scalaVersionListener.head().name}/${nv.name}")
        }
        else if (this.scalaVersionListener.isEmpty()) {
            this.scalaVersionListener.add(new NamedVersion(scalaVersion))
        }
    }

    /**
     * onScalaVersion handler
     *
     * @param callback Closure as onScalaVersion callback
     */
    void onScalaVersion(Closure callback) {
        this.scalaVersionListener.all(callback)
    }

    void setScalaVersion(String scalaVersion) {
        this.scalaVersion = scalaVersion
        fireEvent(scalaVersion)
    }

    String toString() {
        JsonOutput.toJson([name:name,
                           scalaVersion:scalaVersion,
                           archive:[appendixPattern:archive.appendixPattern]])
    }
}
