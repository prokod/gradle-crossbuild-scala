package com.github.prokod.gradle.crossbuild.model

import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.gradle.api.artifacts.Dependency

@EqualsAndHashCode(excludes = 'dep')
@TupleConstructor
class DependencyInsight {
    String groupAndBaseName
    String supposedScalaVersion
    String version
    Dependency dep
}
