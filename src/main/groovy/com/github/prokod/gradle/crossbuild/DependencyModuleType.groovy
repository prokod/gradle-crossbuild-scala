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
 * Abstraction for Scala lang module types
 * With the introduction of Scala 3 which introduced modified naming convention, became the need to cater for
 * both scala lang module naming convention
 */
@SuppressWarnings(['ThisReferenceEscapesConstructor', 'PrivateFieldCouldBeFinal', 'LineLength', 'DuplicateListLiteral'])
enum DependencyModuleType {
    NON_SCALA_3RD_PARTY_LIB([:]),
    SCALA_3RD_PARTY_LIB([:]),
    SCALA_LIBRARY(['2':'scala-library', '3':'scala3-library_3']),
    SCALA_COMPILER(['2':'scala-compiler', '3':'scala3-compiler_3']),
    SCALA_REFLECT(['2':'scala-reflect']),
    UNKNOWN([:])

    private final Map<String, String> namingConventionMap

    private DependencyModuleType(Map<String, String> namingConventionMap) {
        this.namingConventionMap = namingConventionMap
    }

    String getName(String scalaMajorVersion) {
        this.namingConventionMap.get(scalaMajorVersion)
    }

    Set<String> getNames() {
        this.namingConventionMap.values().toSet()
    }

    static DependencyModuleType findByName(String name) {
        def found = values().find { moduleType ->
            moduleType.names.contains(name)
        }
        found ?: UNKNOWN
    }

    /**
     * Converts module type based current compiled scala major version
     *
     * @return dependencyModuleType after conversion
     */
    static String convert(String name, String targetScalaMajorVersion) {
        def moduleType = findByName(name)
        moduleType.getName(targetScalaMajorVersion)
    }
}
