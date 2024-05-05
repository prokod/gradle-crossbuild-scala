package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildExtension
import com.github.prokod.gradle.crossbuild.model.ResolvedTargetCompatibility
import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.model.ScalaCompilerTargetType
import com.github.prokod.gradle.crossbuild.model.ScalaPluginCompileTargetCaseType
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.scala.ScalaCompile

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Gradle scala plugin ScalaCompileTask utils
 */
class ScalaCompileTasks {

    @SuppressWarnings(['LineLength', 'UnnecessaryReturnKeyword'])
    static void tuneCrossBuildScalaCompileTask(CrossBuildExtension extension,
                                               SourceSetInsights sourceSetInsights,
                                               ScalaVersionInsights scalaVersionInsights,
                                               ResolvedTargetCompatibility targetCompatibility) {
        // Classpath debugging by collecting classpath property and printing out may cause ana exception similar to:
        // org.gradle.api.UncheckedIOException: Failed to capture fingerprint of input files for task ':app:compileScala' property 'scalaClasspath' during up-to-date check.
        // So this should be avoided ...
        extension.project.tasks.withType(ScalaCompile) { ScalaCompile t ->
            if (t.name == sourceSetInsights.crossBuild.sourceSet.getCompileTaskName('scala')) {
//                println(">>> " + project.name + ', '+ t.name)
                def analysisFile = t.scalaCompileOptions.incrementalOptions.analysisFile
                if (!analysisFile) {
                    t.scalaCompileOptions.incrementalOptions.analysisFile.set(new File(
                            "$project.buildDir/tmp/scala/compilerAnalysis/" +
                                    "${sourceSetInsights.crossBuild.sourceSet.name}/${project.name}.analysis")
                    )
                }
//                def targetType = ScalaCompilerTargetType.from(scalaVersionInsights.compilerVersion)
//                def target = targetType.getCompilerTargetJvm(targetCompatibility.inferredStrategy, t.targetCompatibility)
//                def parameter = targetType.getTargetParameter()
//                println(">>> [cc] " + targetType + ', '+ target +  ", " + parameter)
//                if (t.scalaCompileOptions.additionalParameters != null) {
//                    def updated = t.scalaCompileOptions.additionalParameters + ["-$parameter:$target".toString()]
//                    t.scalaCompileOptions.additionalParameters = updated
//                    println(">>> [cc-1] " + t.scalaCompileOptions.additionalParameters.join(', '))
//                }
//                else {
//                    t.scalaCompileOptions.additionalParameters = ["-$parameter:$target".toString()]
//                    println(">>> [cc-2] " + t.scalaCompileOptions.additionalParameters.join(', '))
//                }
                def compileCase = ScalaPluginCompileTargetCaseType.from(extension.project.gradle.gradleVersion)

                def (options, msgFunction) = compileCase.getCompilerOptionsFunction()
                        .call(ScalaCompilerTargetType.from(scalaVersionInsights.compilerVersion),
                                targetCompatibility.inferredStrategy,
                                t.scalaCompileOptions,
                                t.targetCompatibility)

                if (msgFunction != null) {
                    extension.project.logger.warn(LoggerUtils.logTemplate(extension.project,
                            lifecycle:'task',
                            msg:"Task: ${t.name} | ${msgFunction(extension.project.gradle.gradleVersion)}"))
                }

                if (options.size() > 0) {
                    if (t.scalaCompileOptions.additionalParameters != null) {
                        def updated = t.scalaCompileOptions.additionalParameters + options
                        t.scalaCompileOptions.additionalParameters = updated
                    } else {
                        t.scalaCompileOptions.additionalParameters = options
                    }
                }
//                println(">>> Setting Scala compiler options: ${t.scalaCompileOptions.additionalParameters.join(', ')} [Task: ${t.name}, JVM tagetCompatibility: ${t.targetCompatibility}]")
                extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                        lifecycle:'task',
                        msg:"Setting Scala compiler options: ${options.join(', ')} [Task: ${t.name}, JVM tagetCompatibility: ${t.targetCompatibility}]"))

                t.exclude { FileTreeElement fte ->
                    def tested = fte.file.toPath()
                    if (tested.normalize().toString().contains("src/${sourceSetInsights.main.sourceSet.name}/")) {
                        def possibleDup = Paths.get(tested.normalize().toString().replace(sourceSetInsights.main.sourceSet.name, sourceSetInsights.crossBuild.sourceSet.name))
                        if (Files.exists(possibleDup) && !Files.isDirectory(possibleDup)) {
                            project.logger.info(LoggerUtils.logTemplate(project,
                                    lifecycle:'task',
                                    msg:"Excluding from compilation detected duplicate source file: [${tested.normalize()}]"))
                            return true
                        }
                    }
                    return false
                }
            }
// TODO: move code here fom below if need be
        }
    }
}

