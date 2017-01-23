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
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class CrossBuildPluginTest extends Specification {
    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile
    File propsFile

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        propsFile = testProjectDir.newFile('gradle.properties')
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with default archiveAppendix for each specified targetVersion should produce expected set of crossbuild jar task/s"() {
        given:
        buildFile << """
import com.github.prokod.gradle.crossbuild.model.*

plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

model {
    crossBuild {
        targetVersions {
            '2.10'(ScalaVer)
            '2.11'(ScalaVer)
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('tasks')
                .build()

        then:
        result.output.contains('crossBuild210Jar')
        result.output.contains('crossBuild211Jar')
        result.task(":tasks").outcome == SUCCESS

        where:
        gradleVersion << ['2.14.1', '3.0']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with default archiveAppendix for each specified targetVersion should produce expected set of crossbuild jar task/s with matching archive name"() {
        given:
        buildFile << """
import com.github.prokod.gradle.crossbuild.model.*

plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

model {
    crossBuild {
        targetVersions {
            '2.10'(ScalaVer)
            '2.11'(ScalaVer)
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('crossBuild210Jar', 'crossBuild211Jar', '--info')
                .build()

        then:
        result.output.contains('_2.10\'')
        result.output.contains('_2.11\'')
        result.task(":crossBuild210Jar").outcome == SUCCESS
        result.task(":crossBuild211Jar").outcome == SUCCESS

        where:
        gradleVersion << ['2.14.1', '3.0']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with custom archiveAppendix for each specified targetVersion should produce expected set of crossbuild jar task/s with matching archive name"() {
        given:
        propsFile << 'prop=A'

        buildFile << """
import com.github.prokod.gradle.crossbuild.model.*

plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

model {
    crossBuild {
        targetVersions {
            '2.10'(ScalaVer) {
                archiveAppendix = "_?_\${prop}"
            }
            '2.11'(ScalaVer) {
                archiveAppendix = "_?_\${prop}"
            }
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('crossBuild210Jar', 'crossBuild211Jar', '--info')
                .build()

        then:
        result.output.contains('_2.10_A\'')
        result.output.contains('_2.11_A\'')
        result.task(":crossBuild210Jar").outcome == SUCCESS
        result.task(":crossBuild211Jar").outcome == SUCCESS

        where:
        gradleVersion << ['2.14.1', '3.0']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with custom archiveAppendix and custom archivesBaseName for each specified targetVersion should produce expected set of crossbuild jar task/s with matching archive name"() {
        given:
        propsFile << 'prop=A'

        buildFile << """
import com.github.prokod.gradle.crossbuild.model.*

plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

model {
    crossBuild {
        targetVersions {
            archivesBaseName = 'mylib'
            '2.10'(ScalaVer) {
                archiveAppendix = "_?_\${prop}"
            }
            '2.11'(ScalaVer) {
                archiveAppendix = "_?_\${prop}"
            }
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('crossBuild210Jar', 'crossBuild211Jar', '--info')
                .build()

        then:
        result.output.contains('\'mylib_2.10_A\'')
        result.output.contains('\'mylib_2.11_A\'')
        result.task(":crossBuild210Jar").outcome == SUCCESS
        result.task(":crossBuild211Jar").outcome == SUCCESS

        where:
        gradleVersion << ['2.14.1', '3.0']
    }

    @Unroll
    def "[#gradleVersion] applying crossbuild plugin with custom target version value for each specified ScalaVer should produce expected set of crossbuild jar task/s with matching archive name"() {
        given:
        propsFile << 'prop=A'

        buildFile << """
import com.github.prokod.gradle.crossbuild.model.*

plugins {
    id 'com.github.prokod.gradle-crossbuild'
}

model {
    crossBuild {
        targetVersions {
            archivesBaseName = 'mylib'
            '2.10'(ScalaVer)
            '2.10_A'(ScalaVer) {
                value = '2.10'
                archiveAppendix = "_?_\${prop}"
            }
            '2.11'(ScalaVer)
            '2.11_A'(ScalaVer) {
                value = '2.11'
                archiveAppendix = "_?_\${prop}"
            }
        }
    }
}
"""

        when:
        def result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('crossBuild210Jar', 'crossBuild211Jar', 'crossBuild210_AJar', 'crossBuild211_AJar', '--info', '--stacktrace')
                .build()

        then:
        result.output.contains('\'mylib_2.10_A\'')
        result.output.contains('\'mylib_2.10\'')
        result.output.contains('\'mylib_2.11_A\'')
        result.output.contains('\'mylib_2.11\'')
        result.task(":crossBuild210Jar").outcome == SUCCESS
        result.task(":crossBuild211Jar").outcome == SUCCESS
        result.task(":crossBuild210_AJar").outcome == SUCCESS
        result.task(":crossBuild211_AJar").outcome == SUCCESS

        where:
        gradleVersion << ['2.14.1', '3.0']
    }
}
