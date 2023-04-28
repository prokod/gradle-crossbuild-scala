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

class ScalaVersionInsightsTest extends Specification {

    def "Scala version insights validation"() {
        when:
        def scalaVersions = new ScalaVersions(['2.9':'2.9.3', '2.10':'2.10.6', '2.11':'2.11.12', '2.12':'2.12.12', '2.13':'2.13.3', '3':'3.2.2'])
        def insights = new ScalaVersionInsights(version, scalaVersions)

        then:
        insights.artifactInlinedVersion == artifactInlinedVersion
        and:
        insights.baseVersion == baseVersion
        and:
        insights.compilerVersion == compilerVersion
        and:
        insights.strippedArtifactInlinedVersion == strippedArtifactInlinedVersion
        and:
        insights.scalaCompilerDefaultTargetJvm == scalaCompilerDefaultTargetJvm

        where:
        version  | artifactInlinedVersion | baseVersion | compilerVersion | strippedArtifactInlinedVersion | scalaCompilerDefaultTargetJvm
        '2.9'    | '2.9.3'                | '2.9'       | '2.9.3'         | '293'                          | 'jvm-1.5'
        '2.10'   | '2.10'                 | '2.10'      | '2.10.6'        | '210'                          | 'jvm-1.6'
        '2.10.6' | '2.10'                 | '2.10'      | '2.10.6'        | '210'                          | 'jvm-1.6'
        '2.11'   | '2.11'                 | '2.11'      | '2.11.12'       | '211'                          | 'jvm-1.6'
        '2.11.12'| '2.11'                 | '2.11'      | '2.11.12'       | '211'                          | 'jvm-1.6'
        '2.11.11'| '2.11'                 | '2.11'      | '2.11.11'       | '211'                          | 'jvm-1.6'
        '2.12'   | '2.12'                 | '2.12'      | '2.12.12'       | '212'                          | 'jvm-1.8'
        '2.12.12'| '2.12'                 | '2.12'      | '2.12.12'       | '212'                          | 'jvm-1.8'
        '2.12.16'| '2.12'                 | '2.12'      | '2.12.16'       | '212'                          | 'jvm-1.8'
        '2.12.17'| '2.12'                 | '2.12'      | '2.12.17'       | '212'                          | 'jvm-1.8'
        '2.13'   | '2.13'                 | '2.13'      | '2.13.3'        | '213'                          | 'jvm-1.8'
        '2.13.3' | '2.13'                 | '2.13'      | '2.13.3'        | '213'                          | 'jvm-1.8'
        '2.13.0' | '2.13'                 | '2.13'      | '2.13.0'        | '213'                          | 'jvm-1.8'
        '3'      | '3'                    | '3'         | '3.2.2'         | '3'                            | '11'
        '3.2.2'  | '3'                    | '3'         | '3.2.2'         | '3'                            | '11'
    }
}
