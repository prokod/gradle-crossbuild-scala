/*
 * Copyright 2023-2024 the original author or authors
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

import org.gradle.api.tasks.scala.ScalaCompileOptions
import org.gradle.util.VersionNumber

/**
 * <ul>
 *  <li>GRADLE_8_5</li>
 *      <ul>
 *          <li>{@code scalaCompileOptionsConfigurer#configure} Gradle by itself is aware already of both 'target' and
 *          'release' flags and skips setting any such flag internally for any compileScala task in case one of the
 *          flags is set by user</li>
 *          <li>{@code ScalaCompileOptionsConfigurer#determineTargetParameter} already configures scalac with
 *          '-release' parameter also</li>
 *      </ul>
 *  <li>GRADLE_8_0</li>
 *      <ul>
 *          <li>{@code scalaCompileOptionsConfigurer#configure} Gradle by itself is aware already of both 'target' and
 *          'release' flags and skips setting any such flag internally for any compileScala task in case one of the
 *          flags is set by user</li>
 *          <li>{@code ScalaCompileOptionsConfigurer#determineTargetParameter} configures scalac with
 *          '-target' parameter only</li>
 *      </ul>
 *  <li>GRADLE_LT_8_0 - Gradle is only aware of 'target' flag</li>
 *      <ul>
 *          <li>{@code scalaCompileOptionsConfigurer#configure} Gradle by itself is only aware of 'target' flag
 *          and skips setting any only that flag internally for any compileScala task in case this flag is set
 *          by user</li>
 *          <li>{@code ScalaCompileOptionsConfigurer#determineTargetParameter} configures scalac with
 *          '-target' parameter only</li>
 *      </ul>
 * </ul>
 *
 * Notes:
 * <ul>
 *     <li>Based on ScalaCompileOptionsConfigurer from https://github.com/gradle/gradle/blob/v7.6.4/subprojects/scala/src/main/java/org/gradle/api/tasks/scala/internal/ScalaCompileOptionsConfigurer.java</li>
 *     <li>Based on ScalaCompileOptionsConfigurer from https://github.com/gradle/gradle/blob/v8.5.0/platforms/jvm/scala/src/main/java/org/gradle/api/tasks/scala/internal/ScalaCompileOptionsConfigurer.java</li>
 *     <li>Based on StandardScalaSettings from https://github.com/scala/scala/blob/v2.12.17/src/compiler/scala/tools/nsc/settings/StandardScalaSettings.scala</li>
 * </ul>
 */
@SuppressWarnings(['LineLength', 'BitwiseOperatorInConditional', 'ThisReferenceEscapesConstructor',
        'PrivateFieldCouldBeFinal', 'Indentation', 'DuplicateListLiteral'])
enum ScalaPluginCompileTargetCaseType {
    GRADLE_6_0_1(VersionNumber.parse('6.0.1'), { ScalaCompilerTargetType targetType,
                    ScalaCompilerTargetStrategyType strategy,
                    ScalaCompileOptions scalaCompileOptions,
                    String targetCompatibility ->
//        def normalized = normalizeTargetCompatibility(targetCompatibility).toInteger()
        def compilerTargetArgs = targetType.getCompilerTargetJvmArgsWithStrategy(strategy, targetCompatibility)

        if (isNullOrEmpty(scalaCompileOptions)) {
            return new Tuple2(compilerTargetArgs, null)
        }
        else {
            if (containsFailOnWarnings(scalaCompileOptions) && compilerTargetArgs.find { it.startsWith('-release:') }) {
                return new Tuple2(compilerTargetArgs, { gradleVersion ->
                        "Cannot reconcile Gradle version: ${gradleVersion}, " +
                        " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}," +
                        " User scalac flags: ${scalaCompileOptions.additionalParameters.join(', ')}" +
                        " and scalac with minimum compiler version: ${targetType.compilerVersion} checks" +
                        " with the given target jvm: ${targetCompatibility}." +
                        ' Consider changing one or more of the following: target jvm, scala version, scalac user' +
                        ' defined parameters' })
            }
            else {
                return new Tuple2(compilerTargetArgs, null)
            }
        }
//        if (targetType.getTargetParameter() == 'release') {
//            // scalac compiler for scala 2.12.17 and up throws deprecation error if -target is not exactly 8
//            if (normalized >= 8) {
////            def targetFerDefaultParameter = target >= 8 ? '8' : target.toString()
////                def compilerTargetArg = sprintf("%s%s%s%s", '-', targetType.getTargetParameter(), ':', targetType
////                        .getCompilerTargetJvmValuesWithStrategy(strategy, targetCompatibility))
////
////                [compilerTargetArg,'-target:8']
//                def compilerTargetArgs = targetType.getCompilerTargetJvmArgsWithStrategy(strategy, targetCompatibility)
//                compilerTargetArgs
//            }
//            else {
//                throw new StopExecutionException("Cannot reconcile Gradle version <8.0 ScalaPlugin checks" +
//                        " and scalac ${targetType.compilerVersion} checks" +
//                        " with the given target jvm ${targetCompatibility}." +
//                        " Consider changing at least one of the target jvm and scala version")
//            }
//        }
//        // scalac compiler in this version does not support yet 'release' flag
//        else {
////            def compilerTargetArg = sprintf("%s%s%s%s", '-', targetType.getTargetParameter(), ':', targetType
////                        .getCompilerTargetJvmValuesWithStrategy(strategy, targetCompatibility))
////            [compilerTargetArg]
//            def compilerTargetArgs = targetType.getCompilerTargetJvmArgsWithStrategy(strategy, targetCompatibility)
//            compilerTargetArgs
//        }
    }),
    GRADLE_8_0(VersionNumber.parse('8.0'), { ScalaCompilerTargetType targetType,
                                            ScalaCompilerTargetStrategyType strategy,
                                            ScalaCompileOptions scalaCompileOptions,
                                            String targetCompatibility ->
//        def t = ScalaCompilerTargetType.from(compilerVersion)
        def compilerTargetArgs = targetType.getCompilerTargetJvmArgsWithStrategy(strategy, targetCompatibility)

        if (isNullOrEmpty(scalaCompileOptions)) {
            return new Tuple2(compilerTargetArgs, null)
        }
        else {
            if (containsFailOnWarnings(scalaCompileOptions) && containsTarget(scalaCompileOptions) &&
                    compilerTargetArgs.find { it.startsWith('-release:') }) {
                return new Tuple2(compilerTargetArgs, { gradleVersion ->
                        "Cannot reconcile Gradle version: ${gradleVersion}, " +
                        " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}," +
                        " User scalac flags: ${scalaCompileOptions.additionalParameters.join(', ')}" +
                        " and scalac with minimum compiler version: ${targetType.compilerVersion} checks" +
                        " with the given target jvm: ${targetCompatibility}." +
                        ' Consider changing one or more of the following: target jvm, scala version, scalac user' +
                        ' defined parameters' })
            }
            else {
                return  new Tuple2(compilerTargetArgs, null)
            }
        }
    }),
    GRADLE_8_5(VersionNumber.parse('8.5'), { ScalaCompilerTargetType targetType,
                                             ScalaCompilerTargetStrategyType strategy,
                                             ScalaCompileOptions scalaCompileOptions,
                                             String targetCompatibility ->
//        def t = ScalaCompilerTargetType.from(compilerVersion)
        def compilerTargetArgs = targetType.getCompilerTargetJvmArgsWithStrategy(strategy, targetCompatibility)

        def normalized = normalizeTargetCompatibility(targetCompatibility).toInteger()
        // Java 8
        if (normalized == 8) {
            return new Tuple2([], { gradleVersion ->
                "Detected Gradle version: ${gradleVersion}" +
                " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}, will be skipped" +
                ' in favor of Scala Plugin own determined flags'
            })
        }
        else {
            // Scala 2.12.17+ and jvm greater then 8 - force 8 and issue message on that
            if (compilerTargetArgs.size() > 1 && containsFailOnWarnings(scalaCompileOptions)) {
                return new Tuple2(compilerTargetArgs, null)
            }
            // scala 2.13 and higher
            else if (compilerTargetArgs.find { it.startsWith('-release:') }) {
                return new Tuple2([], { gradleVersion ->
                    "Detected Gradle version: ${gradleVersion} and Scala compiler version is 2.13.1 and above." +
                    " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}" +
                    '   , will be skipped in favor of Scala Plugin own determined flags'
                })
            }
            //
//            else {
//
//            }
        }
    })

