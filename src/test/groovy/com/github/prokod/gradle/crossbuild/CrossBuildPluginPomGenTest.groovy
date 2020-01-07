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
    File mavenLocalRepo

    def setup() {
        buildFile = file('build.gradle')
        propsFile = file('gradle.properties')
        mavenLocalRepo = directory('.m2')
    }

    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin with publishing dsl should produce expected pom files and their content should be correct"() {
        given:
        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
    id 'maven-publish'
}

group = 'com.github.prokod.it'
version = '1.0'
archivesBaseName = 'test'

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
        maven(MavenPublication) {
            groupId = 'org.gradle.sample'
            artifactId = 'project1-sample'
            version = '1.1'

            from components.java
        }
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

dependencies {
    compile "org.scalatest:scalatest_?:3.0.1"
    compile "com.google.guava:guava:18.0"
    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"

    compileOnly "org.apache.spark:spark-sql_${defaultScalaVersion}:${defaultScalaVersion == '2.10' ? '1.6.3' : '2.2.1'}"
    crossBuildV210CompileOnly "org.apache.spark:spark-sql_2.10:1.6.3"
    crossBuildV211CompileOnly "org.apache.spark:spark-sql_2.11:2.2.1"
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withDebug(false)
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments("-Dmaven.repo.local=${mavenLocalRepo.absolutePath}", 'publishToMavenLocal', 'crossBuildResolvedConfigs', '--info', '--stacktrace')
                .build()

        then:
        result.task(":publishToMavenLocal").outcome == SUCCESS

        def pom210 = new File("${dir.root.absolutePath}${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def pom211 = new File("${dir.root.absolutePath}${File.separator}build${File.separator}generated-pom_2.11.xml").text

        fileExists"*/.m2/*test_2.10*jar"
        fileExists"*/.m2/*test_2.11*jar"

        when:
        def project210 = new XmlSlurper().parseText(pom210)

        then:
        project210.dependencies.dependency.size() == 4
        project210.dependencies.dependency[0].groupId == 'org.scala-lang'
        project210.dependencies.dependency[0].artifactId == 'scala-library'
        project210.dependencies.dependency[0].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.10']
        project210.dependencies.dependency[0].scope == 'compile'
        project210.dependencies.dependency[1].groupId == 'org.apache.spark'
        project210.dependencies.dependency[1].artifactId == 'spark-sql_2.10'
        project210.dependencies.dependency[1].version == '1.6.3'
        project210.dependencies.dependency[1].scope == 'compile'
        project210.dependencies.dependency[2].groupId == 'com.google.guava'
        project210.dependencies.dependency[2].artifactId == 'guava'
        project210.dependencies.dependency[2].version == '18.0'
        project210.dependencies.dependency[2].scope == 'compile'
        project210.dependencies.dependency[3].groupId == 'org.scalatest'
        project210.dependencies.dependency[3].artifactId == 'scalatest_2.10'
        project210.dependencies.dependency[3].version == '3.0.1'
        project210.dependencies.dependency[3].scope == 'compile'

        when:
        def project211 = new XmlSlurper().parseText(pom211)

        then:
        project211.dependencies.dependency.size() == 4
        project211.dependencies.dependency[0].groupId == 'org.scalatest'
        project211.dependencies.dependency[0].artifactId == 'scalatest_2.11'
        project211.dependencies.dependency[0].version == '3.0.1'
        project211.dependencies.dependency[0].scope == 'compile'
        project211.dependencies.dependency[1].groupId == 'org.apache.spark'
        project211.dependencies.dependency[1].artifactId == 'spark-sql_2.11'
        project211.dependencies.dependency[1].version == '2.2.1'
        project211.dependencies.dependency[1].scope == 'compile'
        project211.dependencies.dependency[2].groupId == 'org.scala-lang'
        project211.dependencies.dependency[2].artifactId == 'scala-library'
        project211.dependencies.dependency[2].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        project211.dependencies.dependency[2].scope == 'compile'
        project211.dependencies.dependency[3].groupId == 'com.google.guava'
        project211.dependencies.dependency[3].artifactId == 'guava'
        project211.dependencies.dependency[3].version == '18.0'
        project211.dependencies.dependency[3].scope == 'compile'

        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.10'
        '4.10.3'        | '2.11'
        '5.4.1'         | '2.10'
    }
}