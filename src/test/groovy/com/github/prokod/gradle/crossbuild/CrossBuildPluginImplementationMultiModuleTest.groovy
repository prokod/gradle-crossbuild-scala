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

import org.gradle.testkit.runner.GradleRunner
import org.skyscreamer.jsonassert.JSONAssert
import org.xmlunit.diff.Diff
import spock.lang.Requires
import spock.lang.Unroll

import java.nio.file.Files

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginImplementationMultiModuleTest extends CrossBuildGradleRunnerSpec {

    File settingsFile
    File propsFile
    File buildFile
    File libBuildFile
    File libJavaFile
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
        libJavaFile = file('lib/src/main/java/HelloWorldLibImpl.java')
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
     *     <li>Plugin apply mode: Eager</li>
     *     <li>Running tasks that triggers main sourceset configurations: No</li>
     * </ul>
     * Leveraging layout 2 of propagating cross build configs to sub projects
     * Layout 2 means:
     * <ul>
     *     <li>root-project</li>
     *     <ul>
     *         <li>{@code plugins} DSL for crossbuild plugin</li>
     *         <li>{@code subprojects} block with {@code apply plugin:} {@link CrossBuildPlugin} followed by crossbuld plugin DSL</li>
     *     </ul>
     *     <li>sub-projects</li>
     *     <ul>
     *         <li>Nothing special</li>
     *     </ul>
     * </ul>
     * NOTES:
     * <ul>
     *     <li>Gradle build is called in this test with specific cross compile tasks only: 'crossBuildSpark160_210Jar', 'crossBuildSpark240_211Jar'.
     *     As a result, the main sourceset classpath configuration is not triggered to get resolved. Usual issues like scala-library is missing in 'app' module
     *     than do not pop up and so the 'user' do not need to use api configuration for implicit scala-library dependency inclusion, or to explicitly include
     *     scala-library dependency in all modules using implementation configuration as the plugin takes care of it for cross compile configurations.</li>
     * </ul>
     * @return
     */
    @Requires({ System.getProperty("java.version").startsWith('1.8') })
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project and calling crossBuildXXXJar tasks on it should build correctly"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
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

    crossBuild {
        
        scalaVersionsCatalog = ['2.11':'2.11.11']

        builds {
            spark160_210 
            spark240_211
        }
    }
    
    publishing {
        publications {
            crossBuildSpark160_210(MavenPublication) {
                afterEvaluate {
                    artifact crossBuildSpark160_210Jar
                }
            }
            crossBuildSpark240_211(MavenPublication) {
                afterEvaluate {
                    artifact crossBuildSpark240_211Jar
                }
            }
        }
    }

    tasks.withType(GenerateMavenPom) { t ->
        if (t.name.contains('CrossBuildSpark160_210')) {
            t.destination = file("\$buildDir/generated-pom_2.10.xml")
        }
        if (t.name.contains('CrossBuildSpark240_211')) {
            t.destination = file("\$buildDir/generated-pom_2.11.xml")
        }
    }
}
"""

        libScalaFile << """
import org.scalatest._

trait HelloWorldLibApi {
   def greet()
}
"""

        libJavaFile << """

public class HelloWorldLibImpl implements HelloWorldLibApi {
   public void greet() {
      System.out.println("Hello, world!");
   }
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
    implementation "org.scalatest:scalatest_?:3.0.1"
    implementation "com.google.guava:guava:18.0"
    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        appScalaFile << """
import HelloWorldLibImpl._

object HelloWorldApp {
   def main(args: Array[String]) {
      new HelloWorldLibImpl().greet()
   }
}
"""

