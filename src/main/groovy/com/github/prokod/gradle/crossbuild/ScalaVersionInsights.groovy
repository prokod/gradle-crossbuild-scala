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

import com.github.prokod.gradle.crossbuild.model.ScalaCompilerTargetStrategyType
import com.github.prokod.gradle.crossbuild.model.ScalaCompilerTargetType
import groovy.transform.ToString
import groovy.transform.TupleConstructor

/**
 * Scala versioning semantics class
 */
@SuppressWarnings(['DuplicateNumberLiteral'])
@ToString(includes = 'baseVersion, underscoredBaseVersion, compilerVersion')
@TupleConstructor
class ScalaVersionInsights {
    String baseVersion
    String majorVersion
    String underscoredBaseVersion
    String compilerVersion
    String underscoredCompilerVersion
    String artifactInlinedVersion
    String underscoredArtifactInlinedVersion
    String strippedArtifactInlinedVersion
    Tuple2<String, String> scalaCompilerDefaultTargetJvm

    ScalaVersionInsights(String targetVersion, ScalaVersions scalaVersions = null) {
        insightFor(targetVersion, scalaVersions)

        underscoredBaseVersion = baseVersion.replaceAll('\\.', '_')
        underscoredCompilerVersion = compilerVersion.replaceAll('\\.', '_')
        underscoredArtifactInlinedVersion = artifactInlinedVersion.replaceAll('\\.', '_')
        strippedArtifactInlinedVersion = artifactInlinedVersion.replaceAll('\\.', '')

        scalaCompilerDefaultTargetJvm = ScalaCompilerTargetType.from(compilerVersion)
                .getCompilerTargetJvmValuesWithStrategy(ScalaCompilerTargetStrategyType.TOOLCHAIN_OR_DEFAULT)
    }

    private void insightFor(String version, ScalaVersions scalaVersions) {
        def dotsCount = version.length() - version.replaceAll('\\.', '').length()
        def (verifiedVersion, versionAsNumber) = parseTargetVersion(version, dotsCount, scalaVersions)

        // In Scala 3 binary compatibility is maintained across minor Scala compile versions
        // Scala 3 introduced scalaBinaryVersion=3 for all Scala 3 compiler versions
        if (dotsCount == 0 && versionAsNumber == 3 && scalaVersions != null) {
            baseVersion = verifiedVersion
            majorVersion = verifiedVersion
            compilerVersion = scalaVersions.getCompilerVersion(baseVersion)
            // Before scala 2.10 3rd party scala libs used compiler-version inlined artifact name
            artifactInlinedVersion = baseVersion
        }
        // Scala 2
        else if (dotsCount == 1 && scalaVersions != null) {
            baseVersion = verifiedVersion
            majorVersion = verifiedVersion[0..verifiedVersion.indexOf('.') - 1]
            compilerVersion = scalaVersions.getCompilerVersion(baseVersion)
            // Before scala 2.10 3rd party scala libs used compiler-version inlined artifact name
            artifactInlinedVersion = versionAsNumber < 100 ? compilerVersion : baseVersion
        }
        else if (dotsCount == 2) {
            // Scala 3
            if ((versionAsNumber - 300 >= 0 && versionAsNumber - 300 < 100)
                    || (versionAsNumber - 3000 >= 0 && versionAsNumber - 3000 < 1000)) {
                // In Scala 3 binary compatibility is maintained across minor Scala compile versions
                // Scala 3 introduced scalaBinaryVersion=3 for all Scala 3 compiler versions
                baseVersion = verifiedVersion[0..verifiedVersion.indexOf('.') - 1]
                majorVersion = verifiedVersion[0..verifiedVersion.indexOf('.') - 1]
                compilerVersion = verifiedVersion
                artifactInlinedVersion = baseVersion
            } else {
                baseVersion = verifiedVersion[0..verifiedVersion.lastIndexOf('.') - 1]
                majorVersion = baseVersion[0..baseVersion.indexOf('.') - 1]
                compilerVersion = verifiedVersion
                // Before scala 2.10 3rd party scala libs used compiler-version inlined artifact name
                artifactInlinedVersion = versionAsNumber > 1000 ? baseVersion : compilerVersion
            }
        } else {
            throw new IllegalArgumentException('Too many dot separator chars ' +
                    "in targetVersion [${version}].")
        }
    }

    /**
     * Parses target Scala version
     *
     * @param version Target version to parse
     * @param dotsCount No. of dots in target version
     * @param scalaVersions Scala versions catalog
     * @return A tuple containing the parsed version
     * @throws AssertionError if scalaVersions is not supplied in case that the original target version,
     *                         after removing dots, is not a number (2.10.+ -> 210+ not a number)
     * @throws IllegalStateException when target version cannot be parsed
     */
    private static Tuple2<String, Integer> parseTargetVersion(String version,
                                                              int dotsCount,
                                                              ScalaVersions scalaVersions) {
        def strippedDotsVersion = version.replaceAll('\\.', '')
        if (strippedDotsVersion.isNumber()) {
            def versionAsNumber = Integer.parseInt(strippedDotsVersion)
            return new Tuple2(version, versionAsNumber)
        } else {
            assert scalaVersions != null : "Supplied 'targetVersion' is probably of range type. In such" +
                    " cases supplied 'scalaVersions' (catalog) should not be null."
            def versionComponents = version.split('\\.')
            if (dotsCount == 2 && !versionComponents.last().isNumber()) {
                def newVersion = scalaVersions.getCompilerVersion("${versionComponents[0]}.${versionComponents[1]}")
                def newVersionAsNumber = Integer.parseInt(newVersion.replaceAll('\\.', ''))
                return new Tuple2(newVersion, newVersionAsNumber)
            } else {
                throw new IllegalStateException("Cannot get scala version insights from '$version'")
            }
        }
    }
}
