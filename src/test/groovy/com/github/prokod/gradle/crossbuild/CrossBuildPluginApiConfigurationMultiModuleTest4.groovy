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

class CrossBuildPluginApiConfigurationMultiModuleTest4 extends CrossBuildGradleRunnerSpec {

    File settingsFile
    File propsFile
    File buildFile
    File libBuildFile
    File libScalaFile
    File libJavaFile
    File lib2BuildFile
    File lib2ScalaFile
    File lib2ScalaImplFile
    File lib3BuildFile
    File lib3JavaFile
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
        lib3BuildFile = file('lib3/build.gradle')
        lib3JavaFile = file('lib3/src/main/java/CompileTimeFactorialUtils.java')
        appBuildFile = file('app/build.gradle')
        appScalaFile = file('app/src/main/scala/HelloWorldApp.scala')
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Lazy</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x</li>
     * </ul>
     *
     * Here lib3 is a non cross build dependency.
     * Also lib2 has another _? type of scala 3rd party lib dependency.
     * This test checks that the default scala for lib2 is detected based on scala-lib dependency in the dependent
     * module lib1
     *
     * @see <a href="https://github.com/prokod/gradle-crossbuild-scala/issues/90">issue #90</a>
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with a single scala-lib dependency in lib1 should compile correctly lib2 without errors"() {
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
                        afterEvaluate {
                            artifact crossBuildSpark233_211Jar
                        }
                    }
                    crossBuildSpark242_212(MavenPublication) {
                        afterEvaluate {
                            artifact crossBuildSpark242_212Jar
                        }
                    }
                    crossBuildSpark243_211(MavenPublication) {
                        afterEvaluate {
                            artifact crossBuildSpark243_211Jar
                        }
                    }
                    crossBuildSpark243_212(MavenPublication) {
                        afterEvaluate {
                            artifact crossBuildSpark243_212Jar
                        }
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

dependencies {
    implementation 'org.scalaz:scalaz-core_?:7.2.+'

    implementation 'org.scala-lang:scala-library:${defaultScalaVersion}.+'
}
"""

        lib2ScalaFile.withWriter('utf-8') {it.write """
object CompileTimeFactorial {

  import scala.language.experimental.macros
  import Factorial._
  import CompileTimeFactorialUtils._

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
        println(CompileTimeFactorialUtils.printHello)
        ???
    }
  }
}
""" }

        lib2BuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

dependencies {
    api project(':lib')
    implementation project(':lib3')
    
    implementation 'io.skuber:skuber_?:2.5.0'
    
    // Plugin forgives on this scala-lang unaligned default-variant dependency and fixes it in dependency resolution
    implementation 'org.scala-lang:scala-reflect:2.12.8'
}
"""

        lib3JavaFile << """
import org.apache.commons.math3.util.MathUtils;

public class CompileTimeFactorialUtils {

   public static String printHello() {
      double twoPi = MathUtils.TWO_PI;
      return "Hello! " + twoPi;
   }
}
"""

        lib3BuildFile << """
apply plugin: 'java'

dependencies {
  implementation 'org.apache.commons:commons-math3:3.6.1'
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
            afterEvaluate {
                artifact crossBuildSpark233_211Jar
            }
        }
    }
}
        
dependencies {
    implementation project(':lib2')
    implementation 'org.scalaz:scalaz-core_?:7.2.+'
    implementation 'org.scala-lang:scala-library:${defaultScalaVersion}.+'
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('build', 'lib:crossBuildV210Jar', 'lib2:crossBuildResolvedConfigs', 'lib3:jar', 'app:crossBuildSpark233_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:build").outcome == SUCCESS
        result.task(":lib2:build").outcome == SUCCESS
        result.task(":lib3:build").outcome == SUCCESS
        result.task(":app:build").outcome == SUCCESS
        result.task(":lib2:crossBuildResolvedConfigs").outcome == SUCCESS
        result.task(":lib3:jar").outcome == SUCCESS
        result.task(":lib:crossBuildV210Jar").outcome == SUCCESS
        result.task(":lib:crossBuildSpark233_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark233_211Jar").outcome == SUCCESS

        // 'build' task should:
        fileExists(dir.resolve('lib/build/libs/lib-1.0-SNAPSHOT.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2-1.0-SNAPSHOT.jar'))
        fileExists(dir.resolve('lib3/build/libs/lib3-1.0-SNAPSHOT.jar'))
        fileExists(dir.resolve('app/build/libs/app-1.0-SNAPSHOT.jar'))

        // 'lib:crossBuildV210Jar', 'app:crossBuildSpark233_211Jar' tasks should:
        fileExists(dir.resolve('lib/build/libs/lib-legacy_2.10*.jar'))
        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        !fileExists(dir.resolve('lib/build/libs/lib_2.12*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.11*.jar'))
        !fileExists(dir.resolve('lib2/build/libs/lib2_2.12*.jar'))
        fileExists(dir.resolve('app/build/libs/app-all_2.11*.jar'))
        !fileExists(dir.resolve('app/build/libs/app-all_2.12*.jar'))

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.11'
        '6.9.2'         | '2.12'
        '7.3.3'         | '2.11'
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Lazy</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x</li>
     * </ul>
     *
     * Here we test support for custom scala tag (e.g _2.11, _2.12, _2.13) instead of _? in dependencies
     *
     * Details:
     * <ul>
     *      <li>lib3 is a non cross build project dependency</li>
     *      <li>lib2 has default scala version as a replacement for another _? type of scala 3rd party lib dependency</li>
     * </ul>
     *
     * This test checks that resolved dependencies are correct even though _? scala tag is not used
     *
     * @see <a href="https://github.com/prokod/gradle-crossbuild-scala/issues/90">issue #80</a>
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with no _? scala tag results in correct resolved scala deps"() {
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
                        afterEvaluate {
                            artifact crossBuildSpark233_211Jar
                        }
                    }
                    crossBuildSpark242_212(MavenPublication) {
                        afterEvaluate {
                            artifact crossBuildSpark242_212Jar
                        }
                    }
                    crossBuildSpark243_211(MavenPublication) {
                        afterEvaluate {
                            artifact crossBuildSpark243_211Jar
                        }
                    }
                    crossBuildSpark243_212(MavenPublication) {
                        afterEvaluate {
                            artifact crossBuildSpark243_212Jar
                        }
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

dependencies {
    implementation 'org.scalaz:scalaz-core_${defaultScalaVersion}:7.2.+'

    implementation 'org.scala-lang:scala-library:${defaultScalaVersion}.+'
}
"""

        lib2ScalaFile.withWriter('utf-8') {it.write """
object CompileTimeFactorial {

  import scala.language.experimental.macros
  import Factorial._
  import CompileTimeFactorialUtils._

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
        println(CompileTimeFactorialUtils.printHello)
        ???
    }
  }
}
""" }

        lib2BuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

