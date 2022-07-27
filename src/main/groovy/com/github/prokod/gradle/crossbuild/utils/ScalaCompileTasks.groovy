package com.github.prokod.gradle.crossbuild.utils

import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Gradle scala plugin ScalaCompileTask utils
 */
class ScalaCompileTasks {

    @SuppressWarnings(['LineLength'])
    static void tuneCrossBuildScalaCompileTask(Project project, SourceSetInsights sourceSetInsights) {
        // Classpath debugging by collecting classpath property and printing out may cause ana exception similar to:
        // org.gradle.api.UncheckedIOException: Failed to capture fingerprint of input files for task ':app:compileScala' property 'scalaClasspath' during up-to-date check.
        // So this should be avoided ...
        project.tasks.withType(ScalaCompile) { ScalaCompile t ->
            if (t.name == sourceSetInsights.crossBuild.sourceSet.getCompileTaskName('scala')) {
                def analysisFile = t.scalaCompileOptions.incrementalOptions.analysisFile
                if (!analysisFile) {
                    t.scalaCompileOptions.incrementalOptions.analysisFile.set(new File(
                            "$project.buildDir/tmp/scala/compilerAnalysis/" +
                                    "${sourceSetInsights.crossBuild.sourceSet.name}/${project.name}.analysis")
                    )
                }

                t.exclude { FileTreeElement fte ->
                    def tested = fte.file.toPath()
                    if (tested.normalize().toString().contains("src/${sourceSetInsights.main.sourceSet.name}/")) {
                        def possibleDup = Paths.get(tested.normalize().toString().replace(sourceSetInsights.main.sourceSet.name, sourceSetInsights.crossBuild.sourceSet.name))
                        if (Files.exists(possibleDup) && !Files.isDirectory(possibleDup)) {
                            project.logger.info(LoggerUtils.logTemplate(project,
                                    lifecycle:'task',
                                    msg:"Excluding from compilation detected duplicate source file: [${tested.normalize().toString()}]"))
                            return true
                        }
                    }
                    return false
                }
            }
        }
    }
}
