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
import org.skyscreamer.jsonassert.JSONAssert
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginCompileMultiModuleAdvancedTest extends CrossBuildGradleRunnerSpec {

    File settingsFile
    File propsFile
    File buildFile
    File libBuildFile
    File libScalaFile
    File lib2BuildFile
    File lib2ScalaFile
    File lib3BuildFile
    File lib3ScalaFile
    File lib3JavaFile
    File appBuildFile
    File appScalaFile

    def setup() {
        settingsFile = file('settings.gradle')
        propsFile = file('gradle.properties')
        buildFile = file('build.gradle')
        libBuildFile = file('lib/build.gradle')
        libScalaFile = file('lib/src/main/scala/Factorial.scala')
        lib2BuildFile = file('lib2/build.gradle')
        lib2ScalaFile = file('lib2/src/main/scala/CompileTimeFactorial.scala')
        lib3BuildFile = file('lib3/build.gradle')
        lib3ScalaFile = file('lib3/src/main/scala/CompileTimeFactorialExtended.scala')
        lib3JavaFile = file('lib3/src/main/java/CompileTimeFactorialUtils.java')
        appBuildFile = file('app/build.gradle')
        appScalaFile = file('app/src/main/scala/Test.scala')
    }

    /** *
     * This test is using 'compile' configuration to express dependency graph between the modules.
     * It has a counterpart test which is using 'implementation' configuration instead
     * here {@link CrossBuildPluginImplementationMultiModuleTest}
     *
     * resource file for the test: app_builds_resolved_configurations-00.json
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with cross building dsl that is different on each submodule and with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
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
        maven { url 'https://artifactory.srv.int.avast.com/artifactory/maven' }
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
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark230_211Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                }
                crossBuildSpark240_212(MavenPublication) {
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark240_212Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
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
    compile "com.google.guava:guava:18.0"
    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        lib2ScalaFile << """
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
"""

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
    compile project(':lib')
    compile 'org.scala-lang:scala-reflect:2.12.8'
    crossBuildSpark230_211Compile 'org.scala-lang:scala-reflect:2.11.12'
}
"""

        appScalaFile << """
import CompileTimeFactorial._

object Test extends App {
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
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildSpark230_211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
        
dependencies {
    compile project(':lib2')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('crossBuildResolvedConfigs', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":app:crossBuildSpark230_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildResolvedConfigs").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.12*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.11*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.12*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.11*.jar")
        !fileExists("$dir.root.absolutePath/app/build/libs/app_2.12*.jar")

        when:
        def expectedJsonAsText = loadResourceAsText(dsv: defaultScalaVersion,
                defaultOrRuntime: gradleVersion.startsWith('4') ? 'default' : 'runtime',
                defaultOrCompile: gradleVersion.startsWith('4') ? 'default' : 'compile',
                '/app_builds_resolved_configurations-00.json')
        def appResolvedConfigurationReportFile = findFile("*/app_builds_resolved_configurations.json")

        then:
        appResolvedConfigurationReportFile != null
        def actualJsonAsText = appResolvedConfigurationReportFile.text
        JSONAssert.assertEquals(expectedJsonAsText, actualJsonAsText, false)

        when:
        def pom211File = new File("${dir.root.absolutePath}${File.separator}lib${File.separator}build${File.separator}generated-pom_2.11.xml")
        def pom212File = new File("${dir.root.absolutePath}${File.separator}lib${File.separator}build${File.separator}generated-pom_2.12.xml")

        then:
        pom211File.exists()
        pom212File.exists()

        when:
        def pom211 = new XmlSlurper().parse(pom211File)

        then:
        pom211.dependencies.dependency.size() == 2
        pom211.dependencies.dependency[0].groupId == 'org.scala-lang'
        pom211.dependencies.dependency[0].artifactId == 'scala-library'
        pom211.dependencies.dependency[0].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        pom211.dependencies.dependency[0].scope == 'compile'
        pom211.dependencies.dependency[1].groupId == 'com.google.guava'
        pom211.dependencies.dependency[1].artifactId == 'guava'
        pom211.dependencies.dependency[1].version == '18.0'
        pom211.dependencies.dependency[1].scope == 'compile'

        when:
        def pom212 = new XmlSlurper().parse(pom212File)

        then:
        pom212.dependencies.dependency.size() == 2
        pom212.dependencies.dependency[0].groupId == 'org.scala-lang'
        pom212.dependencies.dependency[0].artifactId == 'scala-library'
        pom212.dependencies.dependency[0].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.12']
        pom212.dependencies.dependency[0].scope == 'compile'
        pom212.dependencies.dependency[1].groupId == 'com.google.guava'
        pom212.dependencies.dependency[1].artifactId == 'guava'
        pom212.dependencies.dependency[1].version == '18.0'
        pom212.dependencies.dependency[1].scope == 'compile'

        when:
        def lib2pom211File = new File("${dir.root.absolutePath}${File.separator}lib2${File.separator}build${File.separator}generated-pom_2.11.xml")
        def lib2pom212File = new File("${dir.root.absolutePath}${File.separator}lib2${File.separator}build${File.separator}generated-pom_2.12.xml")

        then:
        lib2pom211File.exists()
        lib2pom212File.exists()

        when:
        def lib2pom211 = new XmlSlurper().parse(lib2pom211File)

        then:
        lib2pom211.dependencies.dependency.size() == 4
        lib2pom211.dependencies.dependency[0].groupId == 'org.scala-lang'
        lib2pom211.dependencies.dependency[0].artifactId == 'scala-library'
        lib2pom211.dependencies.dependency[0].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        lib2pom211.dependencies.dependency[0].scope == 'compile'
        lib2pom211.dependencies.dependency[1].groupId == 'com.google.guava'
        lib2pom211.dependencies.dependency[1].artifactId == 'guava'
        lib2pom211.dependencies.dependency[1].version == '18.0'
        lib2pom211.dependencies.dependency[1].scope == 'compile'
        lib2pom211.dependencies.dependency[2].groupId == 'com.github.prokod.it'
        lib2pom211.dependencies.dependency[2].artifactId == 'lib_2.11'
        lib2pom211.dependencies.dependency[2].version == '1.0-SNAPSHOT'
        lib2pom211.dependencies.dependency[2].scope == 'compile'
        lib2pom211.dependencies.dependency[3].groupId == 'org.scala-lang'
        lib2pom211.dependencies.dependency[3].artifactId == 'scala-reflect'
        lib2pom211.dependencies.dependency[3].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        lib2pom211.dependencies.dependency[3].scope == 'compile'

        when:
        def lib2pom212 = new XmlSlurper().parse(lib2pom212File)

        then:
        lib2pom212.dependencies.dependency.size() == 4
        lib2pom212.dependencies.dependency[0].groupId == 'com.github.prokod.it'
        lib2pom212.dependencies.dependency[0].artifactId == 'lib_2.12'
        lib2pom212.dependencies.dependency[0].version == '1.0-SNAPSHOT'
        lib2pom212.dependencies.dependency[0].scope == 'compile'
        lib2pom212.dependencies.dependency[1].groupId == 'com.google.guava'
        lib2pom212.dependencies.dependency[1].artifactId == 'guava'
        lib2pom212.dependencies.dependency[1].version == '18.0'
        lib2pom212.dependencies.dependency[1].scope == 'compile'
        lib2pom212.dependencies.dependency[2].groupId == 'org.scala-lang'
        lib2pom212.dependencies.dependency[2].artifactId == 'scala-library'
        lib2pom212.dependencies.dependency[2].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.12']
        lib2pom212.dependencies.dependency[2].scope == 'compile'
        lib2pom212.dependencies.dependency[3].groupId == 'org.scala-lang'
        lib2pom212.dependencies.dependency[3].artifactId == 'scala-reflect'
        lib2pom212.dependencies.dependency[3].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.12']
        lib2pom212.dependencies.dependency[3].scope == 'compile'

        when:
        def appPom211File = new File("${dir.root.absolutePath}${File.separator}app${File.separator}build${File.separator}generated-pom_2.11.xml")
        def appPom212File = new File("${dir.root.absolutePath}${File.separator}app${File.separator}build${File.separator}generated-pom_2.12.xml")

        then:
        appPom211File.exists()
        !appPom212File.exists()

        when:
        def appPom211 = new XmlSlurper().parse(appPom211File)

        then:
        appPom211.dependencies.dependency.size() == 5
        appPom211.dependencies.dependency[0].groupId == 'com.github.prokod.it'
        appPom211.dependencies.dependency[0].artifactId == 'lib2_2.11'
        appPom211.dependencies.dependency[0].version == '1.0-SNAPSHOT'
        appPom211.dependencies.dependency[0].scope == 'compile'
        appPom211.dependencies.dependency[1].groupId == 'org.scala-lang'
        appPom211.dependencies.dependency[1].artifactId == 'scala-library'
        appPom211.dependencies.dependency[1].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        appPom211.dependencies.dependency[1].scope == 'compile'
        appPom211.dependencies.dependency[2].groupId == 'com.google.guava'
        appPom211.dependencies.dependency[2].artifactId == 'guava'
        appPom211.dependencies.dependency[2].version == '18.0'
        appPom211.dependencies.dependency[2].scope == 'compile'
        appPom211.dependencies.dependency[3].groupId == 'com.github.prokod.it'
        appPom211.dependencies.dependency[3].artifactId == 'lib_2.11'
        appPom211.dependencies.dependency[3].version == '1.0-SNAPSHOT'
        appPom211.dependencies.dependency[3].scope == 'compile'
        appPom211.dependencies.dependency[4].groupId == 'org.scala-lang'
        appPom211.dependencies.dependency[4].artifactId == 'scala-reflect'
        appPom211.dependencies.dependency[4].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.11']
        appPom211.dependencies.dependency[4].scope == 'compile'

        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.11'
        '4.10.3'        | '2.12'
        '5.5.1'         | '2.12'
    }

    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin lazily on a multi-module project with dependency graph of depth 3 and with cross building dsl that is different on each submodule and with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
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
        maven { url 'https://artifactory.srv.int.avast.com/artifactory/maven' }
    }
}

