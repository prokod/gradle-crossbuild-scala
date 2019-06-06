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

import com.github.prokod.gradle.crossbuild.model.ArchiveNaming
import com.github.prokod.gradle.crossbuild.model.Build
import org.gradle.internal.impldep.org.junit.Assume
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginCompileMultiModuleTest1 extends CrossBuildGradleRunnerSpec {

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
     *
     * @param gradleVersion Gradle version i.e '4.2'
     * @param defaultScalaVersion i.e '2.12.8'
     * @param ap Default Appendix Pattern i.e '_?'
     * @param oap1 Override Appendix Pattern for cross build no. 1 i.e '-x-y-z_?'
     * @param oap2 Override Appendix Pattern for cross build no. 2
     * @param oap3 Override Appendix Pattern for cross build no. 3
     * @param eap1 Expected Appendix Pattern for cross build no. 1
     * @param eap2 Expected Appendix Pattern for cross build no. 2
     * @param eap3 Expected Appendix Pattern for cross build no. 3
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project with dependency graph of depth 3 and with `withPlugin` dsl should propagate the same plugin configuration to all sub projects"(
            String gradleVersion,
            String defaultScalaVersion,
            String ap,
            String oap1, String oap2, String oap3,
            String eap1, String eap2, String eap3
    ) {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
include 'lib2'
include 'app'
"""

        buildFile << """

plugins {
    id 'com.github.prokod.gradle-crossbuild1'
}

allprojects {
    apply plugin: 'java'
    group = 'com.github.prokod.it'
    version = '1.0-SNAPSHOT'
    
    repositories {
        mavenCentral()
    }
    
    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild1') {
        crossBuild {
            
            scalaVersions = ['2.11':'2.11.12']
        
            archive {
                appendixPattern = '${ap}'
            }
            builds {
                spark160_210 {
                    scala = '2.10'
                    ${oap1 != null ? 'archive.appendixPattern = \'' + oap1 + '\'': ''}
                }
                spark240_211 {
                    scala = '2.11'
                    ${oap2 != null ? 'archive.appendixPattern = \'' + oap2 + '\'' : ''}
                }
                spark241_212 {
                    scala = '2.12'
                        ${oap3 != null ? 'archive { appendixPattern = \'' + oap3 + '\' }': ''}
                }
            }
        }
    }
}
"""

        libBuildFile << """
import com.github.prokod.gradle.crossbuild.model.*

apply plugin: 'com.github.prokod.gradle-crossbuild1'

"""

        lib2BuildFile << """
import com.github.prokod.gradle.crossbuild.model.*

apply plugin: 'com.github.prokod.gradle-crossbuild1'

dependencies {
    compile project(':lib')
}
"""

        appBuildFile << """
import com.github.prokod.gradle.crossbuild.model.*

apply plugin: 'com.github.prokod.gradle-crossbuild1'

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
                .withDebug(true)
                .withArguments('builds', '--info', '--stacktrace')
                .build()

        then:
        result.task(":builds").outcome == SUCCESS
        result.task(":lib:builds").outcome == SUCCESS
        result.task(":lib2:builds").outcome == SUCCESS
        result.task(":app:builds").outcome == SUCCESS

        expect:
        def build1 = new Build('spark160_210').with { b ->
            scala = '2.10'
            archive = new ArchiveNaming(appendixPattern: eap1)
            b
        }
        def build2 = new Build('spark240_211').with { b ->
            scala = '2.11'
            archive = new ArchiveNaming(appendixPattern: eap2)
            b
        }
        def build3 = new Build('spark241_212').with { b ->
            scala = '2.12'
            archive = new ArchiveNaming(appendixPattern: eap3)
            b
        }
        def expected = [build1, build2, build3].collect {it.toString()}.join('\n')
        result.output.contains(expected)

        where:
        gradleVersion | defaultScalaVersion | ap   | oap1       | oap2       | oap3       | eap1       | eap2       | eap3
                '4.2' | '2.10'              | '_?' | null       | null       | null       | '_?'       | '_?'       | '_?'
                '4.2' | '2.10'              | '_?' | '-1-6-0_?' | '-2-4-0_?' | '-2-4-1_?' | '-1-6-0_?' | '-2-4-0_?' | '-2-4-1_?'
    }

    /**
     * Leveraging layout 1 of propagating cross build configs to sub projects
     * Layout 1 means:
     * <ul>
     *     <li>root-project</li>
     *     <ul>
     *         <li>{@code plugins} DSL for crossbuild plugin</li>
     *         <li>{@code allprojects} block with project.pluginManager.withPlugin({@link CrossBuildPlugin1}) containing crossbuld plugin DSL</li>
     *     </ul>
     *     <li>sub-projects</li>
     *     <ul>
     *         <li>{@code apply plugin:} {@link CrossBuildPlugin1}</li>
     *     </ul>
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
    id 'com.github.prokod.gradle-crossbuild1' apply false
}

allprojects {
    apply plugin: 'java'
    group = 'com.github.prokod.it'
    version = '1.0-SNAPSHOT'
    
    repositories {
        mavenCentral()
    }
    
    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild1') {
        crossBuild {
            
            scalaVersions = ['2.11':'2.11.11']

            builds {
                spark160_210 
                spark240_211
            }
        }
    }
    
    project.pluginManager.withPlugin('maven-publish') {
        publishing {
            publications {
                crossBuild210(MavenPublication) {
                    artifact crossBuild210Jar
                }
                crossBuild211(MavenPublication) {
                    artifact crossBuild211Jar
                }
            }
        }

        tasks.withType(GenerateMavenPom) { t ->
            if (t.name.contains('CrossBuild210')) {
                t.destination = file("\$buildDir/generated-pom_2.10.xml")
            }
            if (t.name.contains('CrossBuild211')) {
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
apply plugin: 'com.github.prokod.gradle-crossbuild1'
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
apply plugin: 'com.github.prokod.gradle-crossbuild1'
apply plugin: 'maven-publish'

dependencies {
    compile project(':lib')
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

        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.10.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.10.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.11.jar")

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
        '4.2'           | '2.10'
        '4.2'           | '2.11'
    }

    /**
     * Leveraging layout 2 of propagating cross build configs to sub projects
     * Layout 2 means:
     * <ul>
     *     <li>root-project</li>
     *     <ul>
     *         <li>{@code plugins} DSL for crossbuild plugin</li>
     *         <li>{@code subprojects} block with {@code apply plugin:} {@link CrossBuildPlugin1} followed by crossbuld plugin DSL</li>
     *     </ul>
     *     <li>sub-projects</li>
     *     <ul>
     *         <li>Nothing special</li>
     *     </ul>
     * </ul>
     * @return
     */
    @Unroll
    def "[gradle:#gradleVersion | default-scala-version:#defaultScalaVersion] applying crossbuild plugin on a multi-module project and calling crossBuildXXJar tasks on it should build correctly"() {
        given:
        // root project settings.gradle
        settingsFile << """
include 'lib'
include 'app'
"""

        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild1' apply false
}

