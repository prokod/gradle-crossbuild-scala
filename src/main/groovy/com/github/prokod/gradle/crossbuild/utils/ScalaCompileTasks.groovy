package com.github.prokod.gradle.crossbuild.utils

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile

/**
 * Gradle scala plugin ScalaCompileTask utils
 */
class ScalaCompileTasks {

    @SuppressWarnings(['LineLength'])
    static void tuneCrossBuildScalaCompileTask(Project project, SourceSet sourceSet) {
        // Classpath debugging by collecting classpath property and printing out may cause ana exception similar to:
        // org.gradle.api.UncheckedIOException: Failed to capture fingerprint of input files for task ':app:compileScala' property 'scalaClasspath' during up-to-date check.
        // So this should be avoided ...
        project.tasks.withType(ScalaCompile) { ScalaCompile t ->
            if (t.name == sourceSet.getCompileTaskName('scala')) {
                def analysisFile = t.scalaCompileOptions.incrementalOptions.analysisFile
                if (!analysisFile) {
                    t.scalaCompileOptions.incrementalOptions.analysisFile = new File(
                            "$project.buildDir/tmp/scala/compilerAnalysis/" +
                                    "${sourceSet.name}/${project.name}.analysis")
                }
            }
        }
    }
}
