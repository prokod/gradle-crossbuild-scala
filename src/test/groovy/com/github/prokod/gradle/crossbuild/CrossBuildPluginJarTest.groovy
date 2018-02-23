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

    def setup() {
        buildFile = file('build.gradle')
        propsFile = file('gradle.properties')
        scalaFile = file('src/main/scala/HelloWorldA.scala')
        javaFile = file('src/main/java/HelloWorldB.java')
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
import com.github.prokod.gradle.crossbuild.model.*

plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

group = 'com.github.prokod.it'

repositories {
    mavenCentral()
}

model {
    crossBuild {
        targetVersions {
            v210(ScalaVer) {
                value = '2.10'
            }
            v211(ScalaVer) {
                value = '2.11'
            }
        }
    }
    
    publishing {
        publications {
            crossBuild210(MavenPublication) {
                groupId = project.group
                artifactId = \$.crossBuild.targetVersions.v210.artifactId
                artifact \$.tasks.crossBuild210Jar
            }
            crossBuild211(MavenPublication) {
                groupId = project.group
                artifactId = \$.crossBuild.targetVersions.v211.artifactId
                artifact \$.tasks.crossBuild211Jar
            }
        }
    }
    
    tasks.generatePomFileForCrossBuild210Publication {
        destination = file("\$buildDir/generated-pom_2.10.xml")
    }
    
    tasks.generatePomFileForCrossBuild211Publication {
        destination = file("\$buildDir/generated-pom_2.11.xml")
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
                .withArguments('crossBuild210Jar', 'crossBuild211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":crossBuild210Jar").outcome == SUCCESS
        result.task(":crossBuild211Jar").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/build/libs/junit*_2.10.jar")
        fileExists("$dir.root.absolutePath/build/libs/junit*_2.11.jar")

        where:
        [gradleVersion, defaultScalaVersion] << [['2.14.1', '2.10'], ['3.0', '2.10'], ['4.1', '2.10'], ['2.14.1', '2.11'], ['3.0', '2.11'], ['4.1', '2.11']]
    }
}