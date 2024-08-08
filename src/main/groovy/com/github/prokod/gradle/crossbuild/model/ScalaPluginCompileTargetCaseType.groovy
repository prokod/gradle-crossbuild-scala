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

import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import org.gradle.api.tasks.scala.ScalaCompileOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.semver4j.Semver
import org.gradle.api.logging.LogLevel

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
 *  <li>GRADLE_6_0_1 - Gradle is only aware of 'target' flag</li>
 *      <ul>
 *          <li>{@code scalaCompileOptionsConfigurer#configure} Gradle by itself is only aware of 'target' flag
 *          and skips setting only that flag internally for any compileScala task in case this flag is set
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
        'PrivateFieldCouldBeFinal', 'Indentation', 'DuplicateListLiteral', 'DuplicateNumberLiteral'])
enum ScalaPluginCompileTargetCaseType {
    GRADLE_6_0_1(Semver.parse('6.0.1'), { ScalaVersionInsights scalaVersionInsights,
                                          ScalaCompilerTargetStrategyType strategy,
                                          ScalaCompileOptions scalaCompileOptions,
                                          String targetCompatibility ->

        def targetType = ScalaCompilerTargetType.from(scalaVersionInsights.compilerVersion)
        def compilerTargetArgs = targetType.getCompilerTargetJvmArgsWithStrategy(strategy, targetCompatibility)

        // From trial and error it seems that Gradle's Scala Plugin computed arguments while Toolchain JVM is 8 causes
        // compilation failures. This is not the case it seems when Toolchain JVM is 11 / 17
        def normalized = normalizeTargetCompatibility(targetCompatibility).toInteger()

        // Scala prior to 2.12.16
        // The plugin logic for default compiler flags are better suited, in this scenario, than Gradle's own
        // defaults
        if (Semver.parse(scalaVersionInsights.compilerVersion) < Semver.parse('2.12.16')) {
            if (normalized == 8) {
                return new Tuple2([], { String gradleVersion ->
                    new Tuple2(LogLevel.DEBUG,
                            returnStatMsg('6.0.1/01', gradleVersion, scalaVersionInsights.compilerVersion,
                                    '<2.12.16', normalized, scalaCompileOptions, compilerTargetArgs) +
                                    ', will be skipped in favor of Gradle\'s Scala Plugin own computed flags')
                })
            } else {
                return new Tuple2(compilerTargetArgs, { String gradleVersion ->
                    new Tuple2(LogLevel.DEBUG,
                            returnStatMsg('6.0.1/02', gradleVersion, scalaVersionInsights.compilerVersion,
                                    '<2.12.16', normalized, scalaCompileOptions, compilerTargetArgs) +
                                    ', will be forced to prevent Scala compile from failing')
                })
            }
        }
        // Scala 2.12.16+ (when this is the case compilerTargetArgs size is 2)
        else if (Semver.parse(scalaVersionInsights.compilerVersion) >= Semver.parse('2.12.16') &&
                Semver.parse(scalaVersionInsights.compilerVersion).diff('2.12.0-RC0') == Semver.VersionDiff.PATCH) {
            return new Tuple2(compilerTargetArgs, { String gradleVersion ->
                new Tuple2(LogLevel.DEBUG,
                        returnStatMsg('6.0.1/03', gradleVersion, scalaVersionInsights.compilerVersion,
                                '~2.12.16', normalized, scalaCompileOptions, compilerTargetArgs) +
                                ', will be forced to prevent Scala compile from failing')
            })
        }
        // Scala 2.13.x, up to 2.13.9.not included
        else if (Semver.parse(scalaVersionInsights.compilerVersion) < Semver.parse('2.13.9') &&
                Semver.parse(scalaVersionInsights.compilerVersion).diff('2.13.0-RC0') == Semver.VersionDiff.PATCH) {
            return new Tuple2([], { String gradleVersion ->
                new Tuple2(LogLevel.DEBUG,
                        returnStatMsg('6.0.1/04', gradleVersion, scalaVersionInsights.compilerVersion,
                                '<2.13.9', normalized, scalaCompileOptions, compilerTargetArgs) +
                                ', will be skipped in favor of Gradle\'s Scala Plugin own computed flags')
            })
        }
        // Scala 2.13.9 and higher
        else if (Semver.parse(scalaVersionInsights.compilerVersion) >= Semver.parse('2.13.9') &&
                Semver.parse(scalaVersionInsights.compilerVersion).diff('2.13.0-RC0') == Semver.VersionDiff.PATCH) {
            if (containsFailOnWarnings(scalaCompileOptions)) {
                return new Tuple2([], { String gradleVersion ->
                    new Tuple2(LogLevel.WARN,
                            returnStatMsg('6.0.1/06', '6.0.1', scalaVersionInsights.compilerVersion,
                                    '~2.13.9', normalized, scalaCompileOptions, compilerTargetArgs) +
                                    ' Cannot reconcile currently used Gradle version with its limited ScalaPlugin options' +
                                    ' and the requested Scala compiler version.' +
                                    ' Consider the following option: Try using later Gradle version')
                })
            } else {
                return new Tuple2([], { String gradleVersion ->
                    new Tuple2(LogLevel.DEBUG, returnStatMsg('6.0.1/05', gradleVersion,
                            scalaVersionInsights.compilerVersion, '~2.13.9', normalized,
                            scalaCompileOptions, compilerTargetArgs) +
                                    ', will be skipped in favor of Gradle\'s Scala Plugin own computed flags')
                })
            }
        }

        // Otherwise
        if (containsFailOnWarnings(scalaCompileOptions)) {
            return new Tuple2([], { gradleVersion ->
                new Tuple2(LogLevel.WARN, returnStatMsg('6.0.1/08', '6.0.1',
                        scalaVersionInsights.compilerVersion, '>=3', normalized,
                        scalaCompileOptions, compilerTargetArgs) +
                            ' Cannot reconcile currently used Gradle version with its limited ScalaPlugin options' +
                            ' and the requested Scala compiler version.' +
                            ' Consider the following option: Try using later Gradle version')
            })
        } else {
            return new Tuple2([], { gradleVersion ->
                new Tuple2(LogLevel.DEBUG, returnStatMsg('6.0.1/07', gradleVersion,
                        scalaVersionInsights.compilerVersion, '>=3', normalized,
                        scalaCompileOptions, compilerTargetArgs) +
                            ', will be skipped in favor of Gradle\'s Scala Plugin own computed flags')
            })
        }
    }),
    GRADLE_8_0(Semver.coerce('8.0'), { ScalaVersionInsights scalaVersionInsights,
                                       ScalaCompilerTargetStrategyType strategy,
                                       ScalaCompileOptions scalaCompileOptions,
                                       String targetCompatibility ->
        def targetType = ScalaCompilerTargetType.from(scalaVersionInsights.compilerVersion)

        def compilerTargetArgs = targetType.getCompilerTargetJvmArgsWithStrategy(strategy, targetCompatibility)

        // From trial and error it seems that Gradle's Scala Plugin computed arguments while Toolchain JVM is 8 causes
        // compilation failures. This is not the case it seems when Toolchain JVM is 11 / 17
        def normalized = normalizeTargetCompatibility(targetCompatibility).toInteger()
        // Java 8
        if (normalized == JavaLanguageVersion.of(8).asInt()) {
            return new Tuple2(compilerTargetArgs, { gradleVersion ->
                new Tuple2(LogLevel.DEBUG, 'Detected Toolchain JVM 8' +
                        " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}" +
                        ', will be forced to prevent Scala compile from failing')
            })
        }
        // jvm greater then 8
        else {
            if (containsFailOnWarnings(scalaCompileOptions) && containsTarget(scalaCompileOptions)) {
                return new Tuple2([], { gradleVersion ->
                    new Tuple2(LogLevel.DEBUG, "Detected Gradle version: ${gradleVersion}," +
                            " Scala compiler version: ${scalaVersionInsights.compilerVersion} (supports '-target' flag)" +
                            " and Toolchain version: $normalized." +
                            " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}" +
                            ', will be skipped in favor of Gradle\'s Scala Plugin own computed flags as they are compatible.')
                })
            }
            else {
                return new Tuple2(compilerTargetArgs, { gradleVersion ->
                    new Tuple2(LogLevel.DEBUG, "Detected Gradle version: ${gradleVersion}," +
                            " Scala compiler version: ${scalaVersionInsights.compilerVersion} ('-target' flag is deprecated or unsupported)" +
                            " and Toolchain version: $normalized." +
                            " User scalac flags: ${scalaCompileOptions.additionalParameters.join(', ')}" +
                            " includes '-Xfatal-warnings'." +
                            " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}" +
                            ', will be forced to prevent Scala compile from failing')
                })
            }
        }
    }),
    GRADLE_8_5(Semver.coerce('8.5'), { ScalaVersionInsights scalaVersionInsights,
                                       ScalaCompilerTargetStrategyType strategy,
                                       ScalaCompileOptions scalaCompileOptions,
                                       String targetCompatibility ->
        def targetType = ScalaCompilerTargetType.from(scalaVersionInsights.compilerVersion)

        def compilerTargetArgs = targetType.getCompilerTargetJvmArgsWithStrategy(strategy, targetCompatibility)

        // From trial and error it seems that Gradle's Scala Plugin computed arguments while Toolchain JVM is 8 causes
        // compilation failures. This is not the case it seems when Toolchain JVM is 11 / 17
        def normalized = normalizeTargetCompatibility(targetCompatibility).toInteger()

        if (normalized == JavaLanguageVersion.of(8).asInt()) {
            return new Tuple2(compilerTargetArgs, { gradleVersion ->
                new Tuple2(LogLevel.DEBUG, 'Detected Toolchain JVM 8' +
                        " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}" +
                        ', will be forced to prevent Scala compile from failing')
            })
        }
        // jvm greater then 8
        else {
            if (containsFailOnWarnings(scalaCompileOptions) && containsTarget(scalaCompileOptions)) {
                return new Tuple2([], { gradleVersion ->
                    new Tuple2(LogLevel.DEBUG, "Detected Gradle version: ${gradleVersion}," +
                            " Scala compiler version: ${scalaVersionInsights.compilerVersion} (supports '-target' flag)" +
                            " and Toolchain version: $normalized." +
                            " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}" +
                            ', will be skipped in favor of Gradle\'s Scala Plugin own computed flags as they are compatible.')
                })
            }
            else {
                return new Tuple2(compilerTargetArgs, { gradleVersion ->
                    new Tuple2(LogLevel.DEBUG, "Detected Gradle version: ${gradleVersion}," +
                            " Scala compiler version: ${scalaVersionInsights.compilerVersion} ('-target' flag is deprecated or unsupported)" +
                            " and Toolchain version: $normalized." +
                            " User scalac flags: ${scalaCompileOptions.additionalParameters.join(', ')}" +
                            " includes '-Xfatal-warnings'." +
                            " CrossbuildScala Plugin recommended scalac flags: ${compilerTargetArgs.join(', ')}" +
                            ', will be forced to prevent Scala compile from failing')
                })
            }
        }
    })

