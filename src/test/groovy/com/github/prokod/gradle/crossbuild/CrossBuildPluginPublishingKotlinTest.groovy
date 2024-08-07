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

class CrossBuildPluginPublishingKotlinTest extends CrossBuildGradleRunnerSpec {
    File settingsFileKts
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
     *     <li>The following test ensures that the plugin handles publishing with kotlin scripting</li>
     *     <li></li>
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
    `maven-publish`
}

allprojects {
    repositories {
        mavenCentral()
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

    apply(plugin = "com.github.prokod.gradle-crossbuild")
    apply(plugin = "maven-publish")

    java {
        withJavadocJar()
        withSourcesJar()
    }

    crossBuild {
        scalaVersionsCatalog = mapOf(
            "2.12" to "2.12.19",
            "2.13" to "2.13.14"
        )
        builds {
            register("scala") {
                scalaVersions = setOf("2.12", "2.13")
            }
        }
    }

    // Create Jar type task for custom scaladoc artifact
    val scaladocJar by tasks.creating(Jar::class) {
        from(tasks.getByName("scaladoc"))
        archiveClassifier.set("scaladoc")
    }

    // Exclude default artifact from publishing
    tasks.withType<PublishToMavenLocal>().configureEach {
        val predicate = provider {
            publication != publishing.publications["mavenJava"]
        }

        onlyIf("disable default maven publication") {
            predicate.get()
        }
    }

    // Publish with both source and scaladoc
    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
            create<MavenPublication>("crossBuildScala_212") {
                from(components["crossBuildScala_212"])
                artifact(scaladocJar)
                artifact(tasks["sourcesJar"])
            }
            create<MavenPublication>("crossBuildScala_213") {
                from(components["crossBuildScala_213"])
                artifact(scaladocJar)
                artifact(tasks["sourcesJar"])
            }
        }
    }

    dependencies {
        implementation("org.scala-lang:$defaultScalaLibModuleName:$defaultScalaLibVersion")
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
                .withArguments('tasks', 'crossBuildResolvedConfigs', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        println(result.output)
        result.task(":tasks").outcome == SUCCESS
        result.task(":libraryA:crossBuildScala_212Jar").outcome == SUCCESS
        result.task(":libraryA:crossBuildScala_213Jar").outcome == SUCCESS
        result.task(":libraryA:crossBuildResolvedConfigs").outcome == SUCCESS

        fileExists(dir.resolve('libraryA/build/libs/libraryA_2.12-*.jar'))
        fileExists(dir.resolve('libraryA/build/libs/libraryA_2.13-*.jar'))
        fileExists(dir.resolve('libraryA/build/libs/libraryA*-sources.jar'))
        fileExists(dir.resolve('libraryA/build/libs/libraryA*-javadoc.jar'))

        where:
        gradleVersion   | defaultScalaLibModuleName | defaultScalaLibVersion
        '7.6.4'         | 'scala-library'          | '2.12.19'
        '8.7'           | 'scala-library'          | '2.13.14'
    }
}
