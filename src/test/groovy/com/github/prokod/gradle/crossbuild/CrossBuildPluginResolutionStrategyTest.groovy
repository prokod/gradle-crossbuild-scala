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

class CrossBuildPluginResolutionStrategyTest extends CrossBuildGradleRunnerSpec {
    File buildFile
    File propsFile
    File scalaFile
    File testScalaFile

    def setup() {
        buildFile = file('build.gradle')
        propsFile = file('gradle.properties')
        scalaFile = file('src/main/scala/helloWorld.scala')
        testScalaFile = file('src/test/scala/helloWorldTest.scala')
    }

    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin with publishing dsl should produce expected pom files and their content should be correct"() {
        given:
        scalaFile << """
import org.scalatest._

object HelloWorld {
   /* This is my first java program.  
   * This will print 'Hello World' as the output
   */
   def main(args: Array[String]) {
      println("Hello, world!") // prints Hello World
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
    scalaVersionsCatalog = ['2.10':'2.10.6', '2.11':'2.11.11'] 

    builds {
        v210 {
            scalaVersions = ['2.10']
        }
        v211 {
            scalaVersions = ['2.11']
        }
    }
}

publishing {
    publications {
        crossBuildV210(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildV210Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}        }
        crossBuildV211(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildV211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}        }
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

dependencies {
    compile "org.scalatest:scalatest_?:3.0.1"
    compile "com.google.guava:guava:18.0"
    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"
    // flink-connector-kafka-0.10 dependency is only built for Scala 2.11
    // In this case, when cross building this project to Scala 2.10, that dependency should be excluded
    //  from 'crossBuild210Compile' configurations which inherit from 'compile'. 
    compile 'org.apache.flink:flink-connector-kafka-0.10_2.11:1.4.1'
    crossBuildV210Compile 'org.apache.flink:flink-connector-kafka-0.10_2.10:1.3.2'
}


"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('build', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":publishToMavenLocal").outcome == SUCCESS
        def pom210 = new File("${dir.root.absolutePath}${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def pom211 = new File("${dir.root.absolutePath}${File.separator}build${File.separator}generated-pom_2.11.xml").text

        !pom210.contains('2.11.')
        pom210.contains('2.10.6')
        pom210.contains('18.0')
        pom210.contains('3.0.1')
        !pom211.contains('2.11.+')
        !pom211.contains('2.10.')
        pom211.contains('2.11.11')
        pom211.contains('18.0')
        pom211.contains('3.0.1')

        where:
        gradleVersion | defaultScalaVersion
        '4.2'         | '2.10'
        '4.10.3'      | '2.11'
        '5.4.1'       | '2.11'
    }

    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin with qmark deps in test configuration should be resolved correctly"() {
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
        v211 {
            scalaVersions = ['2.11']
        }
        v212 {
            scalaVersions = ['2.12']
        }
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
                .withArguments('build', 'check', '--info', '--stacktrace')
                .build()

        then:
        result.task(":test").outcome == SUCCESS
        result.task(":build").outcome == SUCCESS

        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.11'
        '4.10.3'        | '2.12'
        '5.4.1'         | '2.12'
    }
}