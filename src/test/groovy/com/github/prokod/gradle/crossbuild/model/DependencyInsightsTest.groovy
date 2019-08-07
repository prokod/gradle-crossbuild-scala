package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.ScalaVersions
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.internal.Describables
import spock.lang.Specification

class DependencyInsightsTest extends Specification {
    def "DependencyInsight equality"() {
        given:
            def dep1 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.3')
            def dep2 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11_appendix', '1.2.3')
            def dep3 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.4')
        when:
            def dependencyInsight1 = DependencyInsight.parse(dep1, ScalaVersions.DEFAULT_SCALA_VERSIONS)
            def dependencyInsight2 = DependencyInsight.parse(dep2, ScalaVersions.DEFAULT_SCALA_VERSIONS)
            def dependencyInsight3 = DependencyInsight.parse(dep3, ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            dependencyInsight1 == dependencyInsight2
            dependencyInsight1 != dependencyInsight3
            dependencyInsight2 != dependencyInsight3
    }

    def "When parseDependencyName is given a simple scala lib dependency name it should return correct limited insight"() {
        when:
            def dependencyLimitedInsight = DependencyLimitedInsight.parseByDependencyName('somescalalib_2.11', ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            dependencyLimitedInsight.baseName == 'somescalalib'
            dependencyLimitedInsight.supposedScalaVersion == '2.11'
    }

    def "When parseDependencyName is given a scala lib dependency name with '_suffix' it should return correct limited insight"() {
        when:
            def dependencyLimitedInsight = DependencyLimitedInsight.parseByDependencyName('somescalalib_2.11_suffix', ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            dependencyLimitedInsight.baseName == 'somescalalib'
            dependencyLimitedInsight.supposedScalaVersion == '2.11'
            dependencyLimitedInsight.appendix == '_suffix'
    }

    def "When parseDependencyName is given a simple scala lib dependency it should return correctly"() {
        given:
            def dep = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.3')
        when:
            def dependencyInsight = DependencyInsight.parse(dep, ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            dependencyInsight.groupAndBaseName == 'some.group:somescalalib'
            dependencyInsight.supposedScalaVersion == '2.11'
    }

    def "When parseDependencyName is given a scala lib dependency with '_suffix' it should return correctly"() {
        given:
            def dep = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11_suffix', '1.2.3')
        when:
            def dependencyInsight = DependencyInsight.parse(dep, ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            dependencyInsight.groupAndBaseName == 'some.group:somescalalib'
            dependencyInsight.supposedScalaVersion == '2.11'
            dependencyInsight.appendix == '_suffix'
    }

    def "When parseDependencyName is given an unconventional scala lib dependency with `_version` as suffix it should return correctly"() {
        given:
            def dep = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11_2.2.1', '1.2.3')
        when:
            def dependencyInsight = DependencyInsight.parse(dep, ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            dependencyInsight.groupAndBaseName == 'some.group:somescalalib'
            dependencyInsight.supposedScalaVersion == '2.11'
            dependencyInsight.appendix == '_2.2.1'
    }

    def "When parseDependencyName given an unconventional scala lib dependency with `-version` before scala version it should return correctly"() {
        given:
            def dep = new DefaultExternalModuleDependency('some.group', 'somescalalib-v2-2.2.1_2.11', '1.2.3')
        when:
            def dependencyInsight = DependencyInsight.parse(dep, ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            dependencyInsight.groupAndBaseName == 'some.group:somescalalib-v2-2.2.1'
            dependencyInsight.supposedScalaVersion == '2.11'
    }

    def "When findAllNonMatchingScalaVersionDependencies given a dependency set it should return correctly"() {
        given:
            def dep1 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.3')
            def dep2 = new DefaultExternalModuleDependency('some.group', 'someotherscalalib_?', '1.2.4')
            def dep3 = new DefaultExternalModuleDependency('some.group', 'yasomecalalib_?_suffix', '1.2.5')
            def dep4 = new DefaultExternalModuleDependency('some.group', 'yasomecalalib_2.10', '1.2.5')

        def dependencySet = new DefaultDependencySet(
                    Describables.of('someDisplayName'),
                    null,
                    new DefaultDomainObjectSet(Dependency, Arrays.asList(dep1, dep2, dep3, dep4)))
        when:
            def tupleList = DependencyInsights.findAllNonMatchingScalaVersionDependencies(dependencySet.collect(), '2.10', ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            tupleList.size() == 3
            tupleList[0].groupAndBaseName == 'some.group:somescalalib'
            tupleList[0].supposedScalaVersion == '2.11'
            tupleList[0].dependency == dep1
            tupleList[1].groupAndBaseName== 'some.group:someotherscalalib'
            tupleList[1].supposedScalaVersion == '?'
            tupleList[1].dependency == dep2
            tupleList[2].groupAndBaseName == 'some.group:yasomecalalib'
            tupleList[2].supposedScalaVersion == '?'
            tupleList[2].dependency == dep3
    }

    def "When findAllNonMatchingScalaVersionDependenciesWithCounterparts given a dependency set it should return correctly"() {
        given:
            def dep1 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.3')
            def dep2 = new DefaultExternalModuleDependency('some.group', 'somescalalib_?', '1.2.4')
            def dep3 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.10', '1.2.5')
            def dep31 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.10', '1.2.6')
            def dep4 = new DefaultExternalModuleDependency('some.group', 'someotherscalalib_2.11', '1.2.3')
            def dep5 = new DefaultExternalModuleDependency('some.group', 'yasomescalalib_?', '1.2.4')
            def dep51 = new DefaultExternalModuleDependency('some.group', 'yasomescalalib_?_suffix', '1.2.4')
            def dep6 = new DefaultExternalModuleDependency('some.group', 'nonscalalib', '1.2.5')
            def dependencySet = new DefaultDependencySet(
                    Describables.of('someDisplayName'),
                    null,
                    new DefaultDomainObjectSet(Dependency, Arrays.asList(dep1, dep2, dep3, dep31, dep4, dep5, dep51, dep6)))
        when:
            def tuplesList = DependencyInsights.findAllNonMatchingScalaVersionDependenciesWithCounterparts(dependencySet.collect(), '2.10', ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            tuplesList.size() == 5
            tuplesList[0].first.groupAndBaseName == 'some.group:somescalalib'
            tuplesList[0].first.supposedScalaVersion == '2.11'
            tuplesList[0].first.dependency == dep1
            tuplesList[0].second.size() == 2
            tuplesList[0].second[0].groupAndBaseName == 'some.group:somescalalib'
            tuplesList[0].second[0].supposedScalaVersion == '2.10'
            tuplesList[0].second[0].dependency == dep3
            tuplesList[0].second[1].groupAndBaseName == 'some.group:somescalalib'
            tuplesList[0].second[1].supposedScalaVersion == '2.10'
            tuplesList[0].second[1].dependency == dep31
            tuplesList[1].first.groupAndBaseName == 'some.group:somescalalib'
            tuplesList[1].first.supposedScalaVersion == '?'
            tuplesList[1].first.dependency == dep2
            tuplesList[1].second.size() == 2
            tuplesList[1].second[0].groupAndBaseName == 'some.group:somescalalib'
            tuplesList[1].second[0].supposedScalaVersion == '2.10'
            tuplesList[1].second[0].dependency == dep3
            tuplesList[1].second[1].groupAndBaseName == 'some.group:somescalalib'
            tuplesList[1].second[1].supposedScalaVersion == '2.10'
            tuplesList[1].second[1].dependency == dep31
            tuplesList[2].first.groupAndBaseName == 'some.group:someotherscalalib'
            tuplesList[2].first.supposedScalaVersion == '2.11'
            tuplesList[2].first.dependency == dep4
            tuplesList[2].second.size() == 0
            tuplesList[3].first.groupAndBaseName == 'some.group:yasomescalalib'
            tuplesList[3].first.supposedScalaVersion == '?'
            tuplesList[3].first.dependency == dep5
            tuplesList[3].second.size() == 0
            tuplesList[4].first.groupAndBaseName == 'some.group:yasomescalalib'
            tuplesList[4].first.supposedScalaVersion == '?'
            tuplesList[4].first.dependency == dep51
            tuplesList[4].second.size() == 0
    }
}
