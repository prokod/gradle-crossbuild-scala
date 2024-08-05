package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildExtension
import com.github.prokod.gradle.crossbuild.model.ResolvedTargetCompatibility
import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.model.ScalaPluginCompileTargetCaseType
import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.api.plugins.JavaPluginExtension
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
            if (t.name == sourceSetInsights.crossBuild.sourceSet.getCompileTaskName('scala') ||
                    t.name == sourceSetInsights.main.sourceSet.getCompileTaskName('scala')) {
                def analysisFile = t.scalaCompileOptions.incrementalOptions.analysisFile
                if (!analysisFile) {
                    t.scalaCompileOptions.incrementalOptions.analysisFile.set(new File(
                            "$extension.project.buildDir/tmp/scala/compilerAnalysis/" +
                                    "${sourceSetInsights.crossBuild.sourceSet.name}/${extension.project.name}.analysis")
                    )
                }

                def (srcCoompat, trgCompat) = compatibility(extension.project, t)



                def compileCase = ScalaPluginCompileTargetCaseType.from(extension.project.gradle.gradleVersion)
                println(">>> compileCase:" + compileCase)

                def (options, msgFunction) = compileCase.getCompilerOptionsFunction()
                        .call(scalaVersionInsights,
                                targetCompatibility.inferredStrategy,
                                t.scalaCompileOptions,
                                t.targetCompatibility)

                if (msgFunction != null) {
                    def (logLevel, msg) = msgFunction(extension.project.gradle.gradleVersion)
                    extension.project.logger.log(logLevel, LoggerUtils.logTemplate(extension.project,
                            lifecycle:'task',
                            msg:"Task: ${t.name} | ${msg}"))
                }

                if (options.size() > 0) {
                    if (t.scalaCompileOptions.additionalParameters != null) {
                        def updated = t.scalaCompileOptions.additionalParameters + options
                        t.scalaCompileOptions.additionalParameters = updated
                    } else {
                        t.scalaCompileOptions.additionalParameters = options
                    }
                }
                println(">>> Setting Scala compiler options: ${t.scalaCompileOptions.additionalParameters?.join(', ')} [Task: ${t.name}, JVM tagetCompatibility: ${t.targetCompatibility}]")
                extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                        lifecycle:'task',
                        msg:"Setting Scala compiler options: ${options.join(', ')} [Task: ${t.name}, JVM tagetCompatibility: ${t.targetCompatibility}]"))

                t.exclude { FileTreeElement fte ->
                    def tested = fte.file.toPath()
                    if (tested.normalize().toString().contains("src/${sourceSetInsights.main.sourceSet.name}/")) {
                        def possibleDup = Paths.get(tested.normalize().toString().replace(sourceSetInsights.main.sourceSet.name, sourceSetInsights.crossBuild.sourceSet.name))
                        if (Files.exists(possibleDup) && !Files.isDirectory(possibleDup)) {
                            extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                                    lifecycle:'task',
                                    msg:"Excluding from compilation detected duplicate source file: [${tested.normalize()}]"))
                            return true
                        }
                    }
                    return false
                }
            }
        }
    }

    /**
     *
     * @param project
     * @param sc
     * @return
     */
    static Tuple2<String, String> compatibility(Project project, ScalaCompile sc) {
        def javaExtension = project.extensions.getByType(JavaPluginExtension)

        def sourceCompatibility = javaExtension.getRawSourceCompatibility() ?: sc.getJavaLauncher().getOrElse(null) != null ? sc.getJavaLauncher().get().getMetadata().getLanguageVersion().toString() : null
        def targetCompatibility = javaExtension.getRawTargetCompatibility() ?: sc.getSourceCompatibility()
        println(">>> default launcher exec path: " + sc.getJavaLauncher().getOrNull()?.executablePath)
        println(">>> default launcher installation path: " + sc.getJavaLauncher().getOrNull()?.metadata?.installationPath)
        println(">>> source comp: " + javaExtension.getRawSourceCompatibility())
        println(">>> source fallback comp: " + (sc.getJavaLauncher().getOrElse(null) != null ? sc.getJavaLauncher().get().getMetadata().getLanguageVersion().toString() : 'N/A'))
        println(">>> target comp: " + javaExtension.getRawTargetCompatibility())
        println(">>> target fallback comp: " +  sc.getSourceCompatibility())
        new Tuple2(sourceCompatibility, targetCompatibility)
    }
}
