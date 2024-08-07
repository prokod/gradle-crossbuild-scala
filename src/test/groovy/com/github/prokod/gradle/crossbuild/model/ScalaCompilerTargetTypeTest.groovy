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

class ScalaCompilerTargetTypeTest extends Specification {

    def "When Scala version insights getCompilerTargetJvm is called correct target flag value is returned"() {
        when:
        def scalaVersions = new ScalaVersions(['2.9':'2.9.3', '2.10':'2.10.6', '2.11':'2.11.12', '2.12':'2.12.12', '2.13':'2.13.3', '3':'3.2.2'])
        def insights = new ScalaVersionInsights(version, scalaVersions)

        then:
        def scalaCompilerTargetType = ScalaCompilerTargetType.from(insights.compilerVersion)
        def calculatedTargetValue = scalaCompilerTargetType
                .getCompilerTargetJvmValuesWithStrategy(ScalaCompilerTargetStrategyType.valueOf(strategy), targetCompatibility)
        def calculatedParameter = scalaCompilerTargetType.getTargetParameter()
        def calculatedTargetArg = scalaCompilerTargetType
                .getCompilerTargetJvmArgsWithStrategy(ScalaCompilerTargetStrategyType.valueOf(strategy), targetCompatibility)
        calculatedTargetValue == targetValue
        calculatedParameter == targetPaarameter
        calculatedTargetArg == targetArgs

        where:
        version   | strategy              | targetCompatibility  | targetValue                   | targetPaarameter | targetArgs
        '2.10.6'  | 'TOOLCHAIN_OR_MAX'    | '8'                  | new Tuple2('jvm-1.7', null)   | 'target'         | ['-target:jvm-1.7']
        '2.11.12' | 'TOOLCHAIN_OR_MAX'    | '11'                 | new Tuple2('jvm-1.8', null)   | 'target'         | ['-target:jvm-1.8']
        '2.11.12' | 'TOOLCHAIN_OR_DEFAULT'| '11'                 | new Tuple2('jvm-1.8', null)   | 'target'         | ['-target:jvm-1.8']
        '2.11.12' | 'TOOLCHAIN_OR_MAX'    | '8'                  | new Tuple2('jvm-1.8', null)   | 'target'         | ['-target:jvm-1.8']
        '2.11.12' | 'TOOLCHAIN_OR_DEFAULT'| '17'                 | new Tuple2('jvm-1.8', null)   | 'target'         | ['-target:jvm-1.8']
        '2.11.12' | 'TOOLCHAIN_OR_MAX'    | '1.8'                | new Tuple2('jvm-1.8', null)   | 'target'         | ['-target:jvm-1.8']
        '2.12.17' | 'TOOLCHAIN_OR_MAX'    | '8'                  | new Tuple2('8', '8')          | 'release'        | ['-target:8', '-release:8']
        '2.12.17' | 'TOOLCHAIN_OR_MAX'    | '1.8'                | new Tuple2('8', '8')          | 'release'        | ['-target:8', '-release:8']
        '2.12.19' | 'TOOLCHAIN_OR_MAX'    | '17'                 | new Tuple2('8', '17')         | 'release'        | ['-target:8', '-release:17']
        '2.12.10' | 'TOOLCHAIN_OR_MAX'    | '8'                  | new Tuple2('jvm-1.8', null)   | 'target'         | ['-target:jvm-1.8']
        '2.12.10' | 'TOOLCHAIN_OR_MAX'    | '1.8'                | new Tuple2('jvm-1.8', null)   | 'target'         | ['-target:jvm-1.8']
        '2.13.1'  | 'TOOLCHAIN_OR_MAX'    | '12'                 | new Tuple2('jvm-12', null)    | 'target'         | ['-target:jvm-12']
        '2.13.1'  | 'TOOLCHAIN_OR_DEFAULT'| '12'                 | new Tuple2('jvm-12', null)    | 'target'         | ['-target:jvm-12']
        '2.13.1'  | 'TOOLCHAIN_OR_MAX'    | '17'                 | new Tuple2('jvm-12', null)    | 'target'         | ['-target:jvm-12']
        '2.13.1'  | 'TOOLCHAIN_OR_DEFAULT'| '17'                 | new Tuple2('jvm-1.8', null)   | 'target'         | ['-target:jvm-1.8']
        '2.13.10' | 'TOOLCHAIN_OR_DEFAULT'| '11'                 | new Tuple2(null, '11')        | 'release'        | ['-release:11']
    }
}
