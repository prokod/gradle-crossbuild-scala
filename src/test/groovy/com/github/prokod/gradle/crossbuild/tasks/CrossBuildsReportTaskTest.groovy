package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.CrossBuildGradleRunnerSpec
import org.gradle.testkit.runner.GradleRunner
import org.skyscreamer.jsonassert.JSONAssert
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildsReportTaskTest extends CrossBuildGradleRunnerSpec {

    File buildFile

    def setup() {
        buildFile = file('build.gradle')
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with custom scala version values for each specified build item should produce expected set of crossbuild items when calling `crossBuildResolvedDsl`"() {
        given:
        buildFile << """

plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

crossBuild {
    scalaVersionsCatalog = ['2.13':'2.13.0']
    builds {
        v210
        v211 {
            scalaVersions = ['2.11', '2.12']
        }
        v211_A {
            scalaVersions = ['2.11']
            archive.appendixPattern = "_?_A"
        }
        v212 {
            scalaVersions = ['2.13']
        }
        v213 {
            scalaVersions = ['2.13']
        }
        spark24 {
            scalaVersions = ['2.11', '2.12']
            archive.appendixPattern = "-2-4-3_?"
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments('crossBuildResolvedDsl')
                .build()

        then:
        result.task(":crossBuildResolvedDsl").outcome == SUCCESS

        when:
            // Gradle 4 'java' plugin Configuration model is less precise and so firstLevelModuleDependencies are under
            // 'default' configuration, Gradle 5 already has a more precise model and so 'default' configuration is replaced
            // by either 'runtime' or 'compile' see https://gradle.org/whats-new/gradle-5/#fine-grained-transitive-dependency-management
            def expectedJsonAsText = loadResourceAsText('/builds_report.json')
            def appBuildsReportFile = findFile('*/*_builds.json')

        then:
        appBuildsReportFile != null
        def actualJsonAsText = appBuildsReportFile.text
        JSONAssert.assertEquals(expectedJsonAsText, actualJsonAsText, false)

        where:
        gradleVersion << ['4.10.3', '5.6.4', '6.0.1']
    }
}
