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

import org.gradle.internal.impldep.org.junit.Assume
import org.gradle.testkit.runner.GradleRunner
import org.skyscreamer.jsonassert.JSONAssert
import org.xmlunit.diff.Diff
import spock.lang.Unroll

import java.nio.file.Files

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginPublishingTest extends CrossBuildGradleRunnerSpec {

    File settingsFile
    File propsFile
    File buildFile
    File libBuildFile
    File libScalaFile
    File lib2BuildFile
    File lib2ScalaFile
    File lib3BuildFile
    File lib3ScalaFile
    File lib3JavaFile
    File appBuildFile
    File appScalaFile

    def setup() {
        settingsFile = file('settings.gradle')
        propsFile = file('gradle.properties')
        buildFile = file('build.gradle')
        libBuildFile = file('lib/build.gradle')
        libScalaFile = file('lib/src/main/scala/Factorial.scala')
        lib2BuildFile = file('lib2/build.gradle')
        lib2ScalaFile = file('lib2/src/main/scala/CompileTimeFactorial.scala')
        lib3BuildFile = file('lib3/build.gradle')
        lib3ScalaFile = file('lib3/src/main/scala/CompileTimeFactorialExtended.scala')
        lib3JavaFile = file('lib3/src/main/java/CompileTimeFactorialUtils.java')
        appBuildFile = file('app/build.gradle')
        appScalaFile = file('app/src/main/scala/Test.scala')
    }

    /**
     * * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Eager</li>
     *     <li> Gradle compatibility matrix: 6.x, 7.x</li>
     * </ul>
     * resource file/s for the test:
     * 04-app_builds_resolved_configurations.json
     * 04-pom_lib2-00.xml
     * 04-pom_app-00.xml
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with cross building dsl that is different on each submodule and with publishing dsl, should produce expected jars and pom files and should have correct pom files content blah"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
include 'lib2'
include 'app'
"""

        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild' apply false
}

allprojects {
    group = 'com.github.prokod.it'
    version = '1.0-SNAPSHOT'
    
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'com.github.prokod.gradle-crossbuild'
    apply plugin: 'maven-publish'

    if (!project.name.endsWith('app')) {
        crossBuild {
            builds {
                spark230_211 
                spark240_212
            }
        }
    }
    
    if (!project.name.endsWith('app')) {
        // This block is only compatible with Gradle 6 and above
        java {
            withSourcesJar()
            withJavadocJar()
        }
        publishing {
            publications {
                crossBuildSpark230_211(MavenPublication) {
                    afterEvaluate {
                        artifact crossBuildSpark230_211Jar
                    }
                }
                crossBuildSpark240_212(MavenPublication) {
                    afterEvaluate {
                        artifact crossBuildSpark240_212Jar
                    }
                }
            }
        }
    }

    tasks.withType(GenerateMavenPom) { t ->
        if (t.name.contains('CrossBuildSpark230_211')) {
            t.destination = file("\$buildDir/generated-pom_2.11.xml")
        }
        if (t.name.contains('CrossBuildSpark240_212')) {
            t.destination = file("\$buildDir/generated-pom_2.12.xml")
        }
    }
}
"""

        libScalaFile << """
object Factorial {
  // The actual implementation is regular old-fashioned scala code:
  def normalFactorial(n: Int): Int =
    if (n == 0) 1
    else n * normalFactorial(n - 1)
}
"""

        libBuildFile << """
sourceSets {
    main {
        scala {
            srcDirs = ['src/main/scala', 'src/main/java']
        }
        java {
            srcDirs = []
        }
    }
}

dependencies {
    implementation "com.google.guava:guava:18.0"
    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        lib2ScalaFile.withWriter('utf-8') {it.write """
object CompileTimeFactorial {

  import scala.language.experimental.macros
  import Factorial._

  // This function exposed to consumers has a normal Scala type:
  def factorial(n: Int): Int =
  // but it is implemented as a macro:
  macro CompileTimeFactorial.factorial_impl

  import scala.reflect.macros.blackbox.Context

  // The macro implementation will receive a ‘Context’ and
  // the AST’s of the parameters passed to it:
  def factorial_impl(c: Context)(n: c.Expr[Int]): c.Expr[Int] = {
    import c.universe._

    // We can pattern-match on the AST:
    n match {
      case Expr(Literal(Constant(nValue: Int))) =>
        // We perform the calculation:
        val result = normalFactorial(nValue)
        // And produce an AST for the result of the computation:
        c.Expr(Literal(Constant(result)))
      case other =>
        // Yes, this will be printed at compile time:
        println("Yow!")
        ???
    }
  }
}
""" }

        lib2BuildFile << """
sourceSets {
    main {
        scala {
            srcDirs = ['src/main/scala', 'src/main/java']
        }
        java {
            srcDirs = []
        }
    }
}

dependencies {
    implementation project(':lib')
    implementation 'org.scala-lang:scala-reflect:2.12.8'
    crossBuildSpark230_211Implementation 'org.scala-lang:scala-reflect:2.11.12'
    crossBuildSpark240_212Implementation 'org.scala-lang:scala-reflect:2.12.8'
}
"""

        appScalaFile << """
import CompileTimeFactorial._

object Test extends App {
    println(factorial(10))

    // When uncommented, this will produce an error at compile-time, as we
    // only implemented a case for an Int literal, not a variable:
    // val n = 10
    // println(factorial(n))
}
"""

        appBuildFile << """
crossBuild {
    builds {
        spark230_211 
    }
}

publishing {
    publications {
        crossBuildSpark230_211(MavenPublication) {
            afterEvaluate {
                artifact crossBuildSpark230_211Jar
            }
        }
    }
}
        
dependencies {
    implementation project(':lib2')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('crossBuildResolvedConfigs', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":app:crossBuildSpark230_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildResolvedConfigs").outcome == SUCCESS

        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        fileExists(dir.resolve('lib/build/libs/lib_2.12*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.11*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.12*.jar'))
        fileExists(dir.resolve('app/build/libs/app_2.11*.jar'))
        !fileExists(dir.resolve('app/build/libs/app_2.12*.jar'))

        when:
        def expectedJsonAsText = loadResourceAsText(dsv: defaultScalaVersion,
                defaultOrRuntime: gradleVersion.startsWith('4') ? 'default' : 'runtime',
                defaultOrCompile: gradleVersion.startsWith('4') ? 'default' : 'compile',
                _2_11_12_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.11.12' : '',
                '/04-app_builds_resolved_configurations.json')
        def appResolvedConfigurationReportFile = findFile("*/app_builds_resolved_configurations.json")

        then:
        appResolvedConfigurationReportFile != null
        def actualJsonAsText = appResolvedConfigurationReportFile.text
        JSONAssert.assertEquals(expectedJsonAsText, actualJsonAsText, false)

        when:
        def pom211Path = dir.resolve("lib${File.separator}build${File.separator}generated-pom_2.11.xml")
        def pom212Path = dir.resolve("lib${File.separator}build${File.separator}generated-pom_2.12.xml")

        then:
        Files.exists(pom211Path)
        Files.exists(pom212Path)

        when:
        def pom211 = new XmlSlurper().parse(pom211Path.toFile())

        then:
        pom211.dependencies.dependency.size() == 2
        pom211.dependencies.dependency[0].groupId == 'org.scala-lang'
        pom211.dependencies.dependency[0].artifactId == 'scala-library'
        pom211.dependencies.dependency[0].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        pom211.dependencies.dependency[0].scope == 'runtime'
        pom211.dependencies.dependency[1].groupId == 'com.google.guava'
        pom211.dependencies.dependency[1].artifactId == 'guava'
        pom211.dependencies.dependency[1].version == '18.0'
        pom211.dependencies.dependency[1].scope == 'runtime'

        when:
        def pom212 = new XmlSlurper().parse(pom212Path.toFile())

        then:
        pom212.dependencies.dependency.size() == 2
        pom212.dependencies.dependency[0].groupId == 'org.scala-lang'
        pom212.dependencies.dependency[0].artifactId == 'scala-library'
        pom212.dependencies.dependency[0].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.12']
        pom212.dependencies.dependency[0].scope == 'runtime'
        pom211.dependencies.dependency[1].groupId == 'com.google.guava'
        pom211.dependencies.dependency[1].artifactId == 'guava'
        pom211.dependencies.dependency[1].version == '18.0'
        pom211.dependencies.dependency[1].scope == 'runtime'

        when:
        def lib2pom211Path = dir.resolve("lib2${File.separator}build${File.separator}generated-pom_2.11.xml")
        def lib2pom212Path = dir.resolve("lib2${File.separator}build${File.separator}generated-pom_2.12.xml")

        then:
        Files.exists(lib2pom211Path)
        Files.exists(lib2pom212Path)

        when:
        def expectedLib2Pom211 = loadResourceAsText(sv: '2.11', csv: '2.11.12', '/04-pom_lib2-00.xml')
        Diff d211 = pomDiffFor(expectedLib2Pom211, lib2pom211Path.toFile())

        then:
        !d211.hasDifferences()

        when:
        def expectedLib2Pom212 = loadResourceAsText(sv: '2.12', csv: '2.12.8','/04-pom_lib2-00.xml')
        Diff d212 = pomDiffFor(expectedLib2Pom212, lib2pom212Path.toFile())

        then:
        !d212.hasDifferences()

        when:
        def appPom211Path = dir.resolve("app${File.separator}build${File.separator}generated-pom_2.11.xml")
        def appPom212Path = dir.resolve("app${File.separator}build${File.separator}generated-pom_2.12.xml")

        then:
        Files.exists(appPom211Path)
        !Files.exists(appPom212Path)

        when:
        def expectedAppPom211 = loadResourceAsText( '/04-pom_app-00.xml')
        Diff dApp211 = pomDiffFor(expectedAppPom211, appPom211Path.toFile())

        then:
        !dApp211.hasDifferences()

        where:
        gradleVersion | defaultScalaVersion
        '6.9.2'       | '2.12'
        '7.3.3'       | '2.11'
    }
}