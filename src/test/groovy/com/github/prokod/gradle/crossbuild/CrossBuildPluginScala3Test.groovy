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

class CrossBuildPluginScala3Test extends CrossBuildGradleRunnerSpec {
    File buildFileKts
    File buildFile
    File appScalaFile

    def setup() {
        buildFile = file('build.gradle')
        buildFileKts = file('build.gradle.kts')
        appScalaFile = file('src/main/scala/com/github/prokod/it/api/Api.scala')
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Eager</li>
     *     <li>Gradle compatibility matrix: 7.x, 8.x</li>
     *     <li>Running tasks that triggers main sourceset configurations: Yes</li>
     * </ul>
     * This test checks the following plugin behaviour:
     * <ul>
     *     <li>The following test ensures that the plugin handles compiling for booth scala 2.13 and 3 without scala
     *     compile errors.
     *     The plugin, before this fix, wrongly replaces scala-library dependency with
     *     scala3-library_3 as part of dependency resolution to keep all inherited libraries from main matching
     *     the cross build variant being compiled.
     *     The outcome of this being that scala-library 2.13.x, which is a dependency for scala3-library_3,
     *     is missing from classpath while compiling code for scala 3</li>
     * </ul>
     *
     * @see <a href="https://github.com/prokod/gradle-crossbuild-scala/issues/141">issue #141</a>
     */
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-lib:#defaultScalaLibModuleName] applying crossbuild plugin on a project with cross building of scala 2.x and 3.x should fail on compilation of cross build for scala 3"() {
        given:

        buildFile << """
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.igormaznitsa:jcp:7.0.5")
    }
}

plugins {
    id 'maven-publish'
    id 'com.github.prokod.gradle-crossbuild-scala'
}

group = 'com.github.prokod.it'
version = "1.0-SNAPSHOT"

crossBuild {
    scalaVersionsCatalog = ["2.10": "2.10.7", "2.11": "2.11.12", "2.12": "2.12.19", "2.13": "2.13.10", "3": "3.3.1"]

    builds {
        scala {
            scalaVersions = ["2.12", "2.13", "3"]
        }
    }
}

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation "org.scala-lang:$defaultScalaLibModuleName:$defaultScalaLibVersion"
}

sourceSets.findAll { it.name.startsWith('crossBuild') }.each { sourceSet ->
    def jarTask = project.tasks.findByName(sourceSet.getJarTaskName())
    publishing {
        publications {
            create(sourceSet.name, MavenPublication) {
                from components.findByName(sourceSet.name)
            }
        }
    }
}

"""

        appScalaFile << """
package com.github.prokod.it.api

case class State(n: Int, minValue: Int, maxValue: Int) {
  
  def inc: State =
    if (n == maxValue)
      this
    else
      this.copy(n = n + 1)
  
  def printAll: Unit = {
    println("Printing all")
    for {
      i <- minValue to maxValue
      j <- 0 to n
    }
    println(i + j)
  }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('tasks', 'build', 'crossBuildResolvedConfigs', 'publishToMavenLocal', '--stacktrace')
                .build()

        then:
        result.task(":tasks").outcome == SUCCESS
        result.task(":build").outcome == SUCCESS
        result.task(":publishToMavenLocal").outcome == SUCCESS

        where:
        gradleVersion   | defaultScalaLibModuleName | defaultScalaLibVersion
        '7.6.4'         | 'scala-library'          | '2.12.19'
        '7.6.4'         | 'scala-library'          | '2.13.10'
        '7.6.4'         | 'scala3-library_3'       | '3.3.1'
        '8.7'           | 'scala-library'          | '2.13.10'
        '8.7'           | 'scala-library'          | '2.12.19'
        '8.7'           | 'scala3-library_3'       | '3.3.1'
    }
}
