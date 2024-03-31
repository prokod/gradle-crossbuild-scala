package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.ScalaVersions
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DependencyInsightTest extends Specification {
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

    def "DependencyInsight equality for scala module type dependencies"() {
        given:
        def dep1 = new DefaultExternalModuleDependency('org.scala-lang', 'scala-library', '2.13.10')
        def dep2 = new DefaultExternalModuleDependency('org.scala-lang', 'scala3-library_3', '3.3.1')
        def dep3 = new DefaultExternalModuleDependency('org.scala-lang', 'scala-compiler', '2.13.10')
        def dep4 = new DefaultExternalModuleDependency('org.scala-lang', 'scala3-compiler_3', '3.3.1')
        when:
        def dependencyInsight1 = DependencyInsight.parse(dep1, ScalaVersions.DEFAULT_SCALA_VERSIONS)
        def dependencyInsight2 = DependencyInsight.parse(dep2, ScalaVersions.DEFAULT_SCALA_VERSIONS)
        def dependencyInsight3 = DependencyInsight.parse(dep3, ScalaVersions.DEFAULT_SCALA_VERSIONS)
        def dependencyInsight4 = DependencyInsight.parse(dep4, ScalaVersions.DEFAULT_SCALA_VERSIONS)
        then:
        dependencyInsight1 != dependencyInsight2
        dependencyInsight3 != dependencyInsight4
        dependencyInsight1 != dependencyInsight3
        dependencyInsight2 != dependencyInsight4
        dependencyInsight1.moduleType == dependencyInsight2.moduleType
        dependencyInsight3.moduleType == dependencyInsight4.moduleType
        dependencyInsight1.moduleType != dependencyInsight3.moduleType
    }

    def "When adding equal Dependencies to DependencySet only one such Dependency should be present in the DependencySet"() {
        given:
        def project = ProjectBuilder.builder().build()
        // Before instantiating CrossBuildExtension, project should contain sourceSets otherwise, CrossBuildSourceSets
        // instantiation will fail.
        project.pluginManager.apply(JavaPlugin)

        ObjectFactory objects = project.services.get(ObjectFactory)
        def set = objects.domainObjectSet(Dependency)

        when:
        def dep1 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.3')
        def dep2 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11_appendix', '1.2.3')
        def dep3 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.4')
        def dep4 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.4')
        def dep5 = new DefaultExternalModuleDependency('some.group', 'somescalalib_2.11', '1.2.4', 'someconfig')

        and:
        set.addAll([dep1, dep2, dep3, dep4, dep5])

        then:
        set.size() == 4
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
}
