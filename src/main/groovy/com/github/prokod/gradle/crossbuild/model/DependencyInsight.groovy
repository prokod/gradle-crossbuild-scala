package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.ScalaVersions
import groovy.transform.EqualsAndHashCode
import org.gradle.api.artifacts.Dependency

/**
 * Represents a dependency result object returned from some of the API methods in
 * {@link com.github.prokod.gradle.crossbuild.utils.DependencyInsights}
 *
 */
@EqualsAndHashCode(excludes = ['dependency'], callSuper = true)
class DependencyInsight extends DependencyLimitedInsight {
    String group
    String version
    Dependency dependency

    /**
     * Parses given dependency to its groupName:baseName part and its scala version part.
     *
     * @param dependency to parse
     * @return tuple in the form of (groupName:baseName, scalaVersion) i.e. ('group:lib', '2.11')
     */
    static DependencyInsight parse(Dependency dependency, ScalaVersions scalaVersions) {
        def limitedInsight = parseByDependencyName(dependency.name, scalaVersions)
        new DependencyInsight(baseName:limitedInsight.baseName,
                supposedScalaVersion:limitedInsight.supposedScalaVersion,
                appendix:limitedInsight.appendix,
                group:dependency.group,
                version:dependency.version,
                dependency:dependency)
    }

    String getGroupAndBaseName() {
        "$group:$baseName".toString()
    }
}