dependencies {
    api project(':lib')
    implementation project(':lib3')
    
    implementation 'io.skuber:skuber_${defaultScalaVersion}:2.5.0'
    
    // Plugin forgives on this scala-lang unaligned default-variant dependency and fixes it in dependency resolution
    implementation 'org.scala-lang:scala-reflect:2.12.8'
}
"""

        lib3JavaFile << """
import org.apache.commons.math3.util.MathUtils;

public class CompileTimeFactorialUtils {

   public static String printHello() {
      double twoPi = MathUtils.TWO_PI;
      return "Hello! " + twoPi;
   }
}
"""

        lib3BuildFile << """
apply plugin: 'java'

dependencies {
  implementation 'org.apache.commons:commons-math3:3.6.1'
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
            afterEvaluate {
                artifact crossBuildSpark233_211Jar
            }
        }
    }
}
        
dependencies {
    implementation project(':lib2')

    implementation 'org.scalaz:scalaz-core_${defaultScalaVersion}:7.2.+'
    implementation 'org.scala-lang:scala-library:${defaultScalaVersion}.+'
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()//.forwardOutput()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('build', 'lib:crossBuildV210Jar', 'lib:crossBuildResolvedConfigs', 'lib2:crossBuildResolvedConfigs', 'lib3:jar', 'app:crossBuildSpark233_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:build").outcome == SUCCESS
        result.task(":lib2:build").outcome == SUCCESS
        result.task(":lib3:build").outcome == SUCCESS
        result.task(":app:build").outcome == SUCCESS
        result.task(":lib2:crossBuildResolvedConfigs").outcome == SUCCESS
        result.task(":lib3:jar").outcome == SUCCESS
        result.task(":lib:crossBuildV210Jar").outcome == SUCCESS
        result.task(":lib:crossBuildSpark233_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark233_211Jar").outcome == SUCCESS

        // 'build' task should:
        fileExists(dir.resolve('lib/build/libs/lib-1.0-SNAPSHOT.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2-1.0-SNAPSHOT.jar'))
        fileExists(dir.resolve('lib3/build/libs/lib3-1.0-SNAPSHOT.jar'))
        fileExists(dir.resolve('app/build/libs/app-1.0-SNAPSHOT.jar'))

        // 'lib:crossBuildV210Jar', 'app:crossBuildSpark233_211Jar' tasks should:
        fileExists(dir.resolve('lib/build/libs/lib-legacy_2.10*.jar'))
        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        !fileExists(dir.resolve('lib/build/libs/lib_2.12*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.11*.jar'))
        !fileExists(dir.resolve('lib2/build/libs/lib2_2.12*.jar'))
        fileExists(dir.resolve('app/build/libs/app-all_2.11*.jar'))
        !fileExists(dir.resolve('app/build/libs/app-all_2.12*.jar'))

        when:
        // Gradle 4 'java' plugin Configuration model is less precise and so firstLevelModuleDependencies are under
        // 'default' configuration, Gradle 5 already has a more precise model and so 'default' configuration is replaced
        // by either 'runtime' or 'compile' see https://gradle.org/whats-new/gradle-5/#fine-grained-transitive-dependency-management
        def expectedLibJsonAsText = loadResourceAsText(dsv: defaultScalaVersion,
                defaultOrRuntime: gradleVersion.startsWith('4') ? 'default' : 'runtime',
                defaultOrCompile: gradleVersion.startsWith('4') ? 'default' : 'compile',
                _2_12_cond_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '2.12.15' : '',
                _2_12_: '2.12.15',
                _2_11_12_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.11.12' : '',
                _2_10_7_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.10.7' : '',
                _7_2_plus_for_2_10_cond_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '7.2.30' : '',
                _7_2_plus_for_2_10_: '7.2.30',
                _7_2_plus_cond_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '7.2.34' : '',
                _7_2_plus_: '7.2.34',
                dashOrEmpty: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-' : '',
                '/lib_builds_resolved_configurations-04.json')
        def libResolvedConfigurationReportFile = findFile("*/lib_builds_resolved_configurations.json")

        then:
        libResolvedConfigurationReportFile != null
        def actualLibJsonAsText = libResolvedConfigurationReportFile.text
        JSONAssert.assertEquals(expectedLibJsonAsText, actualLibJsonAsText, false)

        when:
        // Gradle 4 'java' plugin Configuration model is less precise ans so firstLevelModuleDependencies are under
        // 'default' configuration, Gradle 5 already has a more precise model and so 'default' configuration is replaced
        // by either 'runtime' or 'compile' see https://gradle.org/whats-new/gradle-5/#fine-grained-transitive-dependency-management
        def expectedLib2JsonAsText = loadResourceAsText(dsv: defaultScalaVersion,
                defaultOrRuntime: gradleVersion.startsWith('4') ? 'default' : 'runtime',
                defaultOrCompile: gradleVersion.startsWith('4') ? 'default' : 'compile',
                _2_12_8_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.12.8' : '',
                _2_12_cond_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.12.10' : '',
                _2_12_: '2.12.10',
                _2_11_12_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.11.12' : '',
                _skuber_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.5.0' : '',
                _play_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.7.4' : '',
                _scalaz_v_cond_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-7.2.34' : '',
                _scalaz_v_: '7.2.34',
                _akka_http_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-10.1.11' : '',
                _akka_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.5.29' : '',
                _ssl_config_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-0.3.8' : '',
                _scala_java8_compat_2_11_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-0.7.0' : '',
                _scala_java8_compat_2_12_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-0.8.0' : '',
                _scala_parser_combinators_2_11_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-1.1.1' : '',
                _scala_parser_combinators_2_12_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-1.1.2' : '',
                _commons_math3_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-3.6.1' : '',
                _snakeyaml_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-1.25' : '',
                _commons_io_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.6' : '',
                _commons_codec_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-1.14' : '',
                _jdk15on_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-1.64' : '',
                _reactive_streams_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-1.0.2' : '',
                _joda_time_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.10.1' : '',
                _jackson_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-2.9.8' : '',
                _config_v_: gradleVersion.startsWith('6') || gradleVersion.startsWith('7') ? '-1.3.3' : '',
                '/lib_builds_resolved_configurations-05.json')
        def lib2ResolvedConfigurationReportFile = findFile("*/lib2_builds_resolved_configurations.json")

        then:
        lib2ResolvedConfigurationReportFile != null
        def actualLib2JsonAsText = lib2ResolvedConfigurationReportFile.text
        JSONAssert.assertEquals(expectedLib2JsonAsText, actualLib2JsonAsText, false)

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.12'
        '6.9.2'         | '2.12'
        '7.3.3'         | '2.11'
    }
}