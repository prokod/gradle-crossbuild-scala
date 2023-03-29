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

import spock.lang.Specification

class ScalaCompilerTargetTypeTest extends Specification {

    def "When Scala version insights getCompilerTargetJvm is called correct target flag value is returned"() {
        when:
        def scalaVersions = new ScalaVersions(['2.9':'2.9.3', '2.10':'2.10.6', '2.11':'2.11.12', '2.12':'2.12.12', '2.13':'2.13.3', '3':'3.2.2'])
        def insights = new ScalaVersionInsights(version, scalaVersions)

        then:
        def calculatedTargetValue = ScalaCompilerTargetType.from(insights.compilerVersion)
                .getCompilerTargetJvm(ScalaCompilerTargetStrategyType.valueOf(strategy), targetCompatibility)
        calculatedTargetValue == targetValue

        where:
        version   | strategy | targetCompatibility  | targetValue
        '2.10.6'  | 'SMART'  | '8'                  | 'jvm-1.7'
        '2.11.12' | 'SMART'  | '11'                 | 'jvm-1.8'
        '2.11.12' | 'SMART'  | '8'                  | 'jvm-1.8'
        '2.11.12' | 'SMART'  | '1.8'                | 'jvm-1.8'
        '2.12.17' | 'SMART'  | '8'                  | 'jvm-1.8'
        '2.12.17' | 'SMART'  | '1.8'                | 'jvm-1.8'
        '2.12.10' | 'SMART'  | '8'                  | 'jvm-1.8'
        '2.12.10' | 'SMART'  | '1.8'                | 'jvm-1.8'
    }
}
