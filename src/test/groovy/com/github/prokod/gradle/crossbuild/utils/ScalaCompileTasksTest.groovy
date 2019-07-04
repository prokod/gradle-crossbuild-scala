package com.github.prokod.gradle.crossbuild.utils

import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import spock.lang.Specification

class ScalaCompileTasksTest extends Specification {
    def "When classpathFilterPredicate is given a candidate dependency file that should not be removed, it should return true"() {
        given:
            def file = new File('~/.gradle/caches/modules-2/files-2.1/com.jumpshot.spark/preprocessing-common-v2-2.2.1_2.11/1.4.0/cdd596565f41fd45834d72bfb7402f979b742edf/preprocessing-common-v2-2.2.1_2.11-1.4.0.jar')
        when:
            def result = ScalaCompileTasks.classpathFilterPredicate(file,
                    ['middomain-shuffle-common', 'preprocessing-common'],
                    [new DefaultExternalModuleDependency('some.group', 'preprocessing-common-v2-2.2.1_2.11', '1.4.0')] as Set)
        then:
            result
    }

    def "When classpathFilterPredicate is given a candidate dependency file that should be removed, it should return false"() {
        given:
            def file = new File('~/.gradle/caches/modules-2/files-2.1/com.jumpshot.spark/preprocessing-common-v2-2.2.1_2.11/1.4.0/cdd596565f41fd45834d72bfb7402f979b742edf/preprocessing-common-v2-2.2.1_2.11-1.4.0.jar')
        when:
            def result = ScalaCompileTasks.classpathFilterPredicate(file, ['middomain-shuffle-common', 'preprocessing-common'], [] as Set)
        then:
            !result
    }
}
