package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.DependencyModuleType
import com.github.prokod.gradle.crossbuild.ScalaVersions
import groovy.transform.EqualsAndHashCode
import org.gradle.api.artifacts.Dependency

/**
 * Represents a dependency result object returned from some of the API methods in
 * {@link com.github.prokod.gradle.crossbuild.utils.DependencyOps}
 *
 */
@EqualsAndHashCode(excludes = ['dependency'], callSuper = true)
class DependencyInsight extends DependencyLimitedInsight {
    String group
    String version
    DependencyModuleType  moduleType
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
                moduleType:inferModuleType(dependency, limitedInsight),
                dependency:dependency)
    }

    String getGroupAndBaseName() {
        "$group:$baseName".toString()
    }

    /**
     * To tag {@link DependencyInsight} with a correct DependencyModuleType the following logic is followed:
     * Check if it is {@link DependencyModuleType#SCALA_LIBRARY} or {@link DependencyModuleType#SCALA_COMPILER}
     * If it so return, otherwise
     * Check if this is {@link DependencyModuleType#SCALA_3RD_PARTY_LIB} otherwise
     * {@link DependencyModuleType#NON_SCALA_3RD_PARTY_LIB}
     * @param dependency
     * @param limitedInsight
     * @return DependencyModuleType
     */
    private static DependencyModuleType inferModuleType(Dependency dependency,
                                                        DependencyLimitedInsight limitedInsight) {
        def moduleType = dependency.group == 'org.scala-lang' ?
                DependencyModuleType.findByName(dependency.name) : DependencyModuleType.UNKNOWN
        if (DependencyModuleType.UNKNOWN == moduleType) {
            if (limitedInsight.supposedScalaVersion) {
                moduleType = DependencyModuleType.SCALA_3RD_PARTY_LIB
            }
            else {
                moduleType = DependencyModuleType.NON_SCALA_3RD_PARTY_LIB
            }
        }
        moduleType
    }
}
