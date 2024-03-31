package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.ScalaVersions
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import spock.lang.Specification

class DependencyOpsTest extends Specification {
    def "When findAllNonMatchingScalaVersionDependencies given a dependency set it should return correctly"() {
        given:
            def dep1 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.3')
            def dep2 = new DefaultExternalModuleDependency('some.group', 'someotherscalalib_?', '1.2.4')
            def dep3 = new DefaultExternalModuleDependency('some.group', 'yasomecalalib_?_suffix', '1.2.5')
            def dep4 = new DefaultExternalModuleDependency('some.group', 'yasomecalalib_2.10', '1.2.5')

        def dependencySet = Arrays.asList(dep1, dep2, dep3, dep4)
        when:
            def tupleList = DependencyOps.findAllNonMatchingScalaVersionDependencies(dependencySet.collect(), '2.10', ScalaVersions.DEFAULT_SCALA_VERSIONS)
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
            def dependencySet = Arrays.asList(dep1, dep2, dep3, dep31, dep4, dep5, dep51, dep6)
        when:
            def tuplesList = DependencyOps.findAllNonMatchingScalaVersionDependenciesWithCounterparts(dependencySet.collect(), '2.10', ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
            tuplesList.size() == 5
            tuplesList[0].first.groupAndBaseName == 'some.group:somescalalib'
            tuplesList[0].first.supposedScalaVersion == '2.11'
            tuplesList[0].first.dependency == dep1
            def setFor211 = tuplesList[0].second.collectEntries{
                [it.version, it]
            }
            setFor211.size() == 2
            setFor211['1.2.5'].groupAndBaseName == 'some.group:somescalalib'
            setFor211['1.2.5'].supposedScalaVersion == '2.10'
            setFor211['1.2.5'].dependency == dep3
            setFor211['1.2.6'].groupAndBaseName == 'some.group:somescalalib'
            setFor211['1.2.6'].supposedScalaVersion == '2.10'
            setFor211['1.2.6'].dependency == dep31
            tuplesList[1].first.groupAndBaseName == 'some.group:somescalalib'
            tuplesList[1].first.supposedScalaVersion == '?'
            tuplesList[1].first.dependency == dep2
            def setForQMark = tuplesList[1].second.collectEntries{
                [it.version, it]
            }
            setForQMark.size() == 2
            setForQMark['1.2.5'].groupAndBaseName == 'some.group:somescalalib'
            setForQMark['1.2.5'].supposedScalaVersion == '2.10'
            setForQMark['1.2.5'].dependency == dep3
            setForQMark['1.2.6'].groupAndBaseName == 'some.group:somescalalib'
            setForQMark['1.2.6'].supposedScalaVersion == '2.10'
            setForQMark['1.2.6'].dependency == dep31
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
