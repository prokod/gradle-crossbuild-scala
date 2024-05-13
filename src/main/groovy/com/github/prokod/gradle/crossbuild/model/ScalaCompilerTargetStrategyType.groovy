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

/**
 * <ul>
 *  <li>DEFAULT - Choose user's requested target JVM otherwise, default target JVM for the current Scala Compiler
 *  version</li>
 *  <li>MAX     - Use latest supported JVM for the given Scala Compiler version up-to user's requested target JVM</li>
 *  <li>FAIL    - Use requested JVM. If not supported raise exception</li>
 * </ul>
 */
@SuppressWarnings(['ThisReferenceEscapesConstructor',
        'PrivateFieldCouldBeFinal', 'Indentation', 'DuplicateListLiteral'])
enum ScalaCompilerTargetStrategyType {
    TOOLCHAIN_OR_DEFAULT({ ScalaCompilerTargetType t, String targetCompatibility ->
        if (targetCompatibility.isEmpty()) {
            return t.getCompilerTargetJvmValues(t.defaultTarget.toString())
        } else {
            def sanitized = sanitizeTargetCompatibility(targetCompatibility)
            if (sanitized.second <= t.maxTarget) {
                return t.getCompilerTargetJvmValues(sanitized.first)
            } else {
                return t.getCompilerTargetJvmValues(t.defaultTarget.toString())
            }
        }
    }),
    TOOLCHAIN_OR_MAX({ ScalaCompilerTargetType t, String targetCompatibility ->
        def sanitized = sanitizeTargetCompatibility(targetCompatibility)
        if (sanitized.second <= t.maxTarget) {
            return t.getCompilerTargetJvmValues(sanitized.first)
        } else {
            return t.getCompilerTargetJvmValues(t.maxTarget.toString())
        }
    }),
    TOOLCHAIN_OR_FAIL({ ScalaCompilerTargetType t, String targetCompatibility ->
        def sanitized = sanitizeTargetCompatibility(targetCompatibility)
        if (sanitized.second <= t.maxTarget) {
            t.getCompilerTargetJvmValues(sanitized.first)
        } else {
            throw new IllegalArgumentException("Requested target JVM [${targetCompatibility}] is not supported " +
                    "in Scala Compilers range starting with ${t.compilerVersion}.")
        }
    })

    private final Closure<Tuple2<String, String>> strategyFunction

    private ScalaCompilerTargetStrategyType(Closure<Tuple2<String, String>> strategyFunction) {
        this.strategyFunction = strategyFunction
    }

    Closure<Tuple2<String, String>> getStrategyFunction() {
        this.strategyFunction
    }

    private static Tuple2<String, Integer> sanitizeTargetCompatibility(String targetCompatibility) {
        def sanitizedTargetCompatibility = targetCompatibility
        if (sanitizedTargetCompatibility.contains('.')) {
            sanitizedTargetCompatibility = targetCompatibility[targetCompatibility.lastIndexOf('.') + 1..-1]
        }
        def sanitizedTargetCompatibilityAsNumber = Integer.parseInt(sanitizedTargetCompatibility)
        new Tuple2<String, Integer>(sanitizedTargetCompatibility, sanitizedTargetCompatibilityAsNumber)
    }
}