    private static Map<VersionNumber,ScalaPluginCompileTargetCaseType> mappings
    private final VersionNumber gradleVersion
    private final Closure<Tuple2<List<String>,Closure<String>>> compilerOptionsFunction

    private ScalaPluginCompileTargetCaseType(VersionNumber gradleVersion,
                                             Closure<Tuple2<List<String>,Closure<String>>> compilerOptionsFunction) {
        this.gradleVersion = gradleVersion
        this.compilerOptionsFunction = compilerOptionsFunction
        mappings = mappings ?: [:]
        mappings.put(gradleVersion, this)
    }

    VersionNumber getGradleVersion() {
        this.gradleVersion
    }

    Closure<Tuple2<List<String>,Closure<String>>> getCompilerOptionsFunction() {
        this.compilerOptionsFunction
    }

    private static boolean isNullOrEmpty(ScalaCompileOptions scalaCompileOptions) {
        (scalaCompileOptions.additionalParameters == null || scalaCompileOptions.additionalParameters.isEmpty())
    }

    private static boolean containsFailOnWarnings(ScalaCompileOptions scalaCompileOptions) {
        scalaCompileOptions.additionalParameters.findAll { it.startsWith('-Xfatal-warnings') }.size() > 0
    }

    private static boolean containsTarget(ScalaCompileOptions scalaCompileOptions) {
        scalaCompileOptions.additionalParameters
                .findAll { it.startsWith('-target:') || it.startsWith('-Xtarget:') }.size() > 0
    }

    static ScalaPluginCompileTargetCaseType from(String gradleVersion) {
        def sanitizedVersion = VersionNumber.parse(gradleVersion)
        def evens = [], odds = []
        values()*.gradleVersion.eachWithIndex { v, ix -> ( ix & 1 ? odds : evens ) << v }
        def ranges = [evens, odds].transpose() + [odds, evens[1..-1]].transpose()
        def range = ranges.find { v1, v2 -> sanitizedVersion < v2 && sanitizedVersion >= v1 }
        if (range != null) {
            def relevantVersion = range[0]
            return mappings.get(relevantVersion)
        }
        else {
            def relevantVersion = ranges[-1][1]
            mappings.get(relevantVersion)
        }
    }

    private static String normalizeTargetCompatibility(String javaSpecVersion) {
        def oldTarget = javaSpecVersion =~ /1\.([5-8])/
        def oldJvm = javaSpecVersion =~ /jvm-1\.([5-8])/
        def jvmish = javaSpecVersion =~ /jvm-(\d*)/

        if (oldJvm.matches()) {
            oldJvm.group(1)
        }
        else if (oldTarget.matches()) {
            oldTarget.group(1)
        } else if (jvmish.matches()) {
            jvmish.group(1)
        } else {
            javaSpecVersion
        }
    }
}
