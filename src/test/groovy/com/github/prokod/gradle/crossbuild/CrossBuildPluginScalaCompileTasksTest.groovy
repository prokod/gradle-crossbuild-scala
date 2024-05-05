/*
 * Copyright 2020-2022 the original author or authors
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

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Requires
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginScalaCompileTasksTest extends CrossBuildGradleRunnerSpec {
    File  settingsFileKts
    File buildFileKts
    File libBuildFileKts
    File libScalaFile

    def setup() {
        settingsFileKts = file('settings.gradle.kts')
        buildFileKts = file('build.gradle.kts')
        libBuildFileKts = file('libraryA/build.gradle.kts')
        libScalaFile = file('libraryA/src/main/scala/com/github/prokod/it/example/Example.scala')
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Eager</li>
     *     <li>Gradle compatibility matrix: 7.x, 8.x</li>
     *     <li>Running tasks that triggers main sourceset configurations: Yes</li>
     *     <li>build.gradle scripting language: Kotlin</li>
     * </ul>
     * This test checks the following plugin behaviour:
     * <ul>
     *     <li>The following test ensures that the plugin handles compiling for latest scala 2.12/13 without failure
     *     when user adds -Xfatal-warnings flag to compiler/li>
     * </ul>
     *
     * @see <a href="https://github.com/prokod/gradle-crossbuild-scala/issues/140">issue #140</a>
     */
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-lib:#defaultScalaLibModuleName] applying crossbuild plugin on a project with cross building of scala 2.x and 3.x should fail on compilation of cross build for scala 3"() {
        given:

        settingsFileKts << """
rootProject.name = "gradle-scala-template"

include("libraryA")
"""
        buildFileKts << """
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.igormaznitsa:jcp:7.0.5")
    }
}

plugins {
    id("com.github.prokod.gradle-crossbuild")
}

allprojects {
    repositories {
        mavenCentral()
        jcenter()
    }

    configurations {
        create("scalaCompilerPlugin") {
            setTransitive(false)
        }
    }
}

subprojects {
    group = "com.github.prokod.it"
    version = "1.0-SNAPSHOT"

    apply(plugin = "scala")
    apply(plugin = "com.github.prokod.gradle-crossbuild")
//    apply(plugin = "com.github.maiflai.scalatest")

    crossBuild {
        scalaVersionsCatalog = mapOf(
                "2.12" to "2.12.17",
                "2.13" to "2.13.12"
        )

        builds {
            create("v212")
            create("v213")
        }
    }

    dependencies {
        implementation("org.scala-lang:scala-library:$defaultScalaLibVersion")
//        testImplementation("org.scalatest:scalatest_2.12:3.2.18")
//        "scalaCompilerPlugin" ("com.olegpy:better-monadic-for_2.12:0.3.1")
//        testImplementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    }

    val copyPlugins by tasks.registering(Copy::class) {
        from(configurations["scalaCompilerPlugin"])
        into("\$buildDir/scalac-plugins")
    }

    tasks.withType<ScalaCompile>().configureEach {
//        val plugins = File("\$buildDir/scalac-plugins").listFiles()?.let {
//            "-Xplugin:" + it.joinToString(",")
//        }
        
        when (name) {
            "compileScala" -> scalaCompileOptions.additionalParameters =
                    listOf("-release:8", "-feature", "-Xfatal-warnings")
            else -> scalaCompileOptions.additionalParameters =
                    listOf("-feature", "-Xfatal-warnings") //+ listOfNotNull(plugins)
        }
    }
}
"""

        libBuildFileKts << """
"""


        libScalaFile << """
package com.github.prokod.it.example

class Example {
  def add(lhs: Int, rhs: Int): Int = lhs + rhs
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('tasks', 'build', 'crossBuildAssemble', 'crossBuildResolvedConfigs', '--stacktrace')
                .build()

        then:
        result.task(":tasks").outcome == SUCCESS
        result.task(":libraryA:build").outcome == SUCCESS
        result.task(":libraryA:crossBuildAssemble").outcome == SUCCESS

        fileExists(dir.resolve('libraryA/build/libs/libraryA_2.12-*.jar'))
        fileExists(dir.resolve('libraryA/build/libs/libraryA_2.13-*.jar'))

        where:
        gradleVersion   | defaultScalaLibModuleName | defaultScalaLibVersion
        '7.6.4'         | 'scala-library'          | '2.12.17'
        '8.7'           | 'scala-library'          | '2.13.12'
    }
}
