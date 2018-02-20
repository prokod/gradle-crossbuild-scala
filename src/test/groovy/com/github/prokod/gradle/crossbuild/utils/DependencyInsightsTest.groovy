package com.github.prokod.gradle.crossbuild.utils

import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import spock.lang.Specification

class DependencyInsightsTest extends Specification {
    def "When parseDependencyName given a simple scala lib dependency it should return correctly"() {
        given:
            def dep = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.3')
        when:
            def (baseName, scalaVersion) = DependencyInsights.parseDependencyName(dep)
        then:
            baseName == 'some.group:somescalalib'
            scalaVersion == '2.11'
    }

    def "When parseDependencyName given a complex scala lib dependency it should return correctly"() {
        given:
            def dep = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11_2.2.1', '1.2.3')
        when:
            def (baseName, scalaVersion) = DependencyInsights.parseDependencyName(dep)
        then:
            baseName == 'some.group:somescalalib'
            scalaVersion == '2.11'
    }

    def "When findAllNonMatchingScalaVersionDependencies given a dependency set it should return correctly"() {
        given:
            def dep1 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.3')
            def dep2 = new DefaultExternalModuleDependency('some.group', 'someotherscalalib_?', '1.2.4')
            def dep3 = new DefaultExternalModuleDependency('some.group', 'yasomecalalib_2.10', '1.2.5')
            def dependencySet = new DefaultDependencySet(
                    'someDisplayName',
                    new DefaultDomainObjectSet(Dependency, Arrays.asList(dep1, dep2, dep3)))
        when:
            def tupleList = DependencyInsights.findAllNonMatchingScalaVersionDependencies(dependencySet, '2.10')
        then:
            tupleList.size() == 2
            tupleList[0][0] == 'some.group:somescalalib'
            tupleList[0][1] == '2.11'
            tupleList[0][2] == dep1
            tupleList[1][0] == 'some.group:someotherscalalib'
            tupleList[1][1] == '?'
            tupleList[1][2] == dep2
    }

    def "When findAllNonMatchingScalaVersionDependenciesWithCounterparts given a dependency set it should return correctly"() {
        given:
            def dep1 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.3')
            def dep2 = new DefaultExternalModuleDependency('some.group', 'somescalalib_?', '1.2.4')
            def dep3 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.10', '1.2.5')
            def dep31 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.10', '1.2.6')
            def dep4 = new DefaultExternalModuleDependency('some.group', 'someotherscalalib_2.11', '1.2.3')
            def dep5 = new DefaultExternalModuleDependency('some.group', 'yasomescalalib_?', '1.2.4')
            def dep6 = new DefaultExternalModuleDependency('some.group', 'nonscalalib', '1.2.5')
            def dependencySet = new DefaultDependencySet(
                    'someDisplayName',
                    new DefaultDomainObjectSet(Dependency, Arrays.asList(dep1, dep2, dep3, dep31, dep4, dep5, dep6)))
        when:
            def tuplesList = DependencyInsights.findAllNonMatchingScalaVersionDependenciesWithCounterparts(dependencySet, '2.10')
        then:
            tuplesList.size() == 4
            tuplesList[0].first[0] == 'some.group:somescalalib'
            tuplesList[0].first[1] == '2.11'
            tuplesList[0].first[2] == dep1
            tuplesList[0].second.size() == 2
            tuplesList[0].second[0][0] == 'some.group:somescalalib'
            tuplesList[0].second[0][1] == '2.10'
            tuplesList[0].second[0][2] == dep3
            tuplesList[0].second[1][0] == 'some.group:somescalalib'
            tuplesList[0].second[1][1] == '2.10'
            tuplesList[0].second[1][2] == dep31
            tuplesList[1].first[0] == 'some.group:somescalalib'
            tuplesList[1].first[1] == '?'
            tuplesList[1].first[2] == dep2
            tuplesList[1].second.size() == 2
            tuplesList[1].second[0][0] == 'some.group:somescalalib'
            tuplesList[1].second[0][1] == '2.10'
            tuplesList[1].second[0][2] == dep3
            tuplesList[1].second[1][0] == 'some.group:somescalalib'
            tuplesList[1].second[1][1] == '2.10'
            tuplesList[1].second[1][2] == dep31
            tuplesList[2].first[0] == 'some.group:someotherscalalib'
            tuplesList[2].first[1] == '2.11'
            tuplesList[2].first[2] == dep4
            tuplesList[2].second.size() == 0
    }
}
