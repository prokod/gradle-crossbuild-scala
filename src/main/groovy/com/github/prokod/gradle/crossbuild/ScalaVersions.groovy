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

/**
 * A map of scala version to their default scala compiler version
 *
 */
class ScalaVersions {
    static final ScalaVersions DEFAULT_SCALA_VERSIONS =
            new ScalaVersions(['2.9':'2.9.3', '2.10':'2.10.6', '2.11':'2.11.11', '2.12':'2.12.3'])

    Map<String, String> catalog

    ScalaVersions(Map<String, String> catalog) {
        this.catalog = catalog
    }

    String getCompilerVersion( String scalaVersion ) {
        this.catalog[scalaVersion]
    }

    List<String> mkRefTargetVersions() {
        catalog.keySet().collect { new ScalaVersionInsights(it, this).artifactInlinedVersion }
    }
}