//        else if (t.name == sourceSetInsights.main.sourceSet.getCompileTaskName('scala')) {
//            println(">>>> " + project.name + ', '+ t.name)
//
//            def sv = ScalaVersions.withDefaultsAsFallback(extension.scalaVersionsCatalog)
//            def configuration = extension.project.configurations
//                    .getByName(sourceSetInsights.main.sourceSet.getImplementationConfigurationName())
//            def lib = configuration.allDependencies
//                    .collect {DependencyInsight.parse(it, sv) }
//                    .find {it.moduleType == DependencyModuleType.SCALA_LIBRARY }
//            if (lib == null) {
//                return
//            }
//            println(">>>> [lib] " + lib.group + ', '+ lib.baseName +  ", " + lib.version)
//            def compileCase = ScalaPluginCompileTargetCaseType.from(extension.project.gradle.gradleVersion)
//
//            def options = compileCase.getCompilerOptionsFunction().call(ScalaCompilerTargetType.from(lib.version),
//                    targetCompatibility.inferredStrategy,
//                    t.targetCompatibility)
//
//            if (t.scalaCompileOptions.additionalParameters != null &&
//                    !t.scalaCompileOptions.additionalParameters.isEmpty()) {
//                if (! compileCase.getTargetOptionExistsPredicate().call(t.scalaCompileOptions.additionalParameters)) {
//                    def updated = t.scalaCompileOptions.additionalParameters + options
//                    t.scalaCompileOptions.additionalParameters = updated
//                }
//            }
//            else {
//                t.scalaCompileOptions.additionalParameters = options
//            }
//            def targetType = ScalaCompilerTargetType.from(lib.version)
//            def target = targetType.getCompilerTargetJvm(targetCompatibility.inferredStrategy, t.targetCompatibility)
//            def parameter = targetType.getTargetParameter()
//            if (t.scalaCompileOptions.additionalParameters != null &&
//                    !t.scalaCompileOptions.additionalParameters.isEmpty()) {
//
//                println(">>>> Current options: [${t.scalaCompileOptions.additionalParameters.join(', ')}]")
//                if(VersionNumber.parse(extension.project.gradle.gradleVersion) >= VersionNumber.parse('8.5')) {
//                    if ('release' == targetType.targetParameter) {
//                        if (t.scalaCompileOptions.additionalParameters
//                                .findAll { it.startsWith('-release') }.isEmpty()) {
//                            def updated = t.scalaCompileOptions.additionalParameters +
//                            ["-$parameter:$target".toString()]
//                            t.scalaCompileOptions.additionalParameters = updated
//                            println(">>>> [${lib.version} > 2.13.1]" + project.name + ', ' + t.name + ",
//                            -$parameter:$target,  [${t.scalaCompileOptions.additionalParameters.join(', ')}]")
//                        }
//                    }
//                }
//                else {
//                    if (! t.scalaCompileOptions.additionalParameters
//                            .findAll {it.startsWith('-Xfatal-warnings')}.isEmpty()) {
//                        throw new StopExecutionException('Compiler option -Xfatal-warnings cannot be used' +
//                                " alongside Scala compiler version ${lib.version}")
//                    }
//                    if (t.scalaCompileOptions.additionalParameters
//                            .findAll {it.startsWith('-target')}.isEmpty()) {
//                        def updated = t.scalaCompileOptions.additionalParameters + ["-$parameter:$target".toString()]
//                        t.scalaCompileOptions.additionalParameters = updated
//                    }
//                }
////                    t.scalaCompileOptions.additionalParameters.removeIf {it.contains('-release') ||
// it.contains('11') || it.contains('-target')}
////                    def updated = t.scalaCompileOptions.additionalParameters + ['-target:jvm-11']
////                    t.scalaCompileOptions.additionalParameters = updated
//            }
//            else {
//                t.scalaCompileOptions.additionalParameters = ["-$parameter:$target".toString()]
//                println(">>>> " + project.name + ', '+ t.name +  ", -$parameter:$target")
////                    t.scalaCompileOptions.additionalParameters = ["-$parameter:$target".toString()]
////                    t.scalaCompileOptions.additionalParameters = ['-target:jvm-11'] //'-target:11',
//            }
//        }
