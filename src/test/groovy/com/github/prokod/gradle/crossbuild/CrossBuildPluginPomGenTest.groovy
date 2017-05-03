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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginPomGenTest extends Specification {
    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    File propsFile

    def testMavenCentralAccess() {
        URL u = new URL ( "https://repo1.maven.org/maven2/")
        HttpURLConnection huc =  ( HttpURLConnection )  u.openConnection ()
        huc.setRequestMethod ("HEAD")
        huc.connect ()
        huc.getResponseCode() == HttpURLConnection.HTTP_OK
    }

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        propsFile = testProjectDir.newFile('gradle.properties')
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with publishing dsl should produce expected pom files and their content should be correct"() {
        given:
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

dependencies {
    compile "com.google.guava:guava:18.0"
    compile "org.scala-lang:scala-library:2.11.+"
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":publishToMavenLocal").outcome == SUCCESS
        def pom210 = new File("${testProjectDir.root.absolutePath}${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def pom211 = new File("${testProjectDir.root.absolutePath}${File.separator}build${File.separator}generated-pom_2.11.xml").text

        !pom210.contains('2.11.+')
        pom210.contains('2.10.6')
        pom210.contains('18.0')
        !pom211.contains('2.11.+')
        pom211.contains('2.11.8')
        pom211.contains('18.0')
        where:
        gradleVersion << ['2.14.1', '3.0']
    }
}