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

import static com.github.prokod.gradle.crossbuild.ResolutionStrategyHandler.*

/**
 * Abstraction similar to SBT for3use2_13/fore2_13use3
 * With the introduction of Scala 3 which introduced transitive dependency between scala 2.13 and 3, became the need
 * to detect that across different parts of the plugin
 */
@SuppressWarnings(['ThisReferenceEscapesConstructor', 'PrivateFieldCouldBeFinal', 'LineLength', 'DuplicateListLiteral'])
enum ForUseType {
    FOR3USE2_13,
    FOR2_13USE3,
    NONE

    /**
     * This detects a scenario where a ussr default scala lang dependency in build.gradle is 2.13 and cross compiling
     * currently compiles for scala 3
     *
     * @param requestedCoordinates
     * @param compiled
     * @return
     */
    static boolean isRequested2_13Compiled3(ScalaVersionInsights requested, ScalaVersionInsights compiled) {
        if (compiled.majorVersion == '3' && requested.baseVersion == '2.13') {
            return true
        }
        false
    }
}
