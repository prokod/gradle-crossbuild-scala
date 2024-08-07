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

class CrossBuildPluginPomGenTest extends CrossBuildGradleRunnerSpec {
    File buildFile
    File propsFile
    File mavenLocalRepo

    def setup() {
        buildFile = file('build.gradle')
        propsFile = file('gradle.properties')
        mavenLocalRepo = directory('.m2')
    }

    /**
     * Here we check correctness of pom file content.
     * compileOnly configuration should not appear in the pom file as dependency.
     *
     * @return
     */
    @Requires({ instance.testMavenCentralAccess() })
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
            afterEvaluate {
                from components.crossBuildV210
            }
        }
        crossBuildV211(MavenPublication) {
            afterEvaluate {
                from components.crossBuildV211
            }
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
    implementation "org.scalatest:scalatest_?:3.0.1"
    implementation "com.google.guava:guava:18.0"
    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"

    compileOnly "org.apache.spark:spark-sql_${defaultScalaVersion}:${defaultScalaVersion == '2.10' ? '1.6.3' : '2.2.1'}"
    crossBuildV210CompileOnly "org.apache.spark:spark-sql_2.10:1.6.3"
    crossBuildV211CompileOnly "org.apache.spark:spark-sql_2.11:2.2.1"
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments("-Dmaven.repo.local=${mavenLocalRepo.absolutePath}", 'publishToMavenLocal', 'crossBuildResolvedConfigs', '--info', '--stacktrace')
                .build()

        then:
        result.task(":publishToMavenLocal").outcome == SUCCESS

        def pom210 = dir.resolve("build${File.separator}generated-pom_2.10.xml").text
        def pom211 = dir.resolve("build${File.separator}generated-pom_2.11.xml").text

        fileExists"*/.m2/*test_2.10*jar"
        fileExists"*/.m2/*test_2.11*jar"

        when:
        def project210 = new XmlSlurper().parseText(pom210).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        project210.size() == 3
        project210['org.scala-lang'].groupId == 'org.scala-lang'
        project210['org.scala-lang'].artifactId == 'scala-library'
        project210['org.scala-lang'].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.10']
        project210['org.scala-lang'].scope == 'runtime'
        project210['com.google.guava'].groupId == 'com.google.guava'
        project210['com.google.guava'].artifactId == 'guava'
        project210['com.google.guava'].version == '18.0'
        project210['com.google.guava'].scope == 'runtime'
        project210['org.scalatest'].groupId == 'org.scalatest'
        project210['org.scalatest'].artifactId == 'scalatest_2.10'
        project210['org.scalatest'].version == '3.0.1'
        project210['org.scalatest'].scope == 'runtime'

        when:
        def project211 = new XmlSlurper().parseText(pom211).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        project211.size() == 3
        project211['org.scalatest'].groupId == 'org.scalatest'
        project211['org.scalatest'].artifactId == 'scalatest_2.11'
        project211['org.scalatest'].version == '3.0.1'
        project211['org.scalatest'].scope == 'runtime'
        project211['org.scala-lang'].groupId == 'org.scala-lang'
        project211['org.scala-lang'].artifactId == 'scala-library'
        project211['org.scala-lang'].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        project211['org.scala-lang'].scope == 'runtime'
        project211['com.google.guava'].groupId == 'com.google.guava'
        project211['com.google.guava'].artifactId == 'guava'
        project211['com.google.guava'].version == '18.0'
        project211['com.google.guava'].scope == 'runtime'

        where:
        gradleVersion   | defaultScalaVersion
        '7.6.4'         | '2.10'
        '8.7'           | '2.11'
    }

    /**
     * Here we check correctness of pom file content when using pom.withXml.
     * compileOnly configuration should not appear in the pom file as dependency for scala 2.11
     * compileOnly configuration should appear in the pom file as provided scope for scala 2.10, according to
     * pom.withXml block
     *
     * @return
     */
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin with publishing dsl and custom withXml handler should  produce expected pom files and their content should be correct"() {
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
        scala {
            scalaVersions = ['2.10', '2.11']
        }
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
        crossBuildScala_210(MavenPublication) {
            afterEvaluate {
                from components.crossBuildScala_210

                pom.withXml { xml ->
                    def dependenciesNode = xml.asNode().dependencies?.getAt(0)

                    def dependencyNode = dependenciesNode.appendNode('dependency')
                    dependencyNode.appendNode('groupId', 'org.apache.spark')
                    dependencyNode.appendNode('artifactId', 'spark-sql_2.10')
                    dependencyNode.appendNode('version', '1.6.3')
                    dependencyNode.appendNode('scope', 'provided')
                }
            }
        }
        crossBuildScala_211(MavenPublication) {
            afterEvaluate {
                from components.crossBuildScala_211
            }
        }
    }
}

