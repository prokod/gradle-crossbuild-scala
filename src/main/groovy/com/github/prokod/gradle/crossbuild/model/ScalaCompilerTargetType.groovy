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
package com.github.prokod.gradle.crossbuild.model

import org.gradle.util.VersionNumber

/**
 * Notes:
 * <ul>
 *  <li>Partially based on https://github.com/scala/scala/blob/v2.x.x/src/compiler/scala/tools/nsc/settings/StandardScalaSettings.scala</li>
 *  <li>scalac compiler "target" JVM parameter was deprecated from 2.13.9 onward and superseded by "release" based on both:
 *       https://github.com/scala/bug/issues/12543 and https://github.com/scala/scala/pull/9982</li>
 *  <li>The below table maxTarget was cross referenced with https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html</li>
 *  <li>targetFunction should abide by https://docs.gradle.org/8.7/userguide/scala_plugin.html#sec:scala_target
 *       Currently 2.13.1 <= version < 2.13.9 range is an exception to this rule - should be set to {@code { v -> "${v}" }}</li>
 *  <li>Even though https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html#graalvm-native-image-compatibility-notes
 *      states that scala 2.11.12 can compile too jdk11 bytecode (55) it is not. So at least 2.11.12 default is set
 *      to jdk8</li>
 *  <li> Scala 2.13.0/1 supports also having JVM 9 and setting -release:9 but this cannot be described currently
 *       in this enum as it is arranged currently. So instead the plugin declares release flag as un supported for
 *       that Scala version</li>
 * </ul>
 *
 * v2.x.x should be replaced with a concrete version like v2.13.9 for instance
 */
@SuppressWarnings(['ParameterCount', 'ThisReferenceEscapesConstructor', 'BitwiseOperatorInConditional',
        'PrivateFieldCouldBeFinal', 'LineLength', 'DuplicateListLiteral', 'DuplicateNumberLiteral'])
enum ScalaCompilerTargetType {
    V2_9(VersionNumber.parse('2.9.0'), { v -> "jvm-1.${v}" }, { v -> null },'target',  5, 5),
    V2_10_0(VersionNumber.parse('2.10.0'), { v -> "jvm-1.${v}" }, { v -> null },'target', 6, 7),
    V2_11_0(VersionNumber.parse('2.11.0'), { v -> "jvm-1.${v}" }, { v -> null },'target', 6, 8),
    V2_11_12(VersionNumber.parse('2.11.12'), { v -> "jvm-1.${v}" }, { v -> null }, 'target', 8, 8),
    V2_12_0(VersionNumber.parse('2.12.0'), { v -> "jvm-1.${v}" }, { v -> null }, 'target', 8, 8),
    V2_12_4(VersionNumber.parse('2.12.4'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, { v -> null }, 'target', 8, 11),
    V2_12_15(VersionNumber.parse('2.12.15'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, { v -> null }, 'target', 8, 17),
    V2_12_16(VersionNumber.parse('2.12.16'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, { v -> null }, 'target', 8, 19),
    V2_12_17(VersionNumber.parse('2.12.17'), { v -> '8' }, { v -> "${v}" }, 'release', 8, 19),
    V2_12_18(VersionNumber.parse('2.12.18'), { v -> '8' }, { v -> "${v}" }, 'release', 8, 21),
    V2_12_19(VersionNumber.parse('2.12.19'), { v -> '8' }, { v -> "${v}" }, 'release', 8, 22),
    V2_13_0(VersionNumber.parse('2.13.0'), { v -> "jvm-1.${v}" }, { v -> null },'target', 8, 8),
    V2_13_1(VersionNumber.parse('2.13.1'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, { v -> null } ,'target', 8, 12),
    V2_13_6(VersionNumber.parse('2.13.6'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, { v -> null } , 'target',  8, 17),
    V2_13_7(VersionNumber.parse('2.13.7'), { v -> Integer.parseInt(v) == 8 ? "jvm-1.${v}" : "jvm-${v}" }, { v -> null } , 'target',  8, 18),
    V2_13_9(VersionNumber.parse('2.13.9'), { v -> null }, { v -> "${v}" },'release', 8, 19),
    V3_0_0(VersionNumber.parse('3.0.0'), { v -> null }, { v -> "${v}" },'release', 8, 17),
    V3_1_3(VersionNumber.parse('3.1.3'), { v -> null },  { v -> "${v}" }, 'release', 11, 18),
    V3_3_0(VersionNumber.parse('3.3.0'), { v -> null }, { v -> "${v}" }, 'release', 11, 20),
    V3_3_1(VersionNumber.parse('3.3.1'), { v -> null }, { v -> "${v}" }, 'release', 11, 21),
    V4_0_0(VersionNumber.parse('4.0.0'), { v -> null }, { v -> "${v}" }, 'release', 11, 21)

    private static Map<VersionNumber, ScalaCompilerTargetType> mappings
    private final VersionNumber compilerVersion
    private final Closure<String> targetFunction
    private final Closure<String> releaseFunction
    private final String targetParameter
    private final int defaultTarget
    private final int maxTarget

    private ScalaCompilerTargetType(VersionNumber compilerVersion,
                                    Closure<String> targetFunction,
                                    Closure<String> releaseFunction,
                                    String targetParameter,
                                    int defaultTarget,
                                    int maxTarget) {
        this.compilerVersion = compilerVersion
        this.targetFunction = targetFunction
        this.releaseFunction = releaseFunction
        this.targetParameter = targetParameter
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

    Closure<String> getReleaseFunction() {
        this.releaseFunction
    }

    String getTargetParameter() {
        this.targetParameter
    }

    int getDefaultTarget() {
        this.defaultTarget
    }

    int getMaxTarget() {
        this.maxTarget
    }

    Tuple2<String, String> getCompilerTargetJvmValues(String targetCompatibility) {
        def target = this.targetFunction.call(targetCompatibility)
        def release = this.releaseFunction.call(targetCompatibility)
        new Tuple2<String, String>(target, release)
    }

    Tuple2<String, String> getCompilerTargetJvmValuesWithStrategy(ScalaCompilerTargetStrategyType strategy,
                                                                  String targetCompatibility) {
        strategy.strategyFunction.call(this, targetCompatibility)
    }

    List<String> getCompilerTargetJvmArgsWithStrategy(ScalaCompilerTargetStrategyType strategy,
                                                      String targetCompatibility) {
        def targetJvmValues = getCompilerTargetJvmValuesWithStrategy(strategy, targetCompatibility)
        def targetJvmMap = ['target':targetJvmValues.first, 'release':targetJvmValues.second]
        targetJvmMap.findAll {  it.value != null }.collect { "-${it.key}:${it.value}".toString() }
    }

//    Tuple2<String, String> getCompilerTargetJvm(ScalaCompilerTargetStrategyType strategy, String targetCompatibility) {
//        strategy.strategyFunction.call(this, targetCompatibility)
//    }

    /**
     * Get default values
     *
     * @param strategy
     * @return
     */
    Tuple2<String, String> getCompilerTargetJvmValuesWithStrategy(ScalaCompilerTargetStrategyType strategy) {
        strategy.strategyFunction.call(this, '')
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