subprojects {
    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild') {
        if (!project.name.endsWith('app')) {
            crossBuild {
                builds {
                    spark230_211 
                    spark240_212
                }
            }
        }
    }
    
    project.pluginManager.withPlugin('maven-publish') {
        if (!project.name.endsWith('app')) {
            publishing {
                publications {
                    crossBuildSpark230_211(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark230_211Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark240_212(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark240_212Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
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
object Factorial {
  // The actual implementation is regular old-fashioned scala code:
  def normalFactorial(n: Int): Int =
    if (n == 0) 1
    else n * normalFactorial(n - 1)
}
"""

        libBuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

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
    compile "com.google.guava:guava:18.0"
    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        lib2ScalaFile << """
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
"""

        lib2BuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

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
    compile project(':lib')
    compile 'org.scala-lang:scala-reflect:2.12.8'
    crossBuildSpark230_211Compile 'org.scala-lang:scala-reflect:2.11.12'
}
"""

        appScalaFile << """
import CompileTimeFactorial._

object Test extends App {
    println(factorial(10))

    // When uncommented, this will produce an error at compile-time, as we
    // only implemented a case for an Int literal, not a variable:
    // val n = 10
    // println(factorial(n))
}
"""

        appBuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

crossBuild {
    builds {
        spark230_211 
    }
}

publishing {
    publications {
        crossBuildSpark230_211(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildSpark230_211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
        
dependencies {
    compile project(':lib2')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":app:crossBuildSpark230_211Jar").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.12*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.11*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.12*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.11*.jar")
        !fileExists("$dir.root.absolutePath/app/build/libs/app_2.12*.jar")

        def pom211 = new File("${dir.root.absolutePath}${File.separator}lib${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def pom212 = new File("${dir.root.absolutePath}${File.separator}lib${File.separator}build${File.separator}generated-pom_2.12.xml").text

        !pom211.contains('2.12.')
        pom211.contains('2.11.12')
        pom211.contains('18.0')
        !pom212.contains('2.12.+')
        !pom212.contains('2.11.')
        pom212.contains('2.12.8')
        pom212.contains('18.0')

        def lib2pom211 = new File("${dir.root.absolutePath}${File.separator}lib2${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def lib2pom212 = new File("${dir.root.absolutePath}${File.separator}lib2${File.separator}build${File.separator}generated-pom_2.12.xml").text

        !lib2pom211.contains('2.12.')
        lib2pom211.contains('scala-reflect')
        lib2pom211.contains('2.11.12')
        lib2pom211.contains('1.0-SNAPSHOT')
        lib2pom211.contains('lib_2.11')
        !lib2pom212.contains('2.12.+')
        !lib2pom212.contains('2.11.')
        lib2pom212.contains('2.12.8')
        lib2pom212.contains('scala-reflect')
        lib2pom212.contains('1.0-SNAPSHOT')
        lib2pom212.contains('lib_2.12')

        def appPom211 = new File("${dir.root.absolutePath}${File.separator}app${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def appPom212Exist = new File("${dir.root.absolutePath}${File.separator}app${File.separator}build${File.separator}generated-pom_2.12.xml").exists()

        !appPom211.contains('2.12.')
        appPom211.contains('2.11.12')
        appPom211.contains('lib2_2.11')
        !appPom212Exist

        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.11'
        '4.10.3'        | '2.12'
        '5.4.1'         | '2.12'
    }

    /**
     * This test is a variant of the test using resource app_builds_resolved_configurations-00.json
     * In this test we use scalaz as the external 3rd party scala lib (qMarked) dependency the plugin
     * should resolve correctly.
     *
     * @return
     */
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
        maven { url 'https://artifactory.srv.int.avast.com/artifactory/maven' }
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
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark230_211Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                }
                crossBuildSpark240_212(MavenPublication) {
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark240_212Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
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
    ${scalazQmarked ? "compile 'org.scalaz:scalaz-core_?:7.2.28'" : "compile 'org.scalaz:scalaz-core_2.12:7.2.28'\ncrossBuildSpark230_211Compile 'org.scalaz:scalaz-core_2.11:7.2.28'"}

    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        lib2ScalaFile << """
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
"""

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
    compile project(':lib')
    compile 'org.scala-lang:scala-reflect:2.12.8'
    crossBuildSpark230_211Compile 'org.scala-lang:scala-reflect:2.11.12'
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
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildSpark230_211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
        
dependencies {
    compile project(':lib2')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('app:crossBuildSpark230_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":app:crossBuildSpark230_211Jar").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.12*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.12*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.11*.jar")
        !fileExists("$dir.root.absolutePath/app/build/libs/app_2.12*.jar")

        where:
        gradleVersion   | defaultScalaVersion | scalazQmarked
        '4.2'           | '2.11'              | false
        '4.10.3'        | '2.12'              | false
        '5.4.1'         | '2.12'              | false
        '4.2'           | '2.11'              | true
        '4.10.3'        | '2.12'              | true
        '5.4.1'         | '2.12'              | true
    }

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
        maven { url 'https://artifactory.srv.int.avast.com/artifactory/maven' }
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
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark230_211Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                }
                crossBuildSpark240_212(MavenPublication) {
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark240_212Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
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
    compile 'org.scalaz:scalaz-core_?:7.2.28'

    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        lib2ScalaFile << """
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
"""

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
    compile project(':lib')
    compile 'org.scala-lang:scala-reflect:2.12.8'
    crossBuildSpark230_211Compile 'org.scala-lang:scala-reflect:2.11.12'
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
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildSpark230_211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
        
dependencies {
    compile project(':lib2')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('lib:crossBuildV210Jar', 'app:crossBuildSpark230_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:crossBuildV210Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark230_211Jar").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/lib/build/libs/lib-legacy_2.10*.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.12*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.12*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.11*.jar")
        !fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.12*.jar")

        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.11'
        '4.10.3'        | '2.12'
        '5.4.1'         | '2.12'
    }

    /**
     * resource file for the test: app_builds_resolved_configurations-01.json
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with cross building dsl that is different on each submodule and inlined individual appendixPattern with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
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
        maven { url 'https://artifactory.srv.int.avast.com/artifactory/maven' }
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
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark230_211Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                }
                crossBuildSpark240_212(MavenPublication) {
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark240_212Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
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
            archive.appendixPattern = '-legacy_?'
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
    compile 'org.scalaz:scalaz-core_?:7.2.28'

    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        lib2ScalaFile << """
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
"""

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
    compile project(':lib')
    compile 'org.scala-lang:scala-reflect:2.12.8'
    crossBuildSpark230_211Compile 'org.scala-lang:scala-reflect:2.11.12'
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
            archive.appendixPattern = '-all_?'
        }
    }
}

publishing {
    publications {
        crossBuildSpark230_211(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildSpark230_211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
        
dependencies {
    compile project(':lib2')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('lib:crossBuildV210Jar', 'app:crossBuildSpark230_211Jar', 'app:crossBuildResolvedConfigs', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:crossBuildV210Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark230_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildResolvedConfigs").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/lib/build/libs/lib-legacy_2.10*.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.12*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.12*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.11*.jar")
        !fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.12*.jar")

        when:
        // Gradle 4 'java' plugin Configuration model is less precise and so firstLevelModuleDependencies are under
        // 'default' configuration, Gradle 5 already has a more precise model and so 'default' configuration is replaced
        // by either 'runtime' or 'compile' see https://gradle.org/whats-new/gradle-5/#fine-grained-transitive-dependency-management
        def expectedJsonAsText = loadResourceAsText(dsv: defaultScalaVersion,
                defaultOrRuntime: gradleVersion.startsWith('4') ? 'default' : 'runtime',
                defaultOrCompile: gradleVersion.startsWith('4') ? 'default' : 'compile',
                '/app_builds_resolved_configurations-01.json')
        def appResolvedConfigurationReportFile = findFile("*/app_builds_resolved_configurations.json")

        then:
        appResolvedConfigurationReportFile != null
        def actualJsonAsText = appResolvedConfigurationReportFile.text
        JSONAssert.assertEquals(expectedJsonAsText, actualJsonAsText, false)

        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.11'
        '4.10.3'        | '2.12'
        '5.4.1'         | '2.12'
    }

    /**
     * This test checks the following plugin behaviour:
     * 1. under compileOnly type of dependencies. It shows that compileOnly dependencies needs to be repeated in
     * dependent sub module even though the dependency sub module already declares those compileOnly dependencies.
     * In short compileOnly dependencies can not become transitive
     * 2. Plugin forgives scala-lang unaligned default-variant dependency by fixing it (update version) in
     * dependency resolution
     * @return
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 with a more complex inter sub module dependencies and with cross building dsl that is different on each submodule and inlined individual appendixPattern with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
include 'lib2'
include 'lib3'
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
        maven { url 'https://artifactory.srv.int.avast.com/artifactory/maven' }
    }
}

subprojects {
    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild') {
        if (!project.name.endsWith('app')) {
            crossBuild {
                builds {
                    spark233_211
                    spark242_212
                    spark243 {
                        scalaVersions = ['2.11', '2.12']
                        archive.appendixPattern = '-2-4-3_?'
                    }
                }
            }
        }
    }

    project.pluginManager.withPlugin('maven-publish') {
        if (!project.name.endsWith('app')) {
            publishing {
                publications {
                    crossBuildSpark233_211(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark233_211Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark242_212(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark242_212Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark243_211(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark243_211Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark243_212(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark243_212Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                }
            }
        }
    }

    tasks.withType(GenerateMavenPom) { t ->
        if (t.name.contains('CrossBuildSpark233_211')) {
            t.destination = file("\$buildDir/generated-pom233_2.11.xml")
        }
        if (t.name.contains('CrossBuildSpark242_212')) {
            t.destination = file("\$buildDir/generated-pom242_2.12.xml")
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
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

crossBuild {
    builds {
        v210 {
            archive.appendixPattern = '-legacy_?'
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
    compileOnly 'org.scalaz:scalaz-core_${defaultScalaVersion}:7.2.28'
    crossBuildV210CompileOnly 'org.scalaz:scalaz-core_2.10:7.2.25'
    crossBuildSpark233_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.26'
    crossBuildSpark242_212CompileOnly 'org.scalaz:scalaz-core_2.12:7.2.27'
    crossBuildSpark243_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.28'
    crossBuildSpark243_212CompileOnly 'org.scalaz:scalaz-core_2.12:7.2.28'

    compile 'org.scala-lang:scala-library:${defaultScalaVersion}.+'
}
"""

        lib2ScalaFile << """
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
"""

        lib2BuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

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
    compile project(':lib')
    
    compileOnly 'org.scalaz:scalaz-core_2.11:7.2.28'
    crossBuildSpark233_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.26'
    crossBuildSpark242_212CompileOnly 'org.scalaz:scalaz-core_2.12:7.2.27'
    crossBuildSpark243_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.28'
    crossBuildSpark243_212CompileOnly 'org.scalaz:scalaz-core_2.12:7.2.28'
    
    // Plugin forgives on this scala-lang unaligned default-variant dependency and fixes it in dependency resolution
    compile 'org.scala-lang:scala-reflect:2.12.8'
}
"""

        lib3ScalaFile << """
object CompileTimeFactorialExtended {

  import scala.language.experimental.macros
  import Factorial._

  // This function exposed to consumers has a normal Scala type:
  def factorial(n: Int): Int =
  // but it is implemented as a macro:
  macro CompileTimeFactorialExtended.factorial_impl

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
"""

        lib3BuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

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
    compile project(':lib')
    compile 'org.scala-lang:scala-reflect:2.12.8'
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
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

crossBuild {
    builds {
        spark233_211 {
            archive.appendixPattern = '-all_?'
        }
    }
}

publishing {
    publications {
        crossBuildSpark233_211(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildSpark233_211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
        
dependencies {
    compile project(':lib2')

    compileOnly 'org.scalaz:scalaz-core_${defaultScalaVersion}:7.2.28'
    crossBuildSpark233_211CompileOnly 'org.scalaz:scalaz-core_2.11:7.2.26'
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('build', 'lib:crossBuildV210Jar', 'lib2:crossBuildResolvedConfigs', 'lib3:crossBuildResolvedConfigs', 'app:crossBuildSpark233_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:build").outcome == SUCCESS
        result.task(":lib2:build").outcome == SUCCESS
        result.task(":lib3:build").outcome == SUCCESS
        result.task(":app:build").outcome == SUCCESS
        result.task(":lib2:crossBuildResolvedConfigs").outcome == SUCCESS
        result.task(":lib3:crossBuildResolvedConfigs").outcome == SUCCESS
        result.task(":lib:crossBuildV210Jar").outcome == SUCCESS
        result.task(":lib:crossBuildSpark233_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark233_211Jar").outcome == SUCCESS

        // 'build' task should:
        fileExists("$dir.root.absolutePath/lib/build/libs/lib-1.0-SNAPSHOT.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2-1.0-SNAPSHOT.jar")
        fileExists("$dir.root.absolutePath/lib3/build/libs/lib3-1.0-SNAPSHOT.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app-1.0-SNAPSHOT.jar")

        // 'lib:crossBuildV210Jar', 'app:crossBuildSpark233_211Jar' tasks should:
        fileExists("$dir.root.absolutePath/lib/build/libs/lib-legacy_2.10*.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.12*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.12*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.11*.jar")
        !fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.12*.jar")

        when:
        // Gradle 4 'java' plugin Configuration model is less precise ans so firstLevelModuleDependencies are under
        // 'default' configuration, Gradle 5 already has a more precise model and so 'default' configuration is replaced
        // by either 'runtime' or 'compile' see https://gradle.org/whats-new/gradle-5/#fine-grained-transitive-dependency-management
        def expectedLib2JsonAsText = loadResourceAsText(dsv: defaultScalaVersion,
                defaultOrRuntime: gradleVersion.startsWith('4') ? 'default' : 'runtime',
                defaultOrCompile: gradleVersion.startsWith('4') ? 'default' : 'compile',
                '/app_builds_resolved_configurations-02.json')
        def lib2ResolvedConfigurationReportFile = findFile("*/lib2_builds_resolved_configurations.json")

        then:
        lib2ResolvedConfigurationReportFile != null
        def actualLib2JsonAsText = lib2ResolvedConfigurationReportFile.text
        JSONAssert.assertEquals(expectedLib2JsonAsText, actualLib2JsonAsText, false)

        when:
        // Gradle 4 'java' plugin Configuration model is less precise ans so firstLevelModuleDependencies are under
        // 'default' configuration, Gradle 5 already has a more precise model and so 'default' configuration is replaced
        // by either 'runtime' or 'compile' see https://gradle.org/whats-new/gradle-5/#fine-grained-transitive-dependency-management
        def expectedLib3JsonAsText = loadResourceAsText(dsv: defaultScalaVersion,
                defaultOrRuntime: gradleVersion.startsWith('4') ? 'default' : 'runtime',
                defaultOrCompile: gradleVersion.startsWith('4') ? 'default' : 'compile',
                '/app_builds_resolved_configurations-03.json')
        def lib3ResolvedConfigurationReportFile = findFile("*/lib3_builds_resolved_configurations.json")

        then:
        lib3ResolvedConfigurationReportFile != null
        def actualLib3JsonAsText = lib3ResolvedConfigurationReportFile.text
        JSONAssert.assertEquals(expectedLib3JsonAsText, actualLib3JsonAsText, false)

        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.11'
        '4.10.3'        | '2.12'
        '5.4.1'         | '2.12'
    }

    /**
     * Here lib3 is a non cross build dependency.
     * This test checks that the transitive dependencies for lib3 are added to dependent lib2
     * @return
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with cross building dsl that is applied only to some sub modules should produce expected: jars, pom files; and pom files content should be correct"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
include 'lib2'
include 'lib3'
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
        maven { url 'https://artifactory.srv.int.avast.com/artifactory/maven' }
    }
}

subprojects {
    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild') {
        if (!project.name.endsWith('app')) {
            crossBuild {
                builds {
                    spark233_211
                    spark242_212
                    spark243 {
                        scalaVersions = ['2.11', '2.12']
                        archive.appendixPattern = '-2-4-3_?'
                    }
                }
            }
        }
    }

    project.pluginManager.withPlugin('maven-publish') {
        if (!project.name.endsWith('app')) {
            publishing {
                publications {
                    crossBuildSpark233_211(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark233_211Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark242_212(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark242_212Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark243_211(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark243_211Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                    crossBuildSpark243_212(MavenPublication) {
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                            artifact crossBuildSpark243_212Jar
                        ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                    }
                }
            }
        }
    }

    tasks.withType(GenerateMavenPom) { t ->
        if (t.name.contains('CrossBuildSpark233_211')) {
            t.destination = file("\$buildDir/generated-pom233_2.11.xml")
        }
        if (t.name.contains('CrossBuildSpark242_212')) {
            t.destination = file("\$buildDir/generated-pom242_2.12.xml")
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
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

crossBuild {
    builds {
        v210 {
            archive.appendixPattern = '-legacy_?'
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
    compile 'org.scalaz:scalaz-core_${defaultScalaVersion}:7.2.28'
    crossBuildV210Compile 'org.scalaz:scalaz-core_2.10:7.2.25'
    crossBuildSpark233_211Compile 'org.scalaz:scalaz-core_2.11:7.2.26'
    crossBuildSpark242_212Compile 'org.scalaz:scalaz-core_2.12:7.2.27'
    crossBuildSpark243_211Compile 'org.scalaz:scalaz-core_2.11:7.2.28'
    crossBuildSpark243_212Compile 'org.scalaz:scalaz-core_2.12:7.2.28'

    compile 'org.scala-lang:scala-library:${defaultScalaVersion}.+'
}
"""

        lib2ScalaFile << """
object CompileTimeFactorial {

  import scala.language.experimental.macros
  import Factorial._
  import CompileTimeFactorialUtils._

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
        println(CompileTimeFactorialUtils.printHello)
        ???
    }
  }
}
"""

        lib2BuildFile << """
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

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
    compile project(':lib')
    compile project(':lib3')
    
    // Plugin forgives on this scala-lang unaligned default-variant dependency and fixes it in dependency resolution
    compile 'org.scala-lang:scala-reflect:2.12.8'
}
"""

        lib3JavaFile << """
import org.apache.commons.math3.util.MathUtils;

public class CompileTimeFactorialUtils {

   public static String printHello() {
      double twoPi = MathUtils.TWO_PI;
      return "Hello! " + twoPi;
   }
}
"""

        lib3BuildFile << """
apply plugin: 'java'
//apply plugin: 'maven-publish'

dependencies {
  compile 'org.apache.commons:commons-math3:3.6.1'
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
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

crossBuild {
    builds {
        spark233_211 {
            archive.appendixPattern = '-all_?'
        }
    }
}

publishing {
    publications {
        crossBuildSpark233_211(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildSpark233_211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
        
dependencies {
    compile project(':lib2')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('build', 'lib:crossBuildV210Jar', 'lib2:crossBuildResolvedConfigs', 'lib3:jar', 'app:crossBuildSpark233_211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:build").outcome == SUCCESS
        result.task(":lib2:build").outcome == SUCCESS
        result.task(":lib3:build").outcome == SUCCESS
        result.task(":app:build").outcome == SUCCESS
        result.task(":lib2:crossBuildResolvedConfigs").outcome == SUCCESS
        result.task(":lib3:jar").outcome == SUCCESS
        result.task(":lib:crossBuildV210Jar").outcome == SUCCESS
        result.task(":lib:crossBuildSpark233_211Jar").outcome == SUCCESS
        result.task(":app:crossBuildSpark233_211Jar").outcome == SUCCESS

        // 'build' task should:
        fileExists("$dir.root.absolutePath/lib/build/libs/lib-1.0-SNAPSHOT.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2-1.0-SNAPSHOT.jar")
        fileExists("$dir.root.absolutePath/lib3/build/libs/lib3-1.0-SNAPSHOT.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app-1.0-SNAPSHOT.jar")

        // 'lib:crossBuildV210Jar', 'app:crossBuildSpark233_211Jar' tasks should:
        fileExists("$dir.root.absolutePath/lib/build/libs/lib-legacy_2.10*.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.12*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.11*.jar")
        !fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.12*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.11*.jar")
        !fileExists("$dir.root.absolutePath/app/build/libs/app-all_2.12*.jar")

        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.11'
        '4.10.3'        | '2.12'
        '5.4.1'         | '2.12'
    }
}