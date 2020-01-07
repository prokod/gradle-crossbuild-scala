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
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class CrossBuildPluginTest extends CrossBuildGradleRunnerSpec {
    File buildFile
    File propsFile

    def setup() {
        buildFile = file('build.gradle')
        propsFile = file('gradle.properties')
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with default values and no publishing plugin/block should produce expected set of crossbuild jar task(s) with matching resolved appendix for archive names when calling `tasks`"() {
        given:
        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

crossBuild {
    builds {
        v211
        v212
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('tasks', '--info', '--stacktrace')
                .build()

        then:
        result.output.contains('crossBuildV211Jar')
        !result.output.contains('publishCrossBuildV211PublicationToMavenLocal')
        result.output.contains('crossBuildV212Jar')
        !result.output.contains('publishCrossBuildV212PublicationToMavenLocal')
        result.output.contains("""
Crossbuilding tasks
-------------------
crossBuildResolvedConfigs - Summary report for cross building resolved Configurations
crossBuildResolvedDsl - Summary report for cross building resolved Dsl
""")
        result.task(":tasks").outcome == SUCCESS

        // TODO: consider removing/splitting test
        result.output.contains('_2.11]')
        result.output.contains('_2.12]')

        where:
        gradleVersion << ['4.2', '4.10.3', '5.6.4']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with crossBuild.archiveAppendix custom value and no publishing plugin/block should produce expected set of crossbuild jar task(s) with matching resolved appendix for archive names when calling `tasks`"() {
        given:
        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

crossBuild {
    archive {
        appendixPattern = '-2-4-3_?'
    }
    builds {
        v211
        v212
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('tasks', '--info', '--stacktrace')
                .build()

        then:
        result.output.contains('crossBuildV211Jar')
        !result.output.contains('publishCrossBuildV211PublicationToMavenLocal')
        result.output.contains('crossBuildV212Jar')
        !result.output.contains('publishCrossBuildV212PublicationToMavenLocal')
        result.output.contains('-2-4-3_2.11]')
        result.output.contains('-2-4-3_2.12]')
        result.task(":tasks").outcome == SUCCESS

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }

    @Unroll
    def "[#gradleVersion] applying noth crossbuild plugin and maven-publish plugin should produce expected set of crossbuild jar task(s) and publish task(s) when calling `tasks`"() {
        given:
        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
    id 'maven-publish'
}

group = 'project.group'

crossBuild {
    builds {
        v211
        v212
    }
}

publishing {
    publications {
        crossBuildV211(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildV211Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
        crossBuildV212(MavenPublication) {
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : 'afterEvaluate {'}
                artifact crossBuildV212Jar
            ${publishTaskSupportingDeferredConfiguration(gradleVersion) ? '' : '}'}
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('tasks', '--info', '--stacktrace')
                .build()

        then:
        result.output.contains('crossBuildV211Jar')
        result.output.contains('publishCrossBuildV211PublicationToMavenLocal')
        result.output.contains('crossBuildV212Jar')
        result.output.contains('publishCrossBuildV212PublicationToMavenLocal')
        result.task(":tasks").outcome == SUCCESS

        where:
        gradleVersion << ['4.2','4.10.3', '5.4.1']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with defaults and calling `crossBuildXXXJar` tasks, should produce expected set of crossbuild jar task(s) with matching resolved appendix for archive names"() {
        given:
        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

crossBuild {
    builds {
        v211
        v212
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('crossBuildV211Jar', 'crossBuildV212Jar', '--info', '--stacktrace')
                .build()

        then:
        result.output.contains('_2.11]')
        result.output.contains('_2.12]')
        result.task(":crossBuildV211Jar").outcome == SUCCESS
        result.task(":crossBuildV212Jar").outcome == SUCCESS

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with custom archiveAppendix for each specified targetVersion should produce expected set of crossbuild jar task(s) with matching resolved appendix for archive names"() {
        given:
        propsFile << 'prop=A'

        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

crossBuild {
    builds {
        v210 {
            archive.appendixPattern = "_?_\${prop}"
        }
        v211 {
            archive.appendixPattern = "_?_\${prop}"
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('crossBuildV210Jar', 'crossBuildV211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.output.contains('_2.10_A]')
        result.output.contains('_2.11_A]')
        result.task(":crossBuildV210Jar").outcome == SUCCESS
        result.task(":crossBuildV211Jar").outcome == SUCCESS

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with custom archiveAppendix and custom archivesBaseName for each specified targetVersion should produce expected set of crossbuild jar task/s with matching archive names"() {
        given:
        propsFile << 'prop=A'

        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
    id 'maven-publish'
}

archivesBaseName = 'mylib'

crossBuild {
    builds {
        v210 {
            archive.appendixPattern = "_?_\${prop}"
        }
        v211 {
            archive.appendixPattern = "_?_\${prop}"
        }
    }
}

gradle.projectsEvaluated {
    logger.info('__' + tasks.crossBuildV210Jar.baseName + '__')
    logger.info('__' + tasks.crossBuildV211Jar.baseName + '__')
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withDebug(false)
                .withArguments('crossBuildV210Jar', 'crossBuildV211Jar', '--debug', '--stacktrace')
                .build()

        then:
        result.output.contains('__mylib_2.10_A__')
        result.output.contains('__mylib_2.11_A__')
        result.task(":crossBuildV210Jar").outcome == SUCCESS
        result.task(":crossBuildV211Jar").outcome == SUCCESS

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with custom scala version value for each specified build item should produce expected set of crossbuild jar task/s with matching archive names"() {
        given:
        propsFile << 'prop=A'

        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

archivesBaseName = 'mylib'

crossBuild {
    scalaVersionsCatalog = ['2.13':'2.13.0']
    builds {
        v210
        v211 {
            scalaVersions = ['2.11', '2.12']
        }
        v211_A {
            scalaVersions = ['2.11']
            archive.appendixPattern = "_?_\${prop}"
        }
        v212 {
            scalaVersions = ['2.13']
            archive.appendixPattern = "-2-12_?"
        }
        v213 {
            scalaVersions = ['2.13']
        }
        spark24 {
            scalaVersions = ['2.11', '2.12']
            archive.appendixPattern = "-2-4-3_?"
        }
    }
}

gradle.projectsEvaluated {
    logger.info('__' + tasks.crossBuildV210Jar.baseName + '__')
    logger.info('__' + tasks.crossBuildV211_211Jar.baseName + '__')
    logger.info('__' + tasks.crossBuildV211_212Jar.baseName + '__')
    logger.info('__' + tasks.crossBuildV211_A_211Jar.baseName + '__')
    logger.info('__' + tasks.crossBuildV212_213Jar.baseName + '__')
    logger.info('__' + tasks.crossBuildV213Jar.baseName + '__')
    logger.info('__' + tasks.crossBuildSpark24_211Jar.baseName + '__')
    logger.info('__' + tasks.crossBuildSpark24_212Jar.baseName + '__')
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withDebug(false)
                .withPluginClasspath()
                .withArguments('crossBuildV210Jar',
                        'crossBuildV211_211Jar',
                        'crossBuildV211_212Jar',
                        'crossBuildV211_A_211Jar',
                        'crossBuildV212_213Jar',
                        'crossBuildV213Jar',
                        'crossBuildSpark24_211Jar',
                        'crossBuildSpark24_212Jar',
                        '--info', '--stacktrace')
                .build()

        then:
        result.output.contains('__mylib_2.10__')
        result.output.contains('__mylib_2.11__')
        result.output.contains('__mylib_2.12__')
        result.output.contains('__mylib_2.11_A__')
        result.output.contains('__mylib-2-12_2.13__')
        result.output.contains('__mylib_2.13__')
        result.output.contains('__mylib-2-4-3_2.11__')
        result.output.contains('__mylib-2-4-3_2.12__')
        result.task(":crossBuildV210Jar").outcome == SUCCESS
        result.task(":crossBuildV211_211Jar").outcome == SUCCESS
        result.task(":crossBuildV211_212Jar").outcome == SUCCESS
        result.task(":crossBuildV211_A_211Jar").outcome == SUCCESS
        result.task(":crossBuildV212_213Jar").outcome == SUCCESS
        result.task(":crossBuildV213Jar").outcome == SUCCESS
        result.task(":crossBuildSpark24_211Jar").outcome == SUCCESS
        result.task(":crossBuildSpark24_212Jar").outcome == SUCCESS

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with custom scala version value for each specified build item should produce expected set of crossbuild jar task/s when calling `tasks`"() {
        given:
        propsFile << 'prop=A'

        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

crossBuild {
    scalaVersionsCatalog = ['2.13':'2.13.0']
    builds {
        v210
        v211 {
            scalaVersions = ['2.11', '2.12']
        }
        v211_A {
            scalaVersions = ['2.11']
            archive.appendixPattern = "_?_\${prop}"
        }
        v212 {
            scalaVersions = ['2.13']
        }
        v213 {
            scalaVersions = ['2.13']
        }
        spark24 {
            scalaVersions = ['2.11', '2.12']
            archive.appendixPattern = "-2-4-3_?"
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withDebug(false)
                .withPluginClasspath()
                .withArguments('tasks',
                        '--info', '--stacktrace')
                .build()

        then:
        result.output.contains("""
crossBuildSpark24_211Classes - Assembles cross build spark24 211 classes.
crossBuildSpark24_211Jar - Assembles a jar archive containing 211 classes
crossBuildSpark24_212Classes - Assembles cross build spark24 212 classes.
crossBuildSpark24_212Jar - Assembles a jar archive containing 212 classes
crossBuildV210Classes - Assembles cross build v210 classes.
crossBuildV210Jar - Assembles a jar archive containing 210 classes
crossBuildV211_211Classes - Assembles cross build v211 211 classes.
crossBuildV211_211Jar - Assembles a jar archive containing 211 classes
crossBuildV211_212Classes - Assembles cross build v211 212 classes.
crossBuildV211_212Jar - Assembles a jar archive containing 212 classes
crossBuildV211_A_211Classes - Assembles cross build v211 a 211 classes.
crossBuildV211_A_211Jar - Assembles a jar archive containing 211 classes
crossBuildV212_213Classes - Assembles cross build v212 213 classes.
crossBuildV212_213Jar - Assembles a jar archive containing 213 classes
crossBuildV213Classes - Assembles cross build v213 classes.
crossBuildV213Jar - Assembles a jar archive containing 213 classes
""")
        result.task(":tasks").outcome == SUCCESS

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with default archiveAppendix for each specified targetVersion and calling `publishToMavenLocal` should produce expected set of crossbuild jar task/s with matching archive name and expected matching publications"() {
        given:
        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
    id 'maven-publish'
}

group = 'test'

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
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('crossBuildV210Jar', 'crossBuildV211Jar', 'publishToMavenLocal', '--info', '--stacktrace')
                .build()

        then:
        result.output.contains('_2.10')
        result.output.contains('_2.11')
        result.task(":crossBuildV210Jar").outcome == SUCCESS
        result.task(":publishCrossBuildV210PublicationToMavenLocal").outcome == SUCCESS
        result.task(":crossBuildV211Jar").outcome == SUCCESS
        result.task(":publishCrossBuildV211PublicationToMavenLocal").outcome == SUCCESS

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }

    @Unroll
    @Ignore
    def "[#gradleVersion] applying crossbuild plugin with default archiveAppendix for each specified targetVersion and adding user defined configurations should not cause any exception"() {
        given:
        buildFile << """
import com.github.prokod.gradle.crossbuild.model.*

plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

group = 'test'

model {
    crossBuild {
        targetVersions {
            v210(ScalaVer)
            v211(ScalaVer)
            
            dependencyResolution.includes = [configurations.integrationTestCompile]
        }
    }
}

sourceSets {
    integrationTest
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('crossBuild210Jar', 'crossBuild211Jar', '--info', '--stacktrace')
                .build()

        then:
        result.output.contains('_2.10\'')
        result.output.contains('_2.11\'')
        result.task(":crossBuild210Jar").outcome == SUCCESS
        result.task(":crossBuild211Jar").outcome == SUCCESS

        where:
        gradleVersion << ['2.14.1', '3.0', '4.1']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with default archiveAppendix and value for each specified targetVersion should throw when scalaVersions catalog is missing needed version"() {
        given:
        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

crossBuild {
    builds {
        v213 {
            scalaVersion = '2.13'
        }
    }
}
"""

        when:
        GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('crossBuild213Jar', '--info')
                .build()

        then:
        thrown(RuntimeException)

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with build name following convention for implicit version and `build.scalaVersion` for explicit version, when the values conflict, then exception is raised"() {
        given:
        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

crossBuild {
    scalaVersionsCatalog = ['2.13':'2.13.0']
    
    builds {
        v212 {
            scalaVersion = '2.13'
        }
    }
}
"""

        when:
        GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('crossBuild213Jar', '--info')
                .build()

        then:
        thrown(RuntimeException)

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with build name following convention for implicit version and `build.scalaVersion` for explicit version, when the values are aligned, then crossBuildJar ask outcome should be success"() {
        given:
        buildFile << """
plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

crossBuild {
    scalaVersionsCatalog = ['2.13':'2.13.0']
    
    builds {
        v213 {
            scalaVersions = ['2.13']
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(dir.root)
                .withPluginClasspath()
                .withArguments('crossBuildV213Jar', '--info')
                .build()

        then:
        result.task(":crossBuildV213Jar").outcome == SUCCESS

        where:
        gradleVersion << ['4.2', '4.10.3', '5.4.1']
    }
}
