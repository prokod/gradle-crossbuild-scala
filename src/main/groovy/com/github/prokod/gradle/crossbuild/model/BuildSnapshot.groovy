/*
 * Copyright 2018-2019 the original author or authors
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

    Set<String> scalaVersions

    static BuildSnapshot from(Build other) {
        new BuildSnapshot(other.name, other.extension, other.archive.appendixPattern, other.scalaVersions)
    }

    BuildSnapshot(String name, CrossBuildExtension extension, String appendixPattern, Set<String> scalaVersions) {
        this.name = name
        this.extension = extension
        this.archive = new ArchiveNamingSnapshot(name, appendixPattern)
        this.scalaVersions = scalaVersions.clone()
    }

    BuildSnapshot(BuildSnapshot other, ArchiveNamingSnapshot archive) {
        this(other.name, other.extension, archive.appendixPattern, other.scalaVersions)
        assert other.name == archive.name : "While instantiating snapshot build $other.name != $archive.name"
    }

    String toString() {
        JsonOutput.toJson([name:name,
                           scalaVersions:scalaVersions,
                           archive:[appendixPattern:archive?.appendixPattern]])
    }
}
