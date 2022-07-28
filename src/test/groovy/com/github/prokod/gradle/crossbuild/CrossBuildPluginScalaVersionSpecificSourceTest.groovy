/*
 * Copyright 2020-2022 the original author or authors
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
import org.gradle.internal.impldep.org.junit.Assume
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginScalaVersionSpecificSourceTest extends CrossBuildGradleRunnerSpec {
    File settingsFile
    File propsFile
    File buildFile
    File appBuildFile
    File appScalaFile

    def setup() {
        settingsFile = file('settings.gradle')
        propsFile = file('gradle.properties')
        buildFile = file('build.gradle')
        appBuildFile = file('app/build.gradle')
        appScalaFile = file('app/src/main/scala/com/github/prokod/it/gradleCrossbuildSample/Main.scala')
    }

    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi cross build aspects (scala / spark) multi-module project with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'app'
"""

        buildFile << """
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.igormaznitsa:jcp:7.0.5")
    }
}

plugins {
    id 'com.github.prokod.gradle-crossbuild' apply false
}

repositories {
    mavenCentral()
}

subprojects {
    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
    apply plugin: 'maven-publish'

    crossBuild {
        scalaVersionsCatalog = ["2.13": "2.13.8", "2.12": "2.12.15"]

        def sparkVersionsForBoth = ["3.3.0", "3.2.1", "3.2.0"]
        def sparkVersionsFor2_12 = ["3.1.3", "3.1.2", "3.1.1", "3.1.0", "3.0.3", "3.0.2", "3.0.1", "3.0.0"]

        builds {
            for (spark in sparkVersionsForBoth) {
                create(spark) {
                    scalaVersions = ["2.12", "2.13"]
                    archive.appendixPattern = "-\${spark}_?"
                    ext = ['sparkVersion':spark]
                }
            }

            for (spark in sparkVersionsFor2_12) {
                create(spark) {
                    scalaVersions = ["2.12"]
                    archive.appendixPattern = "-\${spark}_?"
                    ext = ['sparkVersion':spark]
                }
            }
        }
    }
    
    dependencies {
        implementation "org.scala-lang:scala-library:2.13.8"
    }
}
"""

        appScalaFile << """
package com.github.prokod.it.gradleCrossbuildSample

object Main {

    val scalaCompatVersion = /*\$"\\""+scalaCompat+"\\""\$*/ /*-*/ ""
    val scalaVersion = /*\$"\\""+scala+"\\""\$*/ /*-*/ ""
    val sparkVersion = /*\$"\\""+spark+"\\""\$*/ /*-*/ ""
    val sparkMinorVersion = /*\$"\\""+sparkMinor+"\\""\$*/ /*-*/ ""

    def main(args: Array[String]): Unit = {
        println(f"Read spark version as \$sparkVersion, scala version as \$scalaVersion, scala compat version as \$scalaCompatVersion, spark minor version as \$sparkMinorVersion")

        //#if scalaCompat < 2.13
        //\$println("This doesn't work on scala 2.13: \${scala.collection.mutable.WrappedArray::class.qualifiedName}")
        //#endif
    }
}
"""

        appBuildFile << """
import com.igormaznitsa.jcp.gradle.JcpTask

plugins {
    id 'com.igormaznitsa.jcp'
}

group = 'com.github.prokod.it'
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

sourceSets.findAll { it.name.startsWith('crossBuild') }.each { sourceSet ->
    def compileTaskName = sourceSet.getCompileTaskName('scala')

    def spark = sourceSet.ext.sparkVersion
    def sparkStripped = spark.replaceAll('\\\\.', '')
    def scala = sourceSet.ext.scalaCompilerVersion
    def sparkMinor = spark.substring(0, spark.lastIndexOf('.'))
    def scalaCompat = scala.substring(0, scala.lastIndexOf('.'))
    def scalaCompatStripped = scalaCompat.replaceAll('\\\\.', '')
     
    tasks.register("copy_\${sourceSet.name}", Copy) {
        from configurations.findByName(sourceSet.getRuntimeClasspathConfigurationName())
        into "\$buildDir/classpath-libs-\${sourceSet.name}"
    }
       
    tasks.register("preprocess_\${sourceSet.name}", JcpTask) {
        sources = sourceSets.main.scala.srcDirs
        target = file("src/\${sourceSet.name}/scala")
        clearTarget = true
        fileExtensions = ["java", "scala"]

        vars = ["spark": spark, "sparkMinor": sparkMinor, "scala": scala, "scalaCompat": scalaCompat]
        outputs.upToDateWhen { target.get().exists() }
    }

    project.tasks.findByName(sourceSet.getCompileTaskName('scala')).with { ScalaCompile t ->
        t.dependsOn tasks.findByName("preprocess_\${sourceSet.name}")
        t.dependsOn tasks.findByName("copy_\${sourceSet.name}")
    }

    project.dependencies.add(sourceSet.getImplementationConfigurationName(), [group: "org.apache.spark", name: "spark-sql_\${scalaCompat}", version: "\${spark}"])

    publishing {
        publications {
            create("crossBuild\${sparkStripped}_\${scalaCompatStripped}", MavenPublication) {
                afterEvaluate {
                    artifact project.tasks.findByName("crossBuild\${sparkStripped}_\${scalaCompatStripped}Jar")
                }
            }
        }
    }

    tasks.withType(GenerateMavenPom) { t ->
        if (t.name.contains("CrossBuild\${sparkStripped}_\${scalaCompatStripped}")) {
            t.destination = file("\$buildDir/generated-pom\${sparkStripped}_\${scalaCompatStripped}.xml")
        }
    }
}

