package com.github.prokod.gradle.crossbuild.model

import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.gradle.api.artifacts.Dependency

/**
 * Representing a dependency result object from some of the API methods in
 * {@link com.github.prokod.gradle.crossbuild.utils.DependencyInsights}
 *
 * todo incorporate in DependencyInsights
 */
@EqualsAndHashCode(excludes = 'dep')
@TupleConstructor
class DependencyInsight {
    String groupAndBaseName
    String supposedScalaVersion
    String version
    Dependency dep
}