        appBuildFile << """
dependencies {
    implementation project(':lib')
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('crossBuildSpark160_210Jar', 'crossBuildSpark240_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:crossBuildSpark160_210Jar").outcome == SUCCESS
        result.task(":lib:crossBuildSpark240_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark160_210Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark240_211Jar").outcome == SUCCESS

        fileExists(dir.resolve('lib/build/libs/lib_2.10*.jar'))
        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        fileExists(dir.resolve('app/build/libs/app_2.10*.jar'))
        fileExists(dir.resolve('app/build/libs/app_2.11*.jar'))

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.12'
        '6.9.4'         | '2.10'
        '7.6.1'         | '2.11'
    }

    /**
     * Leveraging layout 2 of propagating cross build configs to sub projects
     * Layout 2 means:
     * <ul>
     *     <li>root-project</li>
     *     <ul>
     *         <li>{@code plugins} DSL for crossbuild plugin</li>
     *
     *  <li>Running tasks that triggers main sourceset configurations: No</li>
     *         <li>{@code subprojects} block with {@code apply plugin:} {@link CrossBuildPlugin} followed by crossbuld plugin DSL</li>
     *     </ul>
     *     <li>sub-projects</li>
     *     <ul>
     *         <li>Nothing special</li>
     *     </ul>
     * </ul>
     *
     * Expects to throw exception regarding org.gradle.api.CircularReferenceException: Circular dependency between lib and app
     *
     * @return
     */
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with circular dependency should not stack overflow  <git issue #72>)"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
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

    crossBuild {
        
        scalaVersionsCatalog = ['2.11':'2.11.11']

        builds {
            spark160_210 
            spark240_211
        }
    }
}
"""

        libScalaFile << """
import org.scalatest._

trait HelloWorldLibApi {
   def greet()
}
"""

        libJavaFile << """

public class HelloWorldLibImpl implements HelloWorldLibApi {
   public void greet() {
      System.out.println("Hello, world!");
   }
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
    implementation "org.scalatest:scalatest_?:3.0.1"
    implementation "com.google.guava:guava:18.0"
    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"

    implementation project(':app')
}
"""

        appScalaFile << """
import HelloWorldLibImpl._

object HelloWorldApp {
   def main(args: Array[String]) {
      new HelloWorldLibImpl().greet()
   }
}
"""

        appBuildFile << """
dependencies {
    implementation project(':lib')
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('crossBuildSpark160_210Jar', 'crossBuildSpark240_211Jar', '--info', '--stacktrace')
                .build()

        then:
        org.gradle.testkit.runner.UnexpectedBuildFailure ex = thrown()
        ex.buildResult.output.contains('org.gradle.api.CircularReferenceException: Circular dependency')

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.11'
        '6.9.4'         | '2.11'
        '7.6.1'         | '2.12'
    }

    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin lazily on a multi-module project with dependency graph of depth 3 and with cross building dsl that is different on each submodule and with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
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
    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild') {
        if (!project.name.endsWith('app')) {
            crossBuild {
                scalaVersionsCatalog = ['2.12':'2.12.8']
                builds {
                    spark230_211 
                    spark240_212
                }
            }
        }
    }
    
    project.pluginManager.withPlugin('maven-publish') {
        if (!project.name.endsWith('app')) {
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
    crossBuildSpark230_211Implementation 'org.scala-lang:scala-reflect:2.11.12'
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
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

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
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":app:crossBuildSpark230_211Jar").outcome == SUCCESS

        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        fileExists(dir.resolve('lib/build/libs/lib_2.12*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.11*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.12*.jar'))
        fileExists(dir.resolve('app/build/libs/app_2.11*.jar'))
        !fileExists(dir.resolve('app/build/libs/app_2.12*.jar'))

        def pom211 = dir.resolve("lib${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def pom212 = dir.resolve("lib${File.separator}build${File.separator}generated-pom_2.12.xml").text

        !pom211.contains('2.12.')
        pom211.contains('2.11.12')
        pom211.contains('18.0')
        !pom212.contains('2.12.+')
        !pom212.contains('2.11.')
        pom212.contains('2.12.8')
        pom212.contains('18.0')

        def lib2pom211 = dir.resolve("lib2${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def lib2pom212 = dir.resolve("lib2${File.separator}build${File.separator}generated-pom_2.12.xml").text

        !lib2pom211.contains('2.12.')
        lib2pom211.contains('scala-reflect')
        lib2pom211.contains('2.11.12')
        lib2pom211.contains('1.0-SNAPSHOT')
        lib2pom211.contains('lib_2.11')
        !lib2pom212.contains('2.12.+')
        !lib2pom212.contains('2.11.')
        lib2pom212.contains('2.12.8')
        lib2pom212.contains('scala-reflect')
        lib2pom212.contains('1.0-SNAPSHOT')
        lib2pom212.contains('lib_2.12')

        def appPom211 = dir.resolve("app${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def appPom212Exist = Files.exists(dir.resolve("app${File.separator}build${File.separator}generated-pom_2.12.xml"))

        !appPom211.contains('2.12.')
        appPom211.contains('2.11.12')
        appPom211.contains('lib2_2.11')
        !appPom212Exist

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.12'
        '6.9.4'         | '2.12'
        '7.6.1'         | '2.11'
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Eager</li>
     * </ul>
     * This test is using 'implementation' configuration to express dependency graph between the modules.
     *
     * resource file/s for the test:
     * 04-app_builds_resolved_configurations.json
     * 04-pom_lib2-00.xml
     * 04-pom_app-00.xml
     */
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with cross building dsl that is different on each submodule and with publishing dsl, should produce expected jars and pom files and should have correct pom files content"() {
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
        def appPom211File = dir.resolve("app${File.separator}build${File.separator}generated-pom_2.11.xml")
        def appPom212File = dir.resolve("app${File.separator}build${File.separator}generated-pom_2.12.xml")

        then:
        Files.exists(appPom211File)
        !Files.exists(appPom212File)

        when:
        def expectedAppPom211 = loadResourceAsText( '/04-pom_app-00.xml')
        Diff dApp211 = pomDiffFor(expectedAppPom211, appPom211File.toFile())

        then:
        !dApp211.hasDifferences()

        where:
        gradleVersion | defaultScalaVersion
        '5.6.4'       | '2.11'
        '6.9.4'       | '2.12'
        '7.6.1'       | '2.11'
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Lazy</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x</li>
     * </ul>
     * Here lib3 is a non cross build dependency.
     * This test checks that when lib3 is added to dependent app module, while using 'implementation' configuration,
     * cross build compileScala scenario should not fail because of a missing lib3 project dependency in
     * compileClasspath
     *
     * @return
     */
    // todo add pom checks
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with cross building dsl that is applied only to some sub modules should produce expected: jars, pom files; and pom files content should be correct"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
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
    implementation 'org.scalaz:scalaz-core_${defaultScalaVersion}:7.2.28'
    crossBuildV210Implementation 'org.scalaz:scalaz-core_2.10:7.2.25'
    crossBuildSpark233_211Implementation 'org.scalaz:scalaz-core_2.11:7.2.26'
    crossBuildSpark242_212Implementation 'org.scalaz:scalaz-core_2.12:7.2.27'
    crossBuildSpark243_211Implementation 'org.scalaz:scalaz-core_2.11:7.2.28'
    crossBuildSpark243_212Implementation 'org.scalaz:scalaz-core_2.12:7.2.28'

    implementation 'org.scala-lang:scala-library:${defaultScalaVersion}.+'
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
//apply plugin: 'maven-publish'

dependencies {
  implementation 'org.apache.commons:commons-math3:3.6.1'
}
"""

