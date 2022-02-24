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

class CrossBuildPluginApiConfigurationMultiModuleTest extends CrossBuildGradleRunnerSpec {

    File settingsFile
    File propsFile
    File buildFile
    File libBuildFile
    File libScalaFile
    File libJavaFile
    File lib2BuildFile
    File lib2ScalaFile
    File lib2ScalaImplFile
    File appBuildFile
    File appScalaFile

    def setup() {
        settingsFile = file('settings.gradle')
        propsFile = file('gradle.properties')
        buildFile = file('build.gradle')
        libBuildFile = file('lib/build.gradle')
        libScalaFile = file('lib/src/main/scala/HelloWorldLibApi.scala')
        libJavaFile = file('lib/src/main/java/HelloWorldLibImpl.java')
        lib2BuildFile = file('lib2/build.gradle')
        lib2ScalaFile = file('lib2/src/main/scala/HelloWorldLib2Api.scala')
        lib2ScalaImplFile = file('lib2/src/main/scala/HelloWorldLib2Impl.scala')
        appBuildFile = file('app/build.gradle')
        appScalaFile = file('app/src/main/scala/HelloWorldApp.scala')
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Lazy</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x</li>
     *     <li>Running tasks that triggers main sourceset configurations: Yes</li>
     * </ul>
     * Leveraging layout 1 of propagating cross build configs to sub projects
     * Layout 1 means:
     * <ul>
     *     <li>root-project</li>
     *     <ul>
     *         <li>{@code plugins} DSL for crossbuild plugin</li>
     *         <li>{@code allprojects} block with project.pluginManager.withPlugin({@link CrossBuildPlugin}) containing crossbuld plugin DSL</li>
     *     </ul>
     *     <li>sub-projects</li>
     *     <ul>
     *         <li>{@code apply plugin:} {@link CrossBuildPlugin}</li>
     *     </ul>
     * </ul>
     * NOTES:
     * <ul>
     *     <li>Gradle build is called in this test with NON specific cross compile tasks: 'build', 'publishToMavenLocal'.
     *      As a result, the main sourceset classpath configuration is triggered to get resolved. Usual issues like scala-library is missing in 'app' module
     *      than do pop up and so the 'user' HAVE TO use api configuration for implicit scala-library dependency inclusion, or to explicitly include
     *      scala-library dependency in all modules using implementation configuration as the plugin ONLY takes care of this behind the scenes for cross compile configurations.</li>
     *     <li>In this test api configuration is used for scala-library dependency propagation to ':app' module. api configuration resolution is handled correctly
     *      from Gradle 5.x and above and this is why this test is not compatible with Gradle 4.x</li>
     * </ul>
     * @return
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
include 'app'
"""

        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild' apply false
}

allprojects {
    apply plugin: 'java'
    group = 'com.github.prokod.it'
    version = '1.0-SNAPSHOT'
    
    repositories {
        mavenCentral()
    }
    
    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild') {
        crossBuild {
            
            scalaVersionsCatalog = ['2.11':'2.11.11']

            builds {
                spark160_210 
                spark240_211
            }
        }
    }
    
    project.pluginManager.withPlugin('maven-publish') {
        publishing {
            publications {
                crossBuildSpark160_210(MavenPublication) {
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark160_210Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                }
                crossBuildSpark240_211(MavenPublication) {
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                        artifact crossBuildSpark240_211Jar
                    ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
                }
            }
        }

        tasks.withType(GenerateMavenPom) { t ->
            if (t.name.contains('CrossBuildSpark160_210')) {
                t.destination = file("\$buildDir/generated-pom_2.10.xml")
            }
            if (t.name.contains('CrossBuildSpark240_211')) {
                t.destination = file("\$buildDir/generated-pom_2.11.xml")
            }
        }
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
    implementation "org.scalatest:scalatest_?:3.0.1"
    implementation "com.google.guava:guava:18.0"
    api "org.scala-lang:scala-library:${defaultScalaVersion}.+"
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
apply plugin: 'com.github.prokod.gradle-crossbuild'
apply plugin: 'maven-publish'

dependencies {
    implementation project(':lib')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments('tasks', 'build', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:publishToMavenLocal").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.10*.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.10*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.11*.jar")

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
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.11'
        '6.9.2'         | '2.11'
        '7.3.3'         | '2.11'
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Eager</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x</li>
     *     <li>Running tasks that triggers main sourceset configurations: Yes</li>
     * </ul>
     * NOTES:
     * <ul>
     *     <li>Gradle build is called in this test with NON specific cross compile tasks: 'build', 'publishToMavenLocal'.
     *     As a result, the main sourceset classpath configuration is triggered to get resolved. Usual issues like scala-library is missing in 'app' module
     *     than do pop up and so the 'user' HAVE TO use api configuration for implicit scala-library dependency inclusion, or to explicitly include
     *     scala-library dependency in all modules using implementation configuration as the plugin ONLY takes care of this behind the scenes for cross compile configurations.</li>
     * </ul>
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with publishing dsl should produce expected: jars, pom files; and pom files content should be correct"() {
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
    }
}

subprojects {
    apply plugin: 'com.github.prokod.gradle-crossbuild'
    apply plugin: 'maven-publish'

    crossBuild {
        
        scalaVersionsCatalog = ['2.11':'2.11.11']

        builds {
            spark160_210 
            spark240_211
        }
    }
    
    publishing {
        publications {
            crossBuildSpark160_210(MavenPublication) {
                ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                    artifact crossBuildSpark160_210Jar
                ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
            }
            crossBuildSpark240_211(MavenPublication) {
                ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                    artifact crossBuildSpark240_211Jar
                ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
            }
        }
    }

    tasks.withType(GenerateMavenPom) { t ->
        if (t.name.contains('CrossBuildSpark160_210')) {
            t.destination = file("\$buildDir/generated-pom_2.10.xml")
        }
        if (t.name.contains('CrossBuildSpark240_211')) {
            t.destination = file("\$buildDir/generated-pom_2.11.xml")
        }
    }
}
"""

        libScalaFile << """
import org.scalatest._

trait HelloWorldLibApi {
   def greet(): String
}
"""

        libJavaFile << """

public class HelloWorldLibImpl implements HelloWorldLibApi {
   public String greet() {
      return "Hello, world!";
   }
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
    implementation "org.scalatest:scalatest_?:3.0.1"
    implementation "com.google.guava:guava:18.0"
    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        lib2ScalaFile << """
trait HelloWorldLib2Api extends HelloWorldLibApi {
   def greet2(): String = greet() + " x2"
}
"""

        lib2ScalaImplFile << """

class HelloWorldLib2Impl extends HelloWorldLib2Api {
   def greet(): String = "Hello, world!"
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
    api project(':lib')
    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"
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
dependencies {
    implementation project(':lib2')
    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments('build', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:publishToMavenLocal").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.10*.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.10*.jar")
        fileExists("$dir.root.absolutePath/lib2/build/libs/lib2_2.11*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.10*.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.11*.jar")

        def pom210 = new File("${dir.root.absolutePath}${File.separator}lib${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def pom211 = new File("${dir.root.absolutePath}${File.separator}lib${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def lib2pom210 = new File("${dir.root.absolutePath}${File.separator}lib2${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def lib2pom211 = new File("${dir.root.absolutePath}${File.separator}lib2${File.separator}build${File.separator}generated-pom_2.11.xml").text

        when:
        def project210 = new XmlSlurper().parseText(pom210).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        project210.size() == 3
        project210['org.scala-lang'].groupId == 'org.scala-lang'
        project210['org.scala-lang'].artifactId == 'scala-library'
        project210['org.scala-lang'].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.10']
        project210['org.scala-lang'].scope == 'compile'
        project210['com.google.guava'].groupId == 'com.google.guava'
        project210['com.google.guava'].artifactId == 'guava'
        project210['com.google.guava'].version == '18.0'
        project210['com.google.guava'].scope == 'compile'
        project210['org.scalatest'].groupId == 'org.scalatest'
        project210['org.scalatest'].artifactId == 'scalatest_2.10'
        project210['org.scalatest'].version == '3.0.1'
        project210['org.scalatest'].scope == 'compile'

        when:
        def project211 = new XmlSlurper().parseText(pom211).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        project211.size() == 3
        project211['org.scalatest'].groupId == 'org.scalatest'
        project211['org.scalatest'].artifactId == 'scalatest_2.11'
        project211['org.scalatest'].version == '3.0.1'
        project211['org.scalatest'].scope == 'compile'
        project211['org.scala-lang'].groupId == 'org.scala-lang'
        project211['org.scala-lang'].artifactId == 'scala-library'
        project211['org.scala-lang'].version == '2.11.11'
        project211['org.scala-lang'].scope == 'compile'
        project211['com.google.guava'].groupId == 'com.google.guava'
        project211['com.google.guava'].artifactId == 'guava'
        project211['com.google.guava'].version == '18.0'
        project211['com.google.guava'].scope == 'compile'

        when:
        def projectLib2_210 = new XmlSlurper().parseText(lib2pom210).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        projectLib2_210.size() == 2
        projectLib2_210['org.scala-lang'].groupId == 'org.scala-lang'
        projectLib2_210['org.scala-lang'].artifactId == 'scala-library'
        projectLib2_210['org.scala-lang'].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.10']
        projectLib2_210['org.scala-lang'].scope == 'compile'
        projectLib2_210['com.github.prokod.it'].groupId == 'com.github.prokod.it'
        projectLib2_210['com.github.prokod.it'].artifactId == 'lib_2.10'
        projectLib2_210['com.github.prokod.it'].version == '1.0-SNAPSHOT'
        projectLib2_210['com.github.prokod.it'].scope == 'compile'

        when:
        def projectLib2_211 = new XmlSlurper().parseText(lib2pom211).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        projectLib2_211.size() == 2
        projectLib2_211['org.scala-lang'].groupId == 'org.scala-lang'
        projectLib2_211['org.scala-lang'].artifactId == 'scala-library'
        projectLib2_211['org.scala-lang'].version == '2.11.11'
        projectLib2_211['org.scala-lang'].scope == 'compile'
        projectLib2_211['com.github.prokod.it'].groupId == 'com.github.prokod.it'
        projectLib2_211['com.github.prokod.it'].artifactId == 'lib_2.11'
        projectLib2_211['com.github.prokod.it'].version == '1.0-SNAPSHOT'
        projectLib2_211['com.github.prokod.it'].scope == 'compile'

        where:
        gradleVersion   | defaultScalaVersion
        '5.6.4'         | '2.10'
        '6.9.2'         | '2.11'
        '7.3.3'         | '2.11'
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Eager</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x</li>
     *     <li>Running tasks that triggers main sourceset configurations: Yes</li>
     * </ul>
     * @return
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] should allow a project without crossbuild plugin to resolve the right dependencies"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
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
apply plugin: 'com.github.prokod.gradle-crossbuild'

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

crossBuild {
    builds {
        v211 
        v212
    }
}

dependencies {
    implementation "org.scalatest:scalatest_?:3.0.1"
    implementation "com.google.guava:guava:18.0"
    api "org.scala-lang:scala-library:${defaultScalaVersion}.+"
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
apply plugin: 'scala'

dependencies {
    implementation project(':lib')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(dir.root)
            .withPluginClasspath()
            .withDebug(true)
            .withArguments('crossBuildV212Jar', 'crossBuildV211Jar', 'build', '--info', '--stacktrace')
            .build()

        then:
        result.task(":lib:crossBuildV212Jar").outcome == SUCCESS
        result.task(":lib:crossBuildV211Jar").outcome == SUCCESS
        result.task(":lib:build").outcome == SUCCESS
        result.task(":app:build").outcome == SUCCESS
        where:
        gradleVersion | defaultScalaVersion
        '5.6.4'       | '2.11'
        '6.9.2'       | '2.12'
        '7.3.3'       | '2.11'
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
    }
}

subprojects {
    apply plugin: 'com.github.prokod.gradle-crossbuild'
    apply plugin: 'maven-publish'

    if (!project.name.endsWith('app')) {
        crossBuild {
            scalaVersionsCatalog = ['2.12':'2.12.8']
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
    ${scalazQmarked ? "api 'org.scalaz:scalaz-core_?:7.2.28'" : "api 'org.scalaz:scalaz-core_2.12:7.2.28'\ncrossBuildSpark230_211Api 'org.scalaz:scalaz-core_2.11:7.2.28'"}

    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        lib2ScalaFile.withWriter('utf-8') {it.write """
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
""" }

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
    implementation project(':lib')
    implementation 'org.scala-lang:scala-reflect:2.12.8'
    crossBuildSpark230_211Implementation 'org.scala-lang:scala-reflect:2.11.12'
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
    implementation project(':lib2')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
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
        '4.10.3'        | '2.11'              | false
        '5.6.4'         | '2.12'              | false
        '6.9.2'         | '2.12'              | false
        '7.3.3'         | '2.11'              | false
        '4.10.3'        | '2.11'              | true
        '5.6.4'         | '2.12'              | true
        '6.9.2'         | '2.12'              | true
        '7.3.3'         | '2.11'              | true
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
    api 'org.scalaz:scalaz-core_?:7.2.28'

    implementation "org.scala-lang:scala-library:${defaultScalaVersion}.+"
}
"""

        lib2ScalaFile.withWriter('utf-8') {it.write """
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
""" }

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
    implementation project(':lib')
    implementation 'org.scala-lang:scala-reflect:2.12.8'
    crossBuildSpark230_211Implementation 'org.scala-lang:scala-reflect:2.11.12'
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
    implementation project(':lib2')
}
"""

        when:
        Assume.assumeTrue(testMavenCentralAccess())
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(true)
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
        '4.10.3'        | '2.11'
        '5.6.4'         | '2.12'
        '6.9.2'         | '2.12'
        '7.3.3'         | '2.11'
    }
}