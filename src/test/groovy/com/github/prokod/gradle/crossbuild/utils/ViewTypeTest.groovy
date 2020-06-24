package com.github.prokod.gradle.crossbuild.utils

import spock.lang.Specification

class ViewTypeTest extends Specification {

    def "filterViwBy should filter based on given tag predicates correctly"() {
        when:
            def views = ViewType.filterViewsBy({ tags -> tags.contains('canBeConsumed') }, { tags -> !tags.contains('test') })
        then :
            views.toSet() == [ViewType.COMPILE,
                    ViewType.COMPILE_ONLY,
                    ViewType.IMPLEMENTATION,
                    ViewType.RUNTIME,
                    ViewType.RUNTIME_ONLY].toSet()
    }

    def "filterViwBy should filter only test related ViewTypes based on given tag predicate"() {
        when:
        def views = ViewType.filterViewsBy({ tags -> tags.contains('test') })
        then :
        views.toSet() == [ViewType.TEST_COMPILE,
                ViewType.TEST_COMPILE_CLASSPATH,
                ViewType.TEST_COMPILE_ONLY,
                ViewType.TEST_IMPLEMENTATION,
                ViewType.TEST_RUNTIME,
                ViewType.TEST_RUNTIME_CLASSPATH,
                ViewType.TEST_RUNTIME_ONLY].toSet()
    }
}
