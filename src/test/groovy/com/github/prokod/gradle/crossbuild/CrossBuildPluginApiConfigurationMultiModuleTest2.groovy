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
import spock.lang.Requires
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginApiConfigurationMultiModuleTest2 extends CrossBuildGradleRunnerSpec {

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
     *     <li>Plugin apply mode: Eager</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x</li>
     *     <li>Running tasks that triggers main sourceset configurations: Yes</li>
     * </ul>
     * @return
     */
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] should allow a project without crossbuild plugin to resolve the right dependencies"() {
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
apply plugin: 'com.github.prokod.gradle-crossbuild'

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

crossBuild {
    builds {
        v211 
        v212
    }
}

dependencies {
    implementation "org.scalatest:scalatest_?:3.0.1"
    implementation "com.google.guava:guava:18.0"
    api "org.scala-lang:scala-library:${defaultScalaVersion}.+"
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
apply plugin: 'scala'

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
            .withArguments('crossBuildV212Jar', 'crossBuildV211Jar', 'build', '--info', '--stacktrace')
            .build()

        then:
        result.task(":lib:crossBuildV212Jar").outcome == SUCCESS
        result.task(":lib:crossBuildV211Jar").outcome == SUCCESS
        result.task(":lib:build").outcome == SUCCESS
        result.task(":app:build").outcome == SUCCESS
        where:
        gradleVersion | defaultScalaVersion
        '5.6.4'       | '2.11'
        '6.9.4'       | '2.12'
        '7.6.1'       | '2.11'
    }

    /**
     * This test is a variant of the test using resource app_builds_resolved_configurations-00.json
     * In this test we use scalaz as the external 3rd party scala lib (qMarked) dependency the plugin
     * should resolve correctly.
     *
     * @return
     */
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion | question-marked-scalaz-lib-dependency ? #scalazQmarked] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with cross building dsl that is different on each submodule and with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
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
            scalaVersionsCatalog = ['2.12':'2.12.8']
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
    ${scalazQmarked ? "api 'org.scalaz:scalaz-core_?:7.2.28'" : "api 'org.scalaz:scalaz-core_2.12:7.2.28'\ncrossBuildSpark230_211Api 'org.scalaz:scalaz-core_2.11:7.2.28'"}

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
                .withArguments('app:crossBuildSpark230_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":app:crossBuildSpark230_211Jar").outcome == SUCCESS

        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        !fileExists(dir.resolve('lib/build/libs/lib_2.12*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.11*.jar'))
        !fileExists(dir.resolve('lib2/build/libs/lib2_2.12*.jar'))
        fileExists(dir.resolve('app/build/libs/app_2.11*.jar'))
        !fileExists(dir.resolve('app/build/libs/app_2.12*.jar'))

        where:
        gradleVersion   | defaultScalaVersion | scalazQmarked
        '5.6.4'         | '2.12'              | false
        '6.9.4'         | '2.12'              | false
        '7.6.1'         | '2.11'              | false
        '5.6.4'         | '2.12'              | true
        '6.9.4'         | '2.12'              | true
        '7.6.1'         | '2.11'              | true
    }

    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with cross building dsl that is different on each submodule and individual appendixPattern with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
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
crossBuild {
    builds {
        v210 {
            archive {
                appendixPattern = '-legacy_?'
            }
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
    api 'org.scalaz:scalaz-core_?:7.2.28'

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
crossBuild {
    builds {
        spark230_211 {
            archive {
                appendixPattern = '-all_?'
            }
        }
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
                .withArguments('crossBuildResolvedConfigs', 'lib:crossBuildV210Jar', 'app:crossBuildSpark230_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:crossBuildV210Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark230_211Jar").outcome == SUCCESS

        fileExists(dir.resolve('lib/build/libs/lib-legacy_2.10*.jar'))
        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        !fileExists(dir.resolve('lib/build/libs/lib_2.12*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.11*.jar'))
        !fileExists(dir.resolve('lib2/build/libs/lib2_2.12*.jar'))
        fileExists(dir.resolve('app/build/libs/app-all_2.11*.jar'))
        !fileExists(dir.resolve('app/build/libs/app-all_2.12*.jar'))

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.12'
        '6.9.4'         | '2.12'
        '7.6.1'         | '2.11'
    }
}