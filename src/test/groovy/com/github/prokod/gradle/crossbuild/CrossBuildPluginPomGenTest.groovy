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

class CrossBuildPluginPomGenTest extends CrossBuildGradleRunnerSpec {
    File buildFile
    File propsFile

    def setup() {
        buildFile = file('build.gradle')
        propsFile = file('gradle.properties')
    }

    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin with publishing dsl should produce expected pom files and their content should be correct"() {
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

dependencies {
    compile "org.scalatest:scalatest_?:3.0.1"
    compile "com.google.guava:guava:18.0"
    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"

    compileOnly "org.apache.spark:spark-sql_${defaultScalaVersion}:${defaultScalaVersion == '2.10' ? '1.6.3' : '2.2.1'}"
    crossBuild210CompileOnly "org.apache.spark:spark-sql_2.10:1.6.3"
    crossBuild211CompileOnly "org.apache.spark:spark-sql_2.11:2.2.1"
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withDebug(true)
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":publishToMavenLocal").outcome == SUCCESS
        def pom210 = new File("${dir.root.absolutePath}${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def pom211 = new File("${dir.root.absolutePath}${File.separator}build${File.separator}generated-pom_2.11.xml").text

        def project210 = new XmlSlurper().parseText(pom210)
        project210.dependencies.dependency.size() == 4
        project210.dependencies.dependency[0].groupId == 'org.scala-lang'
        project210.dependencies.dependency[0].artifactId == 'scala-library'
        project210.dependencies.dependency[0].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.10']
        project210.dependencies.dependency[0].scope == 'compile'
        project210.dependencies.dependency[1].groupId == 'com.google.guava'
        project210.dependencies.dependency[1].artifactId == 'guava'
        project210.dependencies.dependency[1].version == '18.0'
        project210.dependencies.dependency[1].scope == 'compile'
        project210.dependencies.dependency[2].groupId == 'org.scalatest'
        project210.dependencies.dependency[2].artifactId == 'scalatest_2.10'
        project210.dependencies.dependency[2].version == '3.0.1'
        project210.dependencies.dependency[2].scope == 'compile'
        project210.dependencies.dependency[3].groupId == 'org.apache.spark'
        project210.dependencies.dependency[3].artifactId == 'spark-sql_2.10'
        project210.dependencies.dependency[3].version == '1.6.3'
        project210.dependencies.dependency[3].scope == 'provided'

        def project211 = new XmlSlurper().parseText(pom211)
        project211.dependencies.dependency.size() == 4
        project211.dependencies.dependency[0].groupId == 'org.scala-lang'
        project211.dependencies.dependency[0].artifactId == 'scala-library'
        project211.dependencies.dependency[0].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        project211.dependencies.dependency[0].scope == 'compile'
        project211.dependencies.dependency[1].groupId == 'com.google.guava'
        project211.dependencies.dependency[1].artifactId == 'guava'
        project211.dependencies.dependency[1].version == '18.0'
        project211.dependencies.dependency[1].scope == 'compile'
        project211.dependencies.dependency[2].groupId == 'org.scalatest'
        project211.dependencies.dependency[2].artifactId == 'scalatest_2.11'
        project211.dependencies.dependency[2].version == '3.0.1'
        project211.dependencies.dependency[2].scope == 'compile'
        project211.dependencies.dependency[3].groupId == 'org.apache.spark'
        project211.dependencies.dependency[3].artifactId == 'spark-sql_2.11'
        project211.dependencies.dependency[3].version == '2.2.1'
        project211.dependencies.dependency[3].scope == 'provided'

        where:
        [gradleVersion, defaultScalaVersion] << [['2.14.1', '2.10'], ['3.0', '2.10'], ['4.1', '2.10'], ['2.14.1', '2.11'], ['3.0', '2.11'], ['4.1', '2.11']]
    }
}