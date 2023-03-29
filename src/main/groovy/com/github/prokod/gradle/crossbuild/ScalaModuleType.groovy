/*
 * Copyright 2020-2023 the original author or authors
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
 */
@SuppressWarnings(['ThisReferenceEscapesConstructor', 'PrivateFieldCouldBeFinal', 'LineLength', 'DuplicateListLiteral'])
enum ScalaModuleType {
    LIBRARY(['2':'scala-library', '3':'scala3-library_3']),
    COMPILER(['2':'scala-compiler', '3':'scala3-compiler_3']),
    REFLECT(['2':'scala-reflect'])

    private final Map<String, String> namingConventionMap

    private ScalaModuleType(Map<String, String> namingConventionMap) {
        this.namingConventionMap = namingConventionMap
    }

    String getName(String scalaMajorVersion) {
        this.namingConventionMap.get(scalaMajorVersion)
    }

    Set<String> getNames() {
        this.namingConventionMap.values().toSet()
    }

    /**
     * All ViewType filtered by given tags
     *
     * @return
     */
    static String convert(String name, String targetScalaVersion) {
        def moduleType = values().find { moduleType ->
            moduleType.names.contains(name)
        }

        moduleType.getName(targetScalaVersion)
    }
}