tasks.withType(GenerateMavenPom) { t ->
    if (t.name.contains('CrossBuildScala_210')) {
        t.destination = file("\$buildDir/generated-pom_2.10.xml")
    }
    if (t.name.contains('CrossBuildScala_211')) {
        t.destination = file("\$buildDir/generated-pom_2.11.xml")
    }
}

dependencies {
    implementation "org.scalatest:scalatest_?:3.0.1"
    implementation "com.google.guava:guava:18.0"
    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"

    compileOnly "org.apache.spark:spark-sql_${defaultScalaVersion}:${defaultScalaVersion == '2.10' ? '1.6.3' : '2.2.1'}"
    crossBuildScala_210CompileOnly "org.apache.spark:spark-sql_2.10:1.6.3"
    crossBuildScala_211CompileOnly "org.apache.spark:spark-sql_2.11:2.2.1"
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments("-Dmaven.repo.local=${mavenLocalRepo.absolutePath}", 'publishToMavenLocal', 'crossBuildResolvedConfigs', '--info', '--stacktrace')
                .build()

        then:
        result.task(":publishToMavenLocal").outcome == SUCCESS

        def pom210 = dir.resolve("build${File.separator}generated-pom_2.10.xml").text
        def pom211 = dir.resolve("build${File.separator}generated-pom_2.11.xml").text

        fileExists"*/.m2/*test_2.10*jar"
        fileExists"*/.m2/*test_2.11*jar"

        when:
        def project210 = new XmlSlurper().parseText(pom210).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        project210.size() == 4
        project210['org.scala-lang'].groupId == 'org.scala-lang'
        project210['org.scala-lang'].artifactId == 'scala-library'
        project210['org.scala-lang'].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.10']
        project210['org.scala-lang'].scope == 'runtime'
        project210['org.apache.spark'].groupId == 'org.apache.spark'
        project210['org.apache.spark'].artifactId == 'spark-sql_2.10'
        project210['org.apache.spark'].version == '1.6.3'
        project210['org.apache.spark'].scope == 'provided'
        project210['com.google.guava'].groupId == 'com.google.guava'
        project210['com.google.guava'].artifactId == 'guava'
        project210['com.google.guava'].version == '18.0'
        project210['com.google.guava'].scope == 'runtime'
        project210['org.scalatest'].groupId == 'org.scalatest'
        project210['org.scalatest'].artifactId == 'scalatest_2.10'
        project210['org.scalatest'].version == '3.0.1'
        project210['org.scalatest'].scope == 'runtime'

        when:
        def project211 = new XmlSlurper().parseText(pom211).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        project211.size() == 3
        project211['org.scalatest'].groupId == 'org.scalatest'
        project211['org.scalatest'].artifactId == 'scalatest_2.11'
        project211['org.scalatest'].version == '3.0.1'
        project211['org.scalatest'].scope == 'runtime'
        project211['org.scala-lang'].groupId == 'org.scala-lang'
        project211['org.scala-lang'].artifactId == 'scala-library'
        project211['org.scala-lang'].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        project211['org.scala-lang'].scope == 'runtime'
        project211['com.google.guava'].groupId == 'com.google.guava'
        project211['com.google.guava'].artifactId == 'guava'
        project211['com.google.guava'].version == '18.0'
        project211['com.google.guava'].scope == 'runtime'

        where:
        gradleVersion   | defaultScalaVersion
        '7.6.4'         | '2.11'
        '8.7'           | '2.10'
    }

    /**
     * Here we check correctness of pom file content.
     * <ul>
     *     <li>api configuration should appear in the pom file as compile scope for both scala 2.12/3</li>
     *     <li>scalaVersions = [scala2_12, scala2_13] defined on dsl plugin level are the ones that will also appear in pom</li>
     * </ul>
     *
     * @see <a href="https://github.com/prokod/gradle-crossbuild-scala/issues/128">issue #128</a>
     *
     */
    @Requires({ instance.testMavenCentralAccess() })
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin with publishing dsl and api configuration should produce expected pom files and their content should be correct"() {
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