"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())

        and:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('tasks', 'build', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":tasks").outcome == SUCCESS
        result.task(":app:build").outcome == SUCCESS
        result.task(":app:publishToMavenLocal").outcome == SUCCESS

        println(result.output)

        when:
        def libPaths = []
        def xmlPaths = []
        def poms = [:]
        def classPaths = []
        def metadata = []
        def sparkVersionsForBoth = ["3.3.0", "3.2.1", "3.2.0"]
        def scalaVersions = ['2.12.15', '2.13.8']

        and:
        for (spark in sparkVersionsForBoth) {
            for (scala in scalaVersions) {
                def sparkMinor = spark.substring(0, spark.lastIndexOf('.'))
                def sparkStripped = spark.replaceAll('\\.', '')
                def scalaCompat = scala.substring(0, scala.lastIndexOf('.'))
                def scalaCompatStripped = scalaCompat.replaceAll('\\.', '')

                libPaths += [dir.resolve("app/build/libs/app-${spark}_${scalaCompat}*.jar").normalize().toString()]
                xmlPaths += [dir.resolve("app${File.separator}build${File.separator}generated-pom${sparkStripped}_${scalaCompatStripped}.xml").normalize().toString()]
                poms += [[spark, scala, scalaCompat]:new XmlSlurper().parseText(new File(xmlPaths.last()).text).dependencies.dependency.collectEntries {
                    [it.groupId.text(), it]
                }]
                classPaths += [dir.resolve("app/build/classpath-libs-crossBuild${sparkStripped}_${scalaCompatStripped}").normalize().toString()]
                metadata += [[spark, scala, scalaCompat, sparkMinor]]
            }
        }

        and:
        def sparkVersionsFor2_12 = ["3.1.3", "3.1.2", "3.1.1", "3.1.0", "3.0.3", "3.0.2", "3.0.1", "3.0.0"]
        for (spark in sparkVersionsFor2_12) {
            def sparkMinor = spark.substring(0, spark.lastIndexOf('.'))
            def sparkStripped = spark.replaceAll('\\.', '')
            def scala = '2.12.15'
            def scalaCompat = scala.substring(0, scala.lastIndexOf('.'))
            def scalaCompatStripped = scalaCompat.replaceAll('\\.', '')

            libPaths += [dir.resolve("app/build/libs/app-${spark}_${scalaCompat}*.jar").normalize().toString()]
            xmlPaths += [dir.resolve("app${File.separator}build${File.separator}generated-pom${sparkStripped}_${scalaCompatStripped}.xml").normalize().toString()]
            poms += [[spark, scala, scalaCompat]:new XmlSlurper().parseText(new File(xmlPaths.last()).text).dependencies.dependency.collectEntries {
                [it.groupId.text(), it]
            }]
            classPaths += [dir.resolve("app/build/classpath-libs-crossBuild${sparkStripped}_${scalaCompatStripped}").normalize().toString()]
            metadata += [[spark, scala, scalaCompat, sparkMinor]]
        }

        then:
        libPaths.each {assert findFile(it) != null}

        and:
        [classPaths, libPaths, metadata].transpose().collect{ List tuple ->
            def f = findFile(tuple[1])
            def d = new File(tuple[0])
            def data = tuple[2]
            def jars = d.listFiles()
            def urls = jars.collect { it.toURI().toURL() }
            urls += [f.toURI().toURL()]
            ClassLoader classLoader = new URLClassLoader(urls as URL[], this.class.classLoader)
            Class clazzExModule = classLoader.loadClass("com.github.prokod.it.gradleCrossbuildSample.Main\$")
            def module = clazzExModule.getField("MODULE\$").get(null)
            def expected = "Read spark version as ${data[0]}, scala version as ${data[1]}, scala compat version as ${data[2]}, spark minor version as ${data[3]}\n"
            if (data[2] == '2.12') {
                expected += "This doesn't work on scala 2.13: \${scala.collection.mutable.WrappedArray::class.qualifiedName}\n"
            }
            def allWrittenLines = ''
            def stdOut = System.out
            new ByteArrayOutputStream().withCloseable {bo ->
                System.setOut(new PrintStream(bo))
                module.main()
                bo.flush()
                allWrittenLines = new String(bo.toByteArray())
                System.out = stdOut
            }
            new Tuple2(allWrittenLines.replaceAll("\\r\\n?", "\n"), expected)
        }.each {tuple ->
            assert tuple.first == tuple.second
        }

        and:
        xmlPaths.collect {new File(it)}.each {assert it.exists()}

        and:
        poms.each {pom ->
            assert pom.value.size() == 2
            assert pom.value['org.scala-lang'].artifactId == 'scala-library'
            assert pom.value['org.scala-lang'].version == pom.key[1]
            assert pom.value['org.scala-lang'].scope == 'runtime'
            assert pom.value['org.apache.spark'].groupId == 'org.apache.spark'
            assert pom.value['org.apache.spark'].artifactId == "spark-sql_${pom.key[2]}"
            assert pom.value['org.apache.spark'].version == pom.key[0]
            assert pom.value['org.apache.spark'].scope == 'runtime'
        }

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.12'
        '6.9.2'         | '2.13'
        '7.3.3'         | '2.12'
    }
}