allprojects {
    group = 'com.github.prokod.it'
    version = '1.0-SNAPSHOT'
    
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'com.github.prokod.gradle-crossbuild1'
    apply plugin: 'maven-publish'

    crossBuild {
        
        scalaVersions = ['2.11':'2.11.11']

        builds {
            spark160_210 
            spark240_211
        }
    }
    
    publishing {
        publications {
            crossBuild210(MavenPublication) {
                artifact crossBuild210Jar
            }
            crossBuild211(MavenPublication) {
                artifact crossBuild211Jar
            }
        }
    }

    tasks.withType(GenerateMavenPom) { t ->
        if (t.name.contains('CrossBuild210')) {
            t.destination = file("\$buildDir/generated-pom_2.10.xml")
        }
        if (t.name.contains('CrossBuild211')) {
            t.destination = file("\$buildDir/generated-pom_2.11.xml")
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
dependencies {
    compile project(':lib')
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
        result.task(":lib:crossBuild210Jar").outcome == SUCCESS
        result.task(":lib:crossBuild211Jar").outcome == SUCCESS
        result.task(":app:crossBuild210Jar").outcome == SUCCESS
        result.task(":app:crossBuild211Jar").outcome == SUCCESS

        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.10.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.10.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.11.jar")

        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.10'
        '4.2'           | '2.11'
    }

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
    id 'com.github.prokod.gradle-crossbuild1' apply false
}

allprojects {
    group = 'com.github.prokod.it'
    version = '1.0-SNAPSHOT'
    
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'com.github.prokod.gradle-crossbuild1'
    apply plugin: 'maven-publish'

    crossBuild {
        
        scalaVersions = ['2.11':'2.11.11']

        builds {
            spark160_210 
            spark240_211
        }
    }
    
    publishing {
        publications {
            crossBuild210(MavenPublication) {
                artifact crossBuild210Jar
            }
            crossBuild211(MavenPublication) {
                artifact crossBuild211Jar
            }
        }
    }

    tasks.withType(GenerateMavenPom) { t ->
        if (t.name.contains('CrossBuild210')) {
            t.destination = file("\$buildDir/generated-pom_2.10.xml")
        }
        if (t.name.contains('CrossBuild211')) {
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
    compile "org.scalatest:scalatest_?:3.0.1"
    compile "com.google.guava:guava:18.0"
    compile "org.scala-lang:scala-library:${defaultScalaVersion}.+"
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
    compile project(':lib')
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
    compile project(':lib')
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

        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.10.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib_2.11.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib2_2.10.jar")
        fileExists("$dir.root.absolutePath/lib/build/libs/lib2_2.11.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.10.jar")
        fileExists("$dir.root.absolutePath/app/build/libs/app_2.11.jar")

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

        def lib2pom210 = new File("${dir.root.absolutePath}${File.separator}lib2${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def lib2pom211 = new File("${dir.root.absolutePath}${File.separator}lib2${File.separator}build${File.separator}generated-pom_2.11.xml").text

        !lib2pom210.contains('2.11.')
        lib2pom210.contains('2.10.6')
        lib2pom210.contains('1.0-SNAPSHOT')
        !lib2pom211.contains('2.11.+')
        !lib2pom211.contains('2.10.')
        lib2pom211.contains('2.11.11')
        lib2pom211.contains('1.0-SNAPSHOT')
        where:
        gradleVersion   | defaultScalaVersion
        '4.2'           | '2.10'
        '4.2'           | '2.11'
    }
}