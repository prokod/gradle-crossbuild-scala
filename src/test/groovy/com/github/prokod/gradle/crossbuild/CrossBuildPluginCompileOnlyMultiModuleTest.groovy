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
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginCompileOnlyMultiModuleTest extends CrossBuildGradleRunnerSpec {

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
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Lazy</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x</li>
     * </ul>
     *
     * This test checks the following plugin behaviour:
     * 1. compileOnly type of dependencies - compileOnly dependencies must be repeated in a dependent sub module that
     * contains code that requires them, even though the parent sub module (from dependency perspective) already
     * declares those compileOnly dependencies.
     * In short compileOnly dependencies can not become transitive
     * 2. misalignment in scala-lang dependency - the plugin forgives scala-lang unaligned default-variant dependency
     * by fixing it (update version) in dependency resolution
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 with a more complex inter sub module dependencies and with cross building dsl that is different on each submodule and inlined individual appendixPattern with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
include 'lib2'
include 'lib3'
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
    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild') {
        if (!project.name.endsWith('app')) {
            crossBuild {
                scalaVersionsCatalog = ['2.12':'2.12.8']
                builds {
                    spark233_211
                    spark242_212
                    spark243 {
                        scalaVersions = ['2.11', '2.12']
                        archive.appendixPattern = '-2-4-3_?'
                    }
                }
            }
        }
    }

    project.pluginManager.withPlugin('maven-publish') {
        if (!project.name.endsWith('app')) {
            publishing {
                publications {
                    crossBuildSpark233_211(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark233_211Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark242_212(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark242_212Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark243_211(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark243_211Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark243_212(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark243_212Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                }
            }
        }
    }

    tasks.withType(GenerateMavenPom) { t ->
        if (t.name.contains('CrossBuildSpark233_211')) {
            t.destination = file("\$buildDir/generated-pom233_2.11.xml")
        }
        if (t.name.contains('CrossBuildSpark242_212')) {
            t.destination = file("\$buildDir/generated-pom242_2.12.xml")
        }
    }
}
"""

        libScalaFile << """

object scalazOption {
  import scalaz._
  import Scalaz._ 
  val boolT = 6 < 10
  
  boolT.option("corrie")
}

object Factorial {
  // The actual implementation is regular old-fashioned scala code:
  def normalFactorial(n: Int): Int =
    if (n == 0) 1
    else n * normalFactorial(n - 1)
}
"""

        libBuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

crossBuild {
    builds {
        v210 {
            archive.appendixPattern = '-legacy_?'
        }
    }
}

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
    compileOnly 'org.scalaz:scalaz-core_${defaultScalaVersion}:7.2.28'
    crossBuildV210CompileOnly 'org.scalaz:scalaz-core_2.10:7.2.25'
    crossBuildSpark233_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.26'
    crossBuildSpark242_212CompileOnly 'org.scalaz:scalaz-core_2.12:7.2.27'
    crossBuildSpark243_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.28'
    crossBuildSpark243_212CompileOnly 'org.scalaz:scalaz-core_2.12:7.2.28'

    implementation 'org.scala-lang:scala-library:${defaultScalaVersion}.+'
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
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

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
    api project(':lib')
    
    compileOnly 'org.scalaz:scalaz-core_2.11:7.2.28'
    crossBuildSpark233_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.26'
    crossBuildSpark242_212CompileOnly 'org.scalaz:scalaz-core_2.12:7.2.27'
    crossBuildSpark243_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.28'
    crossBuildSpark243_212CompileOnly 'org.scalaz:scalaz-core_2.12:7.2.28'
    
    // Plugin forgives on this scala-lang unaligned default-variant dependency and fixes it in dependency resolution
    implementation 'org.scala-lang:scala-reflect:2.12.8'
}
"""

        lib3ScalaFile.withWriter('utf-8') {it.write """
object CompileTimeFactorialExtended {

  import scala.language.experimental.macros
  import Factorial._

  // This function exposed to consumers has a normal Scala type:
  def factorial(n: Int): Int =
  // but it is implemented as a macro:
  macro CompileTimeFactorialExtended.factorial_impl

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

        lib3BuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

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
}
"""

        appScalaFile << """
object scalazOption {
  import scalaz._
  import Scalaz._ 
  val boolT = 6 < 10
  
  boolT.option("corrie")
}

object Test extends App {
    import CompileTimeFactorial._

    println(factorial(10))

    // When uncommented, this will produce an error at compile-time, as we
    // only implemented a case for an Int literal, not a variable:
    // val n = 10
    // println(factorial(n))
}
"""

        appBuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

crossBuild {
    builds {
        spark233_211 {
            archive.appendixPattern = '-all_?'
        }
    }
}

publishing {
    publications {
        crossBuildSpark233_211(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildSpark233_211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
        
dependencies {
    implementation project(':lib2')

    compileOnly 'org.scalaz:scalaz-core_${defaultScalaVersion}:7.2.28'
    crossBuildSpark233_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.26'
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments('build', 'lib:crossBuildV210Jar', 'lib2:crossBuildResolvedConfigs', 'lib3:crossBuildResolvedConfigs', 'app:crossBuildSpark233_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:build").outcome == SUCCESS
        result.task(":lib2:build").outcome == SUCCESS
        result.task(":lib3:build").outcome == SUCCESS
        result.task(":app:build").outcome == SUCCESS
        result.task(":lib2:crossBuildResolvedConfigs").outcome == SUCCESS
        result.task(":lib3:crossBuildResolvedConfigs").outcome == SUCCESS
        result.task(":lib:crossBuildV210Jar").outcome == SUCCESS
        result.task(":lib:crossBuildSpark233_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark233_211Jar").outcome == SUCCESS

        // 'build' task should:
        fileExists("$dir.root.absolutePath/lib/build/libs/lib-1.0-SNAPSHOT.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2-1.0-SNAPSHOT.jar")
        fileExists("$dir.root.absolutePath/lib3/build/libs/lib3-1.0-SNAPSHOT.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app-1.0-SNAPSHOT.jar")

        // 'lib:crossBuildV210Jar', 'app:crossBuildSpark233_211Jar' tasks should:
        fileExists("$dir.root.absolutePath/lib/build/libs/lib-legacy_2.10*.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.12*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.12*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.11*.jar")
        !fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.12*.jar")

        when:
        // Gradle 4 'java' plugin Configuration model is less precise and so firstLevelModuleDependencies are under
        // 'default' configuration, Gradle 5 already has a more precise model and so 'default' configuration is replaced
        // by either 'runtime' or 'compile' see https://gradle.org/whats-new/gradle-5/#fine-grained-transitive-dependency-management
        def expectedLib2JsonAsText = loadResourceAsText(dsv: defaultScalaVersion,
                defaultOrRuntime: gradleVersion.startsWith('4') ? 'default' : 'runtime',
                defaultOrCompile: gradleVersion.startsWith('4') ? 'default' : 'compile',
                _2_12_8_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.12.8' : '',
                _2_11_12_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.11.12' : '',
                _7_2_26_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-7.2.26' : '',
                _7_2_27_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-7.2.27' : '',
                _7_2_28_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-7.2.28' : '',
                '/lib_builds_resolved_configurations-02.json')
        def lib2ResolvedConfigurationReportFile = findFile("*/lib2_builds_resolved_configurations.json")

        then:
        lib2ResolvedConfigurationReportFile != null
        def actualLib2JsonAsText = lib2ResolvedConfigurationReportFile.text
        JSONAssert.assertEquals(expectedLib2JsonAsText, actualLib2JsonAsText, false)

        when:
        // Gradle 4 'java' plugin Configuration model is less precise ans so firstLevelModuleDependencies are under
        // 'default' configuration, Gradle 5 already has a more precise model and so 'default' configuration is replaced
        // by either 'runtime' or 'compile' see https://gradle.org/whats-new/gradle-5/#fine-grained-transitive-dependency-management
        def expectedLib3JsonAsText = loadResourceAsText(dsv: defaultScalaVersion,
                defaultOrRuntime: gradleVersion.startsWith('4') ? 'default' : 'runtime',
                defaultOrCompile: gradleVersion.startsWith('4') ? 'default' : 'compile',
                _2_12_8_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.12.8' : '',
                _2_11_12_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.11.12' : '',
                '/lib_builds_resolved_configurations-03.json')
        def lib3ResolvedConfigurationReportFile = findFile("*/lib3_builds_resolved_configurations.json")

        then:
        lib3ResolvedConfigurationReportFile != null
        def actualLib3JsonAsText = lib3ResolvedConfigurationReportFile.text
        JSONAssert.assertEquals(expectedLib3JsonAsText, actualLib3JsonAsText, false)

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.11'
        '6.9.2'         | '2.12'
        '7.3.3'         | '2.12'
    }
}