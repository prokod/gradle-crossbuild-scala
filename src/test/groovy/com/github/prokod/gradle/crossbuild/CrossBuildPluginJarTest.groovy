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
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginJarTest extends CrossBuildGradleRunnerSpec {

    File buildFile
    File propsFile
    File scalaFile
    File javaFile
    File testScalaFile

    def setup() {
        buildFile = file('build.gradle')
        propsFile = file('gradle.properties')
        scalaFile = file('src/main/scala/HelloWorldA.scala')
        javaFile = file('src/main/java/HelloWorldB.java')
        testScalaFile = file('src/test/scala/helloWorldTest.scala')
    }

    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin with crossBuild dsl should produce expected jars"() {
        given:
        scalaFile << """
import org.scalatest._

object HelloWorldA {
   /* This is my first java program.  
   * This will print 'Hello World' as the output
   */
   def main(args: Array[String]) {
      println("Hello, world A!")
   }
   
   def runIt() {
         println("Visit, world B!")
   }
}
"""

        javaFile << """

public class HelloWorldB {
   /* This is my first java program.  
   * This will print 'Hello World' as the output
   */
   public static void main(String[] args) {
      HelloWorldA.runIt();
   }
}
"""

        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
    id 'maven-publish'
}

group = 'com.github.prokod.it'

repositories {
    mavenCentral()
}

crossBuild {
    builds {
        v210
        v211
    }
}

publishing {
    publications {
        crossBuildV210(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildV210Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
        crossBuildV211(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildV211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
    
tasks.withType(GenerateMavenPom) { t ->
    if (t.name.contains('CrossBuildV210')) {
        t.destination = file("\$buildDir/generated-pom_2.10.xml")
    }
    if (t.name.contains('CrossBuildV211')) {
        t.destination = file("\$buildDir/generated-pom_2.11.xml")
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
    compile "org.scalatest:scalatest_?:3.0.1"
    compile "com.google.guava:guava:18.0"
    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments('crossBuildV210Jar', 'crossBuildV211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":crossBuildV210Jar").outcome == SUCCESS
        result.task(":crossBuildV211Jar").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/build/libs/junit*_2.10.jar")
        fileExists("$dir.root.absolutePath/build/libs/junit*_2.11.jar")

        where:
        gradleVersion   | defaultScalaVersion
        '4.10.3'        | '2.10'
        '5.6.4'         | '2.11'
        '6.0.1'         | '2.11'
    }

    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin with java-library configurations should create cross built jars"() {
        given:
        scalaFile << """
object Lib {
  val book: scala.xml.Elem = <book id="b20234">Magic of scala-xml</book>

  val id = book \\@ "id"
  //id: String = b20234

  val text = book.text
  //text: String = Magic of scala-xml
}
"""

        testScalaFile << """
import java.util.function._
import scala.compat.java8.FunctionConverters._

object Test {
  val foo: Int => Boolean = i => i > 7
  def testBig(ip: IntPredicate) = ip.test(9)
  println(testBig(foo.asJava))  // Prints true

  val bar = new UnaryOperator[String]{ def apply(s: String) = s.reverse }
  List("cod", "herring").map(bar.asScala)    // List("doc", "gnirrih")

  def testA[A](p: Predicate[A])(a: A) = p.test(a)
  println(testA(asJavaPredicate(foo))(4))  // Prints false
}
"""

        buildFile << """
plugins {
    id 'java-library'
    id 'com.github.prokod.gradle-crossbuild'
}

repositories {
    mavenCentral()
}

crossBuild {
    scalaVersionsCatalog = ['2.11':'2.11.12', '2.12':'2.12.8']

    builds {
        v211
        v212
    }
}

dependencies {
    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"
    implementation 'org.scala-lang.modules:scala-xml_?:[1.0, 2.0['

    testImplementation 'org.scala-lang.modules:scala-java8-compat_?:[0.9,1.0['
    testImplementation "org.junit.jupiter:junit-jupiter-api:5.3.2"
    testImplementation "org.junit.jupiter:junit-jupiter-params:5.3.2"
    testImplementation "org.mockito:mockito-core:2.23.4"
    testImplementation "org.mockito:mockito-junit-jupiter:2.23.4"
    testImplementation "org.assertj:assertj-core:3.11.1"

    testRuntimeOnly "org.junit.jupiter:junit-jupiter-engine:5.3.2"
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments('crossBuildResolvedConfigs', 'crossBuildV211Jar', 'crossBuildV212Jar', 'check', '--info', '--stacktrace')
                .build()

        then:
        result.task(":test").outcome == SUCCESS
        result.task(":crossBuildV211Jar").outcome == SUCCESS
        result.task(":crossBuildV212Jar").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/build/libs/junit*_2.11.jar")
        fileExists("$dir.root.absolutePath/build/libs/junit*_2.12.jar")
        where:
        gradleVersion   | defaultScalaVersion
        '4.10.3'        | '2.11'
        '5.6.4'         | '2.12'
        '6.0.1'         | '2.12'
    }
}