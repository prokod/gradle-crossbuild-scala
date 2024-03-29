/*
 * Copyright 2018-2022 the original author or authors
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

import com.github.prokod.gradle.crossbuild.CrossBuildExtension
import groovy.json.JsonOutput

/**
 * cross build plugin DSL representation for individual build items in {@code builds} block
 */
class BuildSnapshot {
    final String name

    final CrossBuildExtension extension

    final ArchiveNamingSnapshot archive

    final TargetCompatibilitySnapshot targetCompatibility

    Set<String> scalaVersions

    Map<String, Object> ext

    static BuildSnapshot from(Build other) {
        new BuildSnapshot(other.name,
                          other.extension,
                          other.archive.appendixPattern,
                          other.targetCompatibility.strategy,
                          other.scalaVersions,
                          other.ext ?: [:])
    }

    @SuppressWarnings('ParameterCount')
    BuildSnapshot(String name,
                  CrossBuildExtension extension,
                  String appendixPattern,
                  String strategy,
                  Set<String> scalaVersions,
                  Map<String, Object> ext) {
        this.name = name
        this.extension = extension
        this.archive = new ArchiveNamingSnapshot(name, appendixPattern)
        this.targetCompatibility = new TargetCompatibilitySnapshot(name, strategy)
        this.scalaVersions = scalaVersions.clone()
        this.ext = ext.clone()
    }

    BuildSnapshot(BuildSnapshot other, ArchiveNamingSnapshot archive) {
        this(other.name, other.extension,
                archive.appendixPattern,
                other.targetCompatibility.strategy,
                other.scalaVersions, other.ext)
        assert other.name == archive.name : "While instantiating snapshot build $other.name != $archive.name"
    }

    BuildSnapshot(BuildSnapshot other, TargetCompatibilitySnapshot targetCompatibility) {
        this(other.name, other.extension,
                other.archive.appendixPattern,
                targetCompatibility.strategy,
                other.scalaVersions, other.ext)
        assert other.name == targetCompatibility.name : 'While instantiating snapshot build ' +
                "$other.name != $targetCompatibility.name"
    }

    BuildSnapshot(BuildSnapshot other, Map<String, Object> ext) {
        this(other.name, other.extension,
                other.archive.appendixPattern,
                other.targetCompatibility.strategy,
                other.scalaVersions, ext)
        assert other.name == targetCompatibility.name : 'While instantiating snapshot build ' +
                "$other.name != $targetCompatibility.name"
    }

    String toString() {
        JsonOutput.toJson([name:name,
                           scalaVersions:scalaVersions,
                           ext:ext,
                           archive:[appendixPattern:archive?.appendixPattern],
                           targetCompatibility:[strategy:targetCompatibility?.strategy]])
    }
}
