/*
 * Copyright 2016-2017 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.model.Build
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.internal.impldep.org.junit.Assume
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginExtensionTest extends CrossBuildGradleRunnerSpec {

    File settingsFile
    File propsFile
    File buildFile
    File libBuildFile
    File libScalaFile
    File libJavaFile
    File lib2BuildFile
    File lib2ScalaFile
    File lib2ScalaImplFile
    File appBuildFile
    File appScalaFile

    def setup() {
        settingsFile = file('settings.gradle')
        propsFile = file('gradle.properties')
        buildFile = file('build.gradle')
        libBuildFile = file('lib/build.gradle')
        libScalaFile = file('lib/src/main/scala/HelloWorldLibApi.scala')
        libJavaFile = file('lib/src/main/java/HelloWorldLibImpl.java')
        lib2BuildFile = file('lib2/build.gradle')
        lib2ScalaFile = file('lib2/src/main/scala/HelloWorldLib2Api.scala')
        lib2ScalaImplFile = file('lib2/src/main/scala/HelloWorldLib2Impl.scala')
        appBuildFile = file('app/build.gradle')
        appScalaFile = file('app/src/main/scala/HelloWorldApp.scala')
    }

    /**
     *
     * @param gradleVersion Gradle version i.e '4.2'
     * @param defaultScalaVersion i.e '2.12.8'
     * @param ap Default Appendix Pattern i.e '_?'
     * @param oap1 Override Appendix Pattern for cross build no. 1 i.e '-x-y-z_?'
     * @param oap2 Override Appendix Pattern for cross build no. 2
     * @param oap3 Override Appendix Pattern for cross build no. 3
     * @param eap1 Expected Appendix Pattern for cross build no. 1
     * @param eap2 Expected Appendix Pattern for cross build no. 2
     * @param eap3 Expected Appendix Pattern for cross build no. 3
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with `withPlugin` dsl should propagate the same plugin configuration to all sub projects"(
            String gradleVersion,
            String defaultScalaVersion,
            String ap,
            String oap1, String oap2, String oap3,
            String eap1, String eap2, String eap3
    ) {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
include 'lib2'
include 'app'
"""

        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

allprojects {
    apply plugin: 'java'
    group = 'com.github.prokod.it'
    version = '1.0-SNAPSHOT'
    
    repositories {
        mavenCentral()
    }
    
    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild') {
        crossBuild {
            
            scalaVersionsCatalog = ['2.11':'2.11.12']
        
            archive {
                appendixPattern = '${ap}'
            }
            builds {
                spark160 {
                    scalaVersions = ['2.10']
                    ${oap1 != null ? 'archive.appendixPattern = \'' + oap1 + '\'' : ''}
                }
                spark240 {
                    scalaVersions = ['2.11']
                    ${oap2 != null ? 'archive.appendixPattern = \'' + oap2 + '\'' : ''}
                }
                spark241 {
                    scalaVersions = ['2.12']
                        ${oap3 != null ? 'archive { appendixPattern = \'' + oap3 + '\' }' : ''}
                }
            }
        }
    }
}
"""

        libBuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'

"""

        lib2BuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'

dependencies {
    implementation project(':lib')
}
"""

        appBuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'

dependencies {
    implementation project(':lib2')
}
"""

        def project = ProjectBuilder.builder().build()
        // Before instantiating CrossBuildExtension, project should contain sourceSets otherwise, CrossBuildSourceSets
        // instantiation will fail.
        project.pluginManager.apply(JavaPlugin)

        ObjectFactory objects = project.services.get(ObjectFactory)
        def build1 = new Build('spark160', new CrossBuildExtension(project, objects)).with { b ->
            scalaVersions = ['2.10']
            archive.appendixPattern = eap1
            b
        }
        def build2 = new Build('spark240', new CrossBuildExtension(project, objects)).with { b ->
            scalaVersions = ['2.11']
            archive.appendixPattern = eap2
            b
        }
        def build3 = new Build('spark241', new CrossBuildExtension(project, objects)).with { b ->
            scalaVersions = ['2.12']
            archive.appendixPattern = eap3
            b
        }
        def rb1 = BuildResolver.resolve(build1, ScalaVersions.withDefaultsAsFallback('2.11': '2.11.12'))
        def rb2 = BuildResolver.resolve(build2, ScalaVersions.withDefaultsAsFallback('2.11': '2.11.12'))
        def rb3 = BuildResolver.resolve(build3, ScalaVersions.withDefaultsAsFallback('2.11': '2.11.12'))

        def expectedReport = '[' + (rb1 + rb2 + rb3).collect { it.toString() }.join(',\n') + ']'

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments('crossBuildResolvedDsl', '--info', '--stacktrace')
                .build()

        then:
        result.task(":crossBuildResolvedDsl").outcome == SUCCESS
        result.task(":lib:crossBuildResolvedDsl").outcome == SUCCESS
        result.task(":lib2:crossBuildResolvedDsl").outcome == SUCCESS
        result.task(":app:crossBuildResolvedDsl").outcome == SUCCESS

        when:
        def appBuildsReportFile = findFile("*/app_builds.json")

        then:
        appBuildsReportFile != null
        appBuildsReportFile.text == expectedReport

        where:
        gradleVersion | defaultScalaVersion | ap       | oap1       | oap2       | oap3       | eap1       | eap2       | eap3
        '5.6.4'       | '2.10'              | '_?'     | null       | null       | null       | '_?'       | '_?'       | '_?'
        '5.6.4'       | '2.11'              | '-def_?' | '-1-6-0_?' | '-2-4-0_?' | '-2-4-1_?' | '-1-6-0_?' | '-2-4-0_?' | '-2-4-1_?'
        '6.9.2'       | '2.12'              | '-def_?' | null       | null       | null       | '-def_?'   | '-def_?'   | '-def_?'
        '7.3.3'       | '2.11'              | '-def_?' | '-1-6-0_?' | '-2-4-0_?' | '-2-4-1_?' | '-1-6-0_?' | '-2-4-0_?' | '-2-4-1_?'
    }
}