        appScalaFile << """
object Test extends App {
    import Factorial._
    import CompileTimeFactorialUtils._

    println(normalFactorial(10))
    println(CompileTimeFactorialUtils.printHello)
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
    implementation project(':lib')
    implementation project(':lib3')

    implementation 'org.scala-lang:scala-library:${defaultScalaVersion}.+'
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('build', 'lib:crossBuildV210Jar', 'lib3:jar', 'app:crossBuildSpark233_211Jar', 'app:crossBuildResolvedConfigs', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:build").outcome == SUCCESS
        result.task(":lib3:build").outcome == SUCCESS
        result.task(":app:build").outcome == SUCCESS
        result.task(":app:crossBuildResolvedConfigs").outcome == SUCCESS
        result.task(":lib3:jar").outcome == SUCCESS
        result.task(":lib:crossBuildV210Jar").outcome == SUCCESS
        result.task(":lib:crossBuildSpark233_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark233_211Jar").outcome == SUCCESS

        // 'build' task should:
        fileExists(dir.resolve('lib/build/libs/lib-1.0-SNAPSHOT.jar'))
        fileExists(dir.resolve('lib3/build/libs/lib3-1.0-SNAPSHOT.jar'))
        fileExists(dir.resolve('app/build/libs/app-1.0-SNAPSHOT.jar'))

        // 'lib:crossBuildV210Jar', 'app:crossBuildSpark233_211Jar' tasks should:
        fileExists(dir.resolve('lib/build/libs/lib-legacy_2.10*.jar'))
        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        !fileExists(dir.resolve('lib/build/libs/lib_2.12*.jar'))
        fileExists(dir.resolve('app/build/libs/app-all_2.11*.jar'))
        !fileExists(dir.resolve('app/build/libs/app-all_2.12*.jar'))

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.12'
        '6.9.4'         | '2.12'
        '7.6.1'         | '2.11'
    }
}