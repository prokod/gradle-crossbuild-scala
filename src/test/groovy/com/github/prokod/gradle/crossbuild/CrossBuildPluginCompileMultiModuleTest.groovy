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

class CrossBuildPluginCompileMultiModuleTest extends CrossBuildGradleRunnerSpec {

    File settingsFile
    File propsFile
    File buildFile
    File libBuildFile
    File libScalaFile
    File libJavaFile
    File appBuildFile
    File appScalaFile

    def setup() {
        settingsFile = file('settings.gradle')
        propsFile = file('gradle.properties')
        buildFile = file('build.gradle')
        libBuildFile = file('lib/build.gradle')
        libScalaFile = file('lib/src/main/scala/HelloWorldLibApi.scala')
        libJavaFile = file('lib/src/main/java/HelloWorldLibImpl.java')
        appBuildFile = file('mapp/build.gradle')
        appScalaFile = file('mapp/src/main/scala/HelloWorldApp.scala')
    }

    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
include 'mapp'
"""

        buildFile << """

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
import com.github.prokod.gradle.crossbuild.model.*

plugins {
    id 'com.github.prokod.gradle-crossbuild'
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
                pom.withXml {
                    def dependenciesNode = asNode().appendNode("dependencies")

                    if (dependenciesNode != null) {
                        configurations.crossBuild210MavenCompileScope.allDependencies.each { dep ->
                            def dependencyNode = dependenciesNode.appendNode('dependency')
                            dependencyNode.appendNode('groupId', dep.group)
                            dependencyNode.appendNode('artifactId', dep.name)
                            dependencyNode.appendNode('version', dep.version)
                            dependencyNode.appendNode('scope', 'runtime')
                        }
                    }
                }
            }
            crossBuild211(MavenPublication) {
                groupId = project.group
                artifactId = \$.crossBuild.targetVersions.v211.artifactId
                artifact \$.tasks.crossBuild211Jar
                pom.withXml {
                    def dependenciesNode = asNode().appendNode("dependencies")

                    if (dependenciesNode != null) {
                        configurations.crossBuild211MavenCompileScope.allDependencies.each { dep ->
                            def dependencyNode = dependenciesNode.appendNode('dependency')
                            dependencyNode.appendNode('groupId', dep.group)
                            dependencyNode.appendNode('artifactId', dep.name)
                            dependencyNode.appendNode('version', dep.version)
                            dependencyNode.appendNode('scope', 'runtime')
                        }
                    }
                }
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

        appScalaFile << """
import HelloWorldLibImpl._

object HelloWorldApp {
   def main(args: Array[String]) {
      new HelloWorldLibImpl().greet()
   }
}
"""

        appBuildFile << """
import com.github.prokod.gradle.crossbuild.model.*

plugins {
    id 'com.github.prokod.gradle-crossbuild'
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
}

dependencies {
    compile project(':lib')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withDebug(true)
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments('build', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:publishToMavenLocal").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.10.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11.jar")
        fileExists("$dir.root.absolutePath/mapp/build/libs/mapp_2.10.jar")
        fileExists("$dir.root.absolutePath/mapp/build/libs/mapp_2.11.jar")

        def pom210 = new File("${dir.root.absolutePath}${File.separator}lib${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def pom211 = new File("${dir.root.absolutePath}${File.separator}lib${File.separator}build${File.separator}generated-pom_2.11.xml").text

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
        [gradleVersion, defaultScalaVersion] << [['2.14.1', '2.10'], ['3.0', '2.10'], ['4.1', '2.10'], ['2.14.1', '2.11'], ['3.0', '2.11'], ['4.1', '2.11']]
    }
}