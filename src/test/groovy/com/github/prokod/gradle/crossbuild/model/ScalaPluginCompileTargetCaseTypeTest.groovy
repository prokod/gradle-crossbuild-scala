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
package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.ScalaVersions
import spock.lang.Specification

class ScalaPluginCompileTargetCaseTypeTest extends Specification {

    def "When Scala version insights getCompilerTargetJvm is called correct target flag value is returned"() {
        when:
        def scalaPluginCompileTargetCaseType = ScalaPluginCompileTargetCaseType.from(version)

        then:
        scalaPluginCompileTargetCaseType.name() == expected

        where:
        version   | expected
        '6.0.1'   | 'GRADLE_6_0_1'
        '7.3.0'   | 'GRADLE_6_0_1'
        '7.6.4'   | 'GRADLE_6_0_1'
        '8.0'     | 'GRADLE_8_0'
        '8.2'     | 'GRADLE_8_0'
        '8.5'     | 'GRADLE_8_5'
        '8.7'     | 'GRADLE_8_5'
    }
}