def scala2_13 = '2.13.10'
def scala2_12 = '2.12.17'

crossBuild {
    builds {
        scala {
            scalaVersions = [scala2_12, scala2_13]
        }
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
        crossBuildScala_212(MavenPublication) {
            afterEvaluate {
                from components.crossBuildScala_212
            }
        }
        crossBuildScala_213(MavenPublication) {
            afterEvaluate {
                from components.crossBuildScala_213
            }
        }
    }
}

tasks.withType(GenerateMavenPom) { t ->
    if (t.name.contains('CrossBuildScala_212')) {
        t.destination = file("\$buildDir/generated-pom_2.12.xml")
    }
    if (t.name.contains('CrossBuildScala_213')) {
        t.destination = file("\$buildDir/generated-pom_2.13.xml")
    }
}

dependencies {
    api "io.github.cquiroz:scala-java-time_$defaultScalaVersion:2.5.0"

    implementation "org.scala-lang:scala-library:$defaultScalaCompilerVersion"
    implementation "io.circe:circe-core_$defaultScalaVersion:0.14.2"
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments("-Dmaven.repo.local=${mavenLocalRepo.absolutePath}", 'publishToMavenLocal', 'crossBuildResolvedConfigs', '--info', '--stacktrace')
                .build()

        then:
        result.task(":publishToMavenLocal").outcome == SUCCESS

        def pom212 = dir.resolve("build${File.separator}generated-pom_2.12.xml").text
        def pom213 = dir.resolve("build${File.separator}generated-pom_2.13.xml").text

        fileExists"*/.m2/*test_2.12*jar"
        fileExists"*/.m2/*test_2.13*jar"

        when:
        def project212 = new XmlSlurper().parseText(pom212).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        project212.size() == 3
        project212['org.scala-lang'].groupId == 'org.scala-lang'
        project212['org.scala-lang'].artifactId == 'scala-library'
        project212['org.scala-lang'].version == '2.12.17'
        project212['org.scala-lang'].scope == 'runtime'
        project212['io.github.cquiroz'].groupId == 'io.github.cquiroz'
        project212['io.github.cquiroz'].artifactId == 'scala-java-time_2.12'
        project212['io.github.cquiroz'].version == '2.5.0'
        project212['io.github.cquiroz'].scope == 'compile'
        project212['io.circe'].groupId == 'io.circe'
        project212['io.circe'].artifactId == 'circe-core_2.12'
        project212['io.circe'].version == '0.14.2'
        project212['io.circe'].scope == 'runtime'

        when:
        def project213 = new XmlSlurper().parseText(pom213).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        project213.size() == 3
        project213['org.scala-lang'].groupId == 'org.scala-lang'
        project213['org.scala-lang'].artifactId == 'scala-library'
        project213['org.scala-lang'].version == '2.13.10'
        project213['org.scala-lang'].scope == 'runtime'
        project213['io.github.cquiroz'].groupId == 'io.github.cquiroz'
        project213['io.github.cquiroz'].artifactId == 'scala-java-time_2.13'
        project213['io.github.cquiroz'].version == '2.5.0'
        project213['io.github.cquiroz'].scope == 'compile'
        project213['io.circe'].groupId == 'io.circe'
        project213['io.circe'].artifactId == 'circe-core_2.13'
        project213['io.circe'].version == '0.14.2'
        project213['io.circe'].scope == 'runtime'

        where:
        gradleVersion   | defaultScalaVersion | defaultScalaCompilerVersion
        '7.6.4'         | '2.12'              | '2.12.19'
        '8.7'           | '2.13'              | '2.13.13'
    }
}