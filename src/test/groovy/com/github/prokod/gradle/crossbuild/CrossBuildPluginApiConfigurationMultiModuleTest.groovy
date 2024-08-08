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
    File lib3BuildFile
    File lib3JavaFile
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
        lib3BuildFile = file('lib3/build.gradle')
        lib3JavaFile = file('lib3/src/main/java/CompileTimeFactorialUtils.java')
        appBuildFile = file('app/build.gradle')
        appScalaFile = file('app/src/main/scala/HelloWorldApp.scala')
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Lazy</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x, 8.x</li>
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
    @Requires({ System.getProperty("java.version").startsWith('1.8') })
    @Requires({ instance.testMavenCentralAccess() })
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
            
            scalaVersionsCatalog = ['2.10': "${scalaVersionsCatalog['2.10']}", '2.11': "${scalaVersionsCatalog['2.11']}"]

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
                    afterEvaluate {
                        from components.crossBuildSpark160_210
                        // artifact crossBuildSpark160_210Jar
                    }
                }
                crossBuildSpark240_211(MavenPublication) {
                    afterEvaluate {
                        from components.crossBuildSpark240_211
                        // artifact crossBuildSpark240_211Jar
                    }
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
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('tasks', 'build', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:publishToMavenLocal").outcome == SUCCESS

        fileExists(dir.resolve('lib/build/libs/lib_2.10*.jar'))
        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        fileExists(dir.resolve('app/build/libs/app_2.10*.jar'))
        fileExists(dir.resolve('app/build/libs/app_2.11*.jar'))

        def pom210 = dir.resolve("lib${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def pom211 = dir.resolve("lib${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def pomApp210 = dir.resolve("app${File.separator}build${File.separator}generated-pom_2.10.xml").text

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
        gradleVersion   | defaultScalaVersion | scalaVersionsCatalog
        '7.6.4'         | '2.11'              | ['2.10': '2.10.6', '2.11': '2.11.11']
        '8.7'           | '2.11'              | ['2.10': '2.10.6', '2.11': '2.11.11']
    }

    /**
     * Test Properties:
     * <ul>
     *     <li>Plugin apply mode: Eager</li>
     *     <li>Gradle compatibility matrix: 5.x, 6.x, 7.x, 8.x</li>
     *     <li>Running tasks that triggers main sourceset configurations: Yes</li>
     * </ul>
     * NOTES:
     * <ul>
     *     <li>Gradle build is called in this test with NON specific cross compile tasks: 'build', 'publishToMavenLocal'.
     *     As a result, the main sourceset classpath configuration is triggered to get resolved. Usual issues like scala-library is missing in 'app' module
     *     than do pop up and so the 'user' HAVE TO use api configuration for implicit scala-library dependency inclusion, or to explicitly include
     *     scala-library dependency in all modules using implementation configuration as the plugin ONLY takes care of this behind the scenes for cross compile configurations.</li>
     * </ul>
     *
     * @see <a href="https://github.com/prokod/gradle-crossbuild-scala/issues/128">issue #128</a>
     */
    @Requires({ System.getProperty("java.version").startsWith('1.8') })
    @Requires({ instance.testMavenCentralAccess() })
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
                from components.crossBuildSpark160_210
                // artifact crossBuildSpark160_210Jar
            }
            crossBuildSpark240_211(MavenPublication) {
                from components.crossBuildSpark240_211
                // artifact crossBuildSpark240_211Jar
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
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                /*@withDebug@*/
                .withArguments('build', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.task(":lib:publishToMavenLocal").outcome == SUCCESS

        fileExists(dir.resolve('lib/build/libs/lib_2.10*.jar'))
        fileExists(dir.resolve('lib/build/libs/lib_2.11*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.10*.jar'))
        fileExists(dir.resolve('lib2/build/libs/lib2_2.11*.jar'))
        fileExists(dir.resolve('app/build/libs/app_2.10*.jar'))
        fileExists(dir.resolve('app/build/libs/app_2.11*.jar'))

        def pom210 = dir.resolve("lib${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def pom211 = dir.resolve("lib${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def lib2pom210 = dir.resolve("lib2${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def lib2pom211 = dir.resolve("lib2${File.separator}build${File.separator}generated-pom_2.11.xml").text
        def apppom210 = dir.resolve("app${File.separator}build${File.separator}generated-pom_2.10.xml").text
        def apppom211 = dir.resolve("app${File.separator}build${File.separator}generated-pom_2.11.xml").text

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
        project211['org.scala-lang'].version == '2.11.11'
        project211['org.scala-lang'].scope == 'runtime'
        project211['com.google.guava'].groupId == 'com.google.guava'
        project211['com.google.guava'].artifactId == 'guava'
        project211['com.google.guava'].version == '18.0'
        project211['com.google.guava'].scope == 'runtime'

        when:
        def projectLib2_210 = new XmlSlurper().parseText(lib2pom210).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        projectLib2_210.size() == 4
        projectLib2_210['org.scala-lang'].groupId == 'org.scala-lang'
        projectLib2_210['org.scala-lang'].artifactId == 'scala-library'
        projectLib2_210['org.scala-lang'].version == ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog['2.10']
        projectLib2_210['org.scala-lang'].scope == 'runtime'
        projectLib2_210['com.github.prokod.it'].groupId == 'com.github.prokod.it'
        projectLib2_210['com.github.prokod.it'].artifactId == 'lib_2.10'
        projectLib2_210['com.github.prokod.it'].version == '1.0-SNAPSHOT'
        // Should be scope compile as the dependency is declared as api
        // see https://github.com/prokod/gradle-crossbuild-scala/issues/128
        projectLib2_210['com.github.prokod.it'].scope == 'compile'
        projectLib2_210['com.google.guava'].groupId == 'com.google.guava'
        projectLib2_210['com.google.guava'].artifactId == 'guava'
        projectLib2_210['com.google.guava'].version == '18.0'
        projectLib2_210['com.google.guava'].scope == 'runtime'
        projectLib2_210['org.scalatest'].groupId == 'org.scalatest'
        projectLib2_210['org.scalatest'].artifactId == 'scalatest_2.10'
        projectLib2_210['org.scalatest'].version == '3.0.1'
        projectLib2_210['org.scalatest'].scope == 'runtime'

        when:
        def projectLib2_211 = new XmlSlurper().parseText(lib2pom211).dependencies.dependency.collectEntries{
            [it.groupId.text(), it]
        }

        then:
        projectLib2_211.size() == 4
        projectLib2_211['org.scala-lang'].groupId == 'org.scala-lang'
        projectLib2_211['org.scala-lang'].artifactId == 'scala-library'
        projectLib2_211['org.scala-lang'].version == '2.11.11'
        projectLib2_211['org.scala-lang'].scope == 'runtime'
        projectLib2_211['com.github.prokod.it'].groupId == 'com.github.prokod.it'
        projectLib2_211['com.github.prokod.it'].artifactId == 'lib_2.11'
        projectLib2_211['com.github.prokod.it'].version == '1.0-SNAPSHOT'
        projectLib2_211['com.github.prokod.it'].scope == 'compile'
        projectLib2_211['com.google.guava'].groupId == 'com.google.guava'
        projectLib2_211['com.google.guava'].artifactId == 'guava'
        projectLib2_211['com.google.guava'].version == '18.0'
        projectLib2_211['com.google.guava'].scope == 'runtime'
        projectLib2_211['org.scalatest'].groupId == 'org.scalatest'
        projectLib2_211['org.scalatest'].artifactId == 'scalatest_2.11'
        projectLib2_211['org.scalatest'].version == '3.0.1'
        projectLib2_211['org.scalatest'].scope == 'runtime'

        when:
        def projectApp_211 = new XmlSlurper().parseText(apppom211).dependencies.dependency.collectEntries{
            ["${it.groupId.text()}:${it.artifactId.text()}".toString(), it]
        }

        then:
        projectApp_211.size() == 5
        projectApp_211['org.scala-lang:scala-library'].groupId == 'org.scala-lang'
        projectApp_211['org.scala-lang:scala-library'].artifactId == 'scala-library'
        projectApp_211['org.scala-lang:scala-library'].version == '2.11.11'
        projectApp_211['org.scala-lang:scala-library'].scope == 'runtime'
        projectApp_211['com.github.prokod.it:lib_2.11'].groupId == 'com.github.prokod.it'
        projectApp_211['com.github.prokod.it:lib_2.11'].artifactId == 'lib_2.11'
        projectApp_211['com.github.prokod.it:lib_2.11'].version == '1.0-SNAPSHOT'
        projectApp_211['com.github.prokod.it:lib_2.11'].scope == 'runtime'
        projectApp_211['com.github.prokod.it:lib2_2.11'].groupId == 'com.github.prokod.it'
        projectApp_211['com.github.prokod.it:lib2_2.11'].artifactId == 'lib2_2.11'
        projectApp_211['com.github.prokod.it:lib2_2.11'].version == '1.0-SNAPSHOT'
        projectApp_211['com.github.prokod.it:lib2_2.11'].scope == 'runtime'
        projectApp_211['com.google.guava:guava'].groupId == 'com.google.guava'
        projectApp_211['com.google.guava:guava'].artifactId == 'guava'
        projectApp_211['com.google.guava:guava'].version == '18.0'
        projectApp_211['com.google.guava:guava'].scope == 'runtime'
        projectApp_211['org.scalatest:scalatest_2.11'].groupId == 'org.scalatest'
        projectApp_211['org.scalatest:scalatest_2.11'].artifactId == 'scalatest_2.11'
        projectApp_211['org.scalatest:scalatest_2.11'].version == '3.0.1'
        projectApp_211['org.scalatest:scalatest_2.11'].scope == 'runtime'

        where:
        gradleVersion   | defaultScalaVersion
        '7.6.4'         | '2.11'
        '8.7'           | '2.10'
    }
}