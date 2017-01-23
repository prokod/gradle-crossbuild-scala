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

import com.github.prokod.gradle.crossbuild.model.TargetVerItem

class ScalaVersionInsights {
    String baseVersion
    String underscoredBaseVersion
    String compilerVersion
    String underscoredCompilerVersion
    String artifactInlinedVersion
    String underscoredArtifactInlinedVersion
    String strippedArtifactInlinedVersion

    ScalaVersionInsights(TargetVerItem targetVersion, ScalaVersionCatalog scalaVersionCatalog) {
        def dotsCount = targetVersion.value.length() - targetVersion.value.replaceAll("\\.", "").length()
        def targetVersionAsNumber = Integer.parseInt(targetVersion.value.replaceAll("\\.", ""))
        if (dotsCount == 1) {
            baseVersion = targetVersion.value
            compilerVersion = scalaVersionCatalog.getScalaCompilerVersion(targetVersion.value)
            artifactInlinedVersion = targetVersionAsNumber < 100 ? compilerVersion : targetVersion.value // Before scala 2.10 3rd party scala libs used compiler-version inlined artifact name
        }
        else if (dotsCount == 2) {
            baseVersion = targetVersion.value.substring(0, targetVersion.lastIndexOf("\\."))
            compilerVersion = targetVersion.value
            artifactInlinedVersion = targetVersionAsNumber > 1000 ? targetVersion.value.substring(0, targetVersion.value.lastIndexOf("\\.")) : targetVersion.value // Before scala 2.10 3rd party scala libs used compiler-version inlined artifact name
        } else {
            throw new IllegalArgumentException("Too many dot separator chars in targetVersion [${targetVersion.value}].")
        }

        underscoredBaseVersion = baseVersion.replaceAll("\\.", "_")
        underscoredCompilerVersion = compilerVersion.replaceAll("\\.", "_")
        underscoredArtifactInlinedVersion = artifactInlinedVersion.replaceAll("\\.", "_")
        strippedArtifactInlinedVersion = targetVersion.name == targetVersion.value ? artifactInlinedVersion.replaceAll("\\.", "") : targetVersion.name.replaceAll("\\.", "")
    }
}