    private static Map<Semver, ScalaPluginCompileTargetCaseType> mappings
    private final Semver gradleVersionClass
    private final Closure<Tuple2<List<String>, Closure<Tuple2<LogLevel,String>>>> compilerOptionsFunction

    private ScalaPluginCompileTargetCaseType(Semver gradleVersionClass,
                                             Closure<Tuple2<List<String>, Closure<Tuple2<LogLevel,String>>>> compilerOptionsFunction) {
        this.gradleVersionClass = gradleVersionClass
        this.compilerOptionsFunction = compilerOptionsFunction
        mappings = mappings ?: [:]
        mappings.put(gradleVersionClass, this)
    }

    Semver getGradleVersionClass() {
        this.gradleVersionClass
    }

    /**
     * The returned log message severity mapping: 0 = error, 1 = warn, 2 = info, 3 = debug
     * @return A tuple where
     *         v1 is the recommended compiler arguments and
     *         v2 as a tuple where
     *         v2.v1 is {@link LogLevel} for log message severity and
     *         v2.v2 is the log message content
     */
    Closure<Tuple2<List<String>, Closure<Tuple2<LogLevel,String>>>> getCompilerOptionsFunction() {
        this.compilerOptionsFunction
    }

    @SuppressWarnings(['UnusedPrivateMethod'])
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
        def sanitizedVersion = Semver.coerce(gradleVersion)
        def evens = [], odds = []
        values()*.gradleVersionClass.eachWithIndex { v, ix -> (ix & 1 ? odds : evens) << v }
        def ranges = [evens, odds].transpose() + [odds, evens[1..-1]].transpose()
        def range = ranges.find { v1, v2 -> sanitizedVersion < v2 && sanitizedVersion >= v1 }
        if (range != null) {
            def relevantVersion = range[0]
            return mappings.get(relevantVersion)
        } else {
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
        } else if (oldTarget.matches()) {
            oldTarget.group(1)
        } else if (jvmish.matches()) {
            jvmish.group(1)
        } else {
            javaSpecVersion
        }
    }

    @SuppressWarnings(['ParameterCount'])
    static String returnStatMsg(String id,
                                String gradleVersion,
                                String scalaCompilerVersion,
                                String scalaCompilerVersionClass,
                                int jdkVersion,
                                ScalaCompileOptions scalaCompileOptions,
                                List<String> compilerTargetArgs) {
        "$id Detected Gradle-Version: ${gradleVersion}," +
                " Scala-Compiler-Version: ${scalaCompilerVersion} ($scalaCompilerVersionClass)" +
                " and Toolchain-Version: $jdkVersion." +
                " User Scalac-Flags: ${scalaCompileOptions.additionalParameters?.join(', ')}" +
                " CrossbuildScala Plugin recommended Scalac-Flags: ${compilerTargetArgs.join(', ')}"
    }
}
