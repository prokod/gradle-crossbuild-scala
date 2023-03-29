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

import org.gradle.util.VersionNumber

/**
 */
@SuppressWarnings(['ThisReferenceEscapesConstructor', 'BitwiseOperatorInConditional',
        'PrivateFieldCouldBeFinal', 'LineLength', 'DuplicateListLiteral', 'DuplicateNumberLiteral'])
enum ScalaCompilerTargetType {
    V2_9(VersionNumber.parse('2.9.0'), { v -> "jvm-1.${v}" }, 5, 5),
    V2_10_0(VersionNumber.parse('2.10.0'), { v -> "jvm-1.${v}" }, 6, 8),
    V2_11_0(VersionNumber.parse('2.11.0'), { v -> "jvm-1.${v}" }, 6, 8),
    //V2_11_12(VersionNumber.parse('2.11.12'), { v -> "jvm-1.${v}" }, 8, 8),
    V2_12_0(VersionNumber.parse('2.12.0'), { v -> "jvm-1.${v}" }, 8, 8),
    V2_12_16(VersionNumber.parse('2.12.16'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, 8, 19),
    V2_13_0(VersionNumber.parse('2.13.0'), { v -> "jvm-1.${v}" }, 8, 8),
    V2_13_1(VersionNumber.parse('2.13.1'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, 8, 12),
    V2_13_5(VersionNumber.parse('2.13.5'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, 8, 17),
    V2_13_7(VersionNumber.parse('2.13.7'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, 8, 18),
    V2_13_9(VersionNumber.parse('2.13.9'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, 8, 19),
    V3_0_0(VersionNumber.parse('3.0.0'), { v -> "${v}" }, 8, 17),
    V3_1_3(VersionNumber.parse('3.1.3'), { v -> "${v}" }, 11, 18),
    V3_2_0(VersionNumber.parse('3.2.0'), { v -> "${v}" }, 11, 19),
    V4_0_0(VersionNumber.parse('4.0.0'), { v -> "${v}" }, 11, 19)

    private static Map<VersionNumber, ScalaCompilerTargetType> mappings
    private final VersionNumber compilerVersion
    private final Closure<String> targetFunction
    private final int defaultTarget
    private final int maxTarget

    private ScalaCompilerTargetType(VersionNumber compilerVersion,
                                    Closure<String> targetFunction,
                                    int defaultTarget,
                                    int maxTarget) {
        this.compilerVersion = compilerVersion
        this.targetFunction = targetFunction
        this.defaultTarget = defaultTarget
        this.maxTarget = maxTarget
        mappings = mappings ?: [:]
        mappings.put(compilerVersion, this)
    }

    VersionNumber getCompilerVersion() {
        this.compilerVersion
    }

    Closure<String> getTargetFunction() {
        this.targetFunction
    }

    int getDefaultTarget() {
        this.defaultTarget
    }

    int getMaxTarget() {
        this.maxTarget
    }

    String getCompilerDefaultTarget() {
        this.targetFunction.call(this.defaultTarget.toString())
    }

    String getCompilerTargetJvm(ScalaCompilerTargetStrategyType strategy, String targetCompatibility) {
        strategy.strategyFunction.call(this, targetCompatibility)
    }

    /**
     * Convert any Scala Compiler version to matching ScalaCompilerTargetType
     *
     * @return ScalaCompilerTargetType
     */
    static ScalaCompilerTargetType from(String compilerVersion) {
        def sanitizedVersion = VersionNumber.parse(compilerVersion)
        def evens = [], odds = []
        values()*.compilerVersion.eachWithIndex { v, ix -> ( ix & 1 ? odds : evens ) << v }
        def ranges = [evens, odds].transpose() + [odds, evens[1..-1]].transpose()
        def range = ranges.find { v1, v2 -> sanitizedVersion < v2 && sanitizedVersion >= v1 }
        def relevantVersion = range[0]
        mappings.get(relevantVersion)
    }
}
