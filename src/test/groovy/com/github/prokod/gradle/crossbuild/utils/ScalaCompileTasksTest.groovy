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
package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildGradleRunnerSpec
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Requires
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ScalaCompileTasksTest extends CrossBuildGradleRunnerSpec {
    File settingsFileKts
    File buildFileKts
    File libBuildFileKts
    File settingsFile
    File buildFile
    File libBuildFile
    File libScalaFile

    def setup() {
        settingsFileKts = file('settings.gradle.kts')
        buildFileKts = file('build.gradle.kts')
        libBuildFileKts = file('libraryA/build.gradle.kts')
        settingsFile = file('settings.gradle')
        buildFile = file('build.gradle')
        libBuildFile = file('libraryA/build.gradle')
        libScalaFile = file('libraryA/src/main/scala/com/github/prokod/it/example/Example.scala')
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
     *     <li>The following test ensures that the plugin handles compiling for latest scala 2.12/2.13/3 without failure
     *     when user adds -Xfatal-warnings flag to compiler/li>
     * </ul>
     *
     * Initial manual testing results that lead to these unit tests
     * <ul>
     *      <li> sourceset: main          , task: compileScala (2.13)       , toolchain: true , toolchain-version: 8 , test-7: fail, test-8: pass</li>
     *      <li> sourceset: main          , task: compileScala (2.12)       , toolchain: true , toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV212, task: compileCrossBuildV213Scala, toolchain: true , toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV213, task: compileCrossBuildV213Scala, toolchain: true , toolchain-version: 8 , test-7: fail, test-8: pass</li>
     *      <li> sourceset: main          , task: compileScala (2.13)       , toolchain: true , toolchain-version: 11, test-7: fail, test-8: pass</li>
     *      <li> sourceset: main          , task: compileScala (2.12)       , toolchain: true , toolchain-version: 11, test-7: fail, test-8: fail</li>
     *      <li> sourceset: crossBuildV212, task: compileCrossBuildV213Scala, toolchain: true , toolchain-version: 11, test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV213, task: compileCrossBuildV213Scala, toolchain: true , toolchain-version: 11, test-7: fail, test-8: pass</li>
     *      <li> sourceset: main          , task: compileScala (2.13)       , toolchain: false, toolchain-version: 8 , test-7: pass, test-8: fail</li>
     *      <li> sourceset: main          , task: compileScala (2.12)       , toolchain: false, toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV212, task: compileCrossBuildV213Scala, toolchain: false, toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV213, task: compileCrossBuildV213Scala, toolchain: false, toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: main          , task: compileScala (2.13)       , toolchain: false, toolchain-version: 11, test-7: pass, test-8: fail</li>
     *      <li> sourceset: main          , task: compileScala (2.12)       , toolchain: false, toolchain-version: 11, test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV212, task: compileCrossBuildV213Scala, toolchain: false, toolchain-version: 11, test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV213, task: compileCrossBuildV213Scala, toolchain: false, toolchain-version: 11, test-7: pass, test-8: fail</li>
     * </ul>
     * <ul>
     *      <li> sourceset: main          , task: compileScala (2.13)       , toolchain: true , toolchain-version: 8 , test-7: fail, test-8: pass</li>
     *      <li> sourceset: main          , task: compileScala (2.13)       , toolchain: true , toolchain-version: 11, test-7: fail, test-8: pass</li>
     *      <li> sourceset: main          , task: compileScala (2.12)       , toolchain: true , toolchain-version: 11, test-7: fail, test-8: fail</li>
     *      <li> sourceset: main          , task: compileScala (2.13)       , toolchain: false, toolchain-version: 8 , test-7: pass, test-8: fail</li>
     *      <li> sourceset: main          , task: compileScala (2.13)       , toolchain: false, toolchain-version: 11, test-7: pass, test-8: fail</li>
     *      <li> sourceset: main          , task: compileScala (2.12)       , toolchain: true , toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: main          , task: compileScala (2.12)       , toolchain: false, toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: main          , task: compileScala (2.12)       , toolchain: false, toolchain-version: 11, test-7: pass, test-8: pass</li>
     *
     *      PASS when toolchain is not specified by user
     *      PASS when toolchain is specified and it is java 8
     *
     * </ul>
     * <ul>
     *      <li> sourceset: crossBuildV213, task: compileCrossBuildV213Scala, toolchain: true , toolchain-version: 8 , test-7: fail, test-8: pass</li>
     *      <li> sourceset: crossBuildV213, task: compileCrossBuildV213Scala, toolchain: true , toolchain-version: 11, test-7: fail, test-8: pass</li>
     *      <li> sourceset: crossBuildV213, task: compileCrossBuildV213Scala, toolchain: false, toolchain-version: 11, test-7: pass, test-8: fail</li>
     *
     *      <li> sourceset: crossBuildV212, task: compileCrossBuildV212Scala, toolchain: true , toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV212, task: compileCrossBuildV212Scala, toolchain: true , toolchain-version: 11, test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV212, task: compileCrossBuildV212Scala, toolchain: false, toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV213, task: compileCrossBuildV213Scala, toolchain: false, toolchain-version: 8 , test-7: pass, test-8: pass</li>
     *      <li> sourceset: crossBuildV212, task: compileCrossBuildV212Scala, toolchain: false, toolchain-version: 11, test-7: pass, test-8: pass</li>
     *
     *      PASS when scala is 2.12
     * </ul>
     * @see <a href="https://github.com/prokod/gradle-crossbuild-scala/issues/140">issue #140</a>
     */
    @Requires({ System.getProperty("java.version").startsWith('11.') || System.getProperty("java.version").startsWith('17.') })
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def """[gradle:#gradleVersion | scala-lib:#scalaLibModuleName | scala-version:#scalaLibVersion | toolchain:#setToolchain | toolChainVersion:#toolchainVersion | scalaCompileFlag:#scalaCompileFlag1]
           applying crossbuild plugin on a project with cross building of both scala 2.x and 3.x
           with multiple combinations of Gradle version and Toolchain version (JDK)
           should result in successful build"""() {
        given:

        settingsFile << """
rootProject.name = "gradle-scala-template"

include 'libraryA'
"""
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
    id 'com.github.prokod.gradle-crossbuild' apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    configurations {
        scalaCompilerPlugin {
            transitive = false
        }
    }
}

subprojects {
    group = "com.github.prokod.it"
    version = "1.0-SNAPSHOT"

    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
    apply plugin: 'maven-publish'
    
    ${setToolchain ? "java.toolchain.languageVersion = JavaLanguageVersion.of(${toolchainVersion})" : ""}

    crossBuild {

        scalaVersionsCatalog = ["${scalaVersion}": "${scalaLibVersion}"]

        builds {
            scala {
                scalaVersions = ["${scalaVersion}"]
            }
        }
    }

    dependencies {
        implementation "org.scala-lang:$scalaLibModuleName:$scalaLibVersion"
    }

    tasks.withType(ScalaCompile) {
        scalaCompileOptions.additionalParameters = ['-feature', '$scalaCompileFlag1']
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
                .withArguments('tasks', 'crossBuildAssemble', 'crossBuildResolvedConfigs', '--stacktrace')
                .build()

        then:
        result.task(":tasks").outcome == SUCCESS
        result.task(":libraryA:crossBuildAssemble").outcome == SUCCESS

        fileExists(dir.resolve("libraryA/build/libs/libraryA_$scalaVersion-*.jar"))

        where:
        gradleVersion   | scalaLibModuleName | scalaVersion | scalaLibVersion | setToolchain | toolchainVersion  | scalaCompileFlag1
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | true         | "11"              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | '-Xfatal-warnings'
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | '-Xfatal-warnings'
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | '-Xfatal-warnings'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | ''
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | ''
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | true         | "11"              | ''
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | ''
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | false        | null              | ''
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | ''
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | true         | "8"               | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | true         | "8"               | ''
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | ''
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | true         | "11"              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | true         | "11"              | ''
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | ''
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | false        | null              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | false        | null              | ''
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | ''
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | ''
    }

    @Requires({ System.getProperty("java.version").startsWith('11.') || System.getProperty("java.version").startsWith('17.') })
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def """[gradle:#gradleVersion | scala-lib:#scalaLibModuleName | scala-version:#scalaLibVersion | toolchain:#setToolchain | toolChainVersion:#toolchainVersion | scalaCompileFlag:#scalaCompileFlag1]
           applying crossbuild plugin on a project with cross building of both scala 2.x and 3.x
           with multiple combinations of Gradle version and Toolchain version (JDK)
           should result in a failed build"""() {
        given:

        settingsFile << """
rootProject.name = "gradle-scala-template"

include 'libraryA'
"""
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
    id 'com.github.prokod.gradle-crossbuild' apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    configurations {
        scalaCompilerPlugin {
            transitive = false
        }
    }
}

subprojects {
    group = "com.github.prokod.it"
    version = "1.0-SNAPSHOT"

    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
    apply plugin: 'maven-publish'
    
    ${setToolchain ? "java.toolchain.languageVersion = JavaLanguageVersion.of(${toolchainVersion})" : ""}

    crossBuild {

        scalaVersionsCatalog = ["${scalaVersion}": "${scalaLibVersion}"]

        builds {
            scala {
                scalaVersions = ["${scalaVersion}"]
            }
        }
    }

    dependencies {
        implementation "org.scala-lang:$scalaLibModuleName:$scalaLibVersion"
    }

    tasks.withType(ScalaCompile) {
        scalaCompileOptions.additionalParameters = ['-feature', '$scalaCompileFlag1']
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
                .withArguments('tasks', 'crossBuildAssemble', 'crossBuildResolvedConfigs', '--stacktrace')
                .build()

        then:
        def error = thrown(expectedException)
        error.message.contains(expectedMessage)

        where:
        gradleVersion   | scalaLibModuleName | scalaVersion | scalaLibVersion | setToolchain | toolchainVersion  | scalaCompileFlag1  | expectedException      |  expectedMessage
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | true         | "8"               | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | true         | "8"               | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | true         | "11"              | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | true         | "11"              | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
    }

    @Requires({ System.getProperty("java.version").startsWith('1.8') })
    @Requires({ instance.testMavenCentralAccess() })
    def """[gradle:#gradleVersion | scala-lib:#scalaLibModuleName | scala-version:#scalaLibVersion | toolchain:#setToolchain | toolChainVersion:#toolchainVersion | scalaCompileFlag:#scalaCompileFlag1]
           applying crossbuild plugin on a project with cross building of both scala 2.x and 3.x
           with multiple combinations of Gradle version and Toolchain version (JDK)
           with Gradle runtime JDK 8
           should result in successful build"""() {
        given:

        settingsFile << """
rootProject.name = "gradle-scala-template"

include 'libraryA'
"""
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
    id 'com.github.prokod.gradle-crossbuild' apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    configurations {
        scalaCompilerPlugin {
            transitive = false
        }
    }
}

subprojects {
    group = "com.github.prokod.it"
    version = "1.0-SNAPSHOT"

    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
    apply plugin: 'maven-publish'
    
    ${setToolchain ? "java.toolchain.languageVersion = JavaLanguageVersion.of(${toolchainVersion})" : ""}

    crossBuild {

        scalaVersionsCatalog = ["${scalaVersion}": "${scalaLibVersion}"]

        builds {
            scala {
                scalaVersions = ["${scalaVersion}"]
            }
        }
    }

    dependencies {
        implementation "org.scala-lang:$scalaLibModuleName:$scalaLibVersion"
    }

    tasks.withType(ScalaCompile) {
        scalaCompileOptions.additionalParameters = ['-feature', '$scalaCompileFlag1']
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
                .withArguments('tasks', 'crossBuildAssemble', 'crossBuildResolvedConfigs', '--stacktrace')
                .build()

        then:
        result.task(":tasks").outcome == SUCCESS
        result.task(":libraryA:crossBuildAssemble").outcome == SUCCESS

        fileExists(dir.resolve("libraryA/build/libs/libraryA_$scalaVersion-*.jar"))

        where:
        gradleVersion   | scalaLibModuleName | scalaVersion | scalaLibVersion | setToolchain | toolchainVersion  | scalaCompileFlag1
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | '-Xfatal-warnings'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | ''
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | ''
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | false        | null              | ''
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | ''
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | true         | "8"               | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | true         | "8"               | ''
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | ''
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | false        | null              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | false        | null              | ''
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | ''
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | ''
    }

    @Requires({ System.getProperty("java.version").startsWith('1.8') })
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def """[gradle:#gradleVersion | scala-lib:#scalaLibModuleName | scala-version:#scalaLibVersion | toolchain:#setToolchain | toolChainVersion:#toolchainVersion | scalaCompileFlag:#scalaCompileFlag1]
           applying crossbuild plugin on a project with cross building of both scala 2.x and 3.x
           with multiple combinations of Gradle version and Toolchain version (JDK)
           with Gradle runtime JDK 8
           should result in a failed build"""() {
        given:

        settingsFile << """
rootProject.name = "gradle-scala-template"

include 'libraryA'
"""
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
    id 'com.github.prokod.gradle-crossbuild' apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    configurations {
        scalaCompilerPlugin {
            transitive = false
        }
    }
}

subprojects {
    group = "com.github.prokod.it"
    version = "1.0-SNAPSHOT"

    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
    apply plugin: 'maven-publish'
    
    ${setToolchain ? "java.toolchain.languageVersion = JavaLanguageVersion.of(${toolchainVersion})" : ""}

    crossBuild {

        scalaVersionsCatalog = ["${scalaVersion}": "${scalaLibVersion}"]

        builds {
            scala {
                scalaVersions = ["${scalaVersion}"]
            }
        }
    }

    dependencies {
        implementation "org.scala-lang:$scalaLibModuleName:$scalaLibVersion"
    }

    tasks.withType(ScalaCompile) {
        scalaCompileOptions.additionalParameters = ['-feature', '$scalaCompileFlag1']
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
                .withArguments('tasks', 'crossBuildAssemble', 'crossBuildResolvedConfigs', '--stacktrace')
                .build()

        then:
        def error = thrown(expectedException)
        error.message.contains( expectedMessage)

        where:
        gradleVersion   | scalaLibModuleName | scalaVersion | scalaLibVersion | setToolchain | toolchainVersion  | scalaCompileFlag1  | expectedException      |  expectedMessage
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | true         | "8"               | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | true         | "8"               | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | true         | "11"              | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | true         | "11"              | '-Xfatal-warnings' | Exception | 'Consider the following option: Try using later Gradle version'
    }

    /**
     * This test basically gives an upp to date state of scala compilation by Gradle's scala plugin itself
     * Currently the plugin is not interfering with default build scalac arguments
     *
     * @return
     */
    @Requires({ System.getProperty("java.version").startsWith('11.') || System.getProperty("java.version").startsWith('17.') })
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def """[gradle:#gradleVersion | scala-lib:#scalaLibModuleName | scala-version:#scalaLibVersion | toolchain:#setToolchain | toolChainVersion:#toolchainVersion | scalaCompileFlag:#scalaCompileFlag1]
           applying crossbuild plugin on a project with cross building of both scala 2.x and 3.x
           with multiple combinations of Gradle version and Toolchain version (JDK)
           with default scala version compilation (calling build task)
           should result in successful build"""() {
        given:

        settingsFile << """
rootProject.name = "gradle-scala-template"

include 'libraryA'
"""
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
    id 'com.github.prokod.gradle-crossbuild' apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    configurations {
        scalaCompilerPlugin {
            transitive = false
        }
    }
}

subprojects {
    group = "com.github.prokod.it"
    version = "1.0-SNAPSHOT"

    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
    apply plugin: 'maven-publish'
    
    ${setToolchain ? "java.toolchain.languageVersion = JavaLanguageVersion.of(${toolchainVersion})" : ""}

    crossBuild {

        scalaVersionsCatalog = ["${scalaVersion}": "${scalaLibVersion}"]

        builds {
            scala {
                scalaVersions = ["${scalaVersion}"]
            }
        }
    }

    dependencies {
        implementation "org.scala-lang:$scalaLibModuleName:$scalaLibVersion"
    }

    tasks.withType(ScalaCompile) {
        scalaCompileOptions.additionalParameters = ['-feature', '$scalaCompileFlag1']
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
                .withArguments('tasks', 'build', 'crossBuildResolvedConfigs', '--stacktrace')
                .build()

        then:
        result.task(":tasks").outcome == SUCCESS
        result.task(":libraryA:build").outcome == SUCCESS

        fileExists(dir.resolve("libraryA/build/libs/libraryA-*.jar"))

        where:
        gradleVersion   | scalaLibModuleName | scalaVersion | scalaLibVersion | setToolchain | toolchainVersion  | scalaCompileFlag1
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | '-Xfatal-warnings'
//        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | '-Xfatal-warnings'
//        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | '-Xfatal-warnings'
//        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | '-Xfatal-warnings'
//        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | '-Xfatal-warnings'
//        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | '-Xfatal-warnings'
//        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | true         | "11"              | '-Xfatal-warnings'
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | '-Xfatal-warnings'
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | false        | null              | '-Xfatal-warnings'
//        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | '-Xfatal-warnings'
//        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | '-Xfatal-warnings'
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | '-Xfatal-warnings'
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | '-Xfatal-warnings'
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | '-Xfatal-warnings'
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | '-Xfatal-warnings'
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | '-Xfatal-warnings'
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | '-Xfatal-warnings'
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "8"               | ''
//        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | ''
//        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | ''
//        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | true         | "11"              | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.15'       | false        | null              | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "8"               | ''
//        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | ''
//        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | ''
//        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | true         | "11"              | ''
        '7.6.4'         | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | ''
        '8.4'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | ''
        '8.7'           | 'scala-library'    | '2.12'       | '2.12.17'       | false        | null              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | ''
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | ''
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "8"               | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | true         | "11"              | ''
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | ''
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | true         | "11"              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | ''
        '7.6.4'         | 'scala-library'    | '2.13'       | '2.13.1'        | false        | null              | ''
        '8.4'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | ''
        '8.7'           | 'scala-library'    | '2.13'       | '2.13.12'       | false        | null              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | true         | "8"               | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | true         | "8"               | ''
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | ''
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "8"               | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | true         | "11"              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | true         | "11"              | ''
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | ''
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | true         | "11"              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.2.1'         | false        | null              | ''
        '7.6.4'         | 'scala3-library_3' | '3'          | '3.1.1'         | false        | null              | ''
        '8.4'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | ''
        '8.7'           | 'scala3-library_3' | '3'          | '3.3.1'         | false        | null              | ''
    }
}
