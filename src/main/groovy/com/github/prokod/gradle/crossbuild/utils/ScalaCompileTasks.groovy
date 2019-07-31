package com.github.prokod.gradle.crossbuild.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile

/**
 * Gradle scala plugin ScalaCompileTask utils
 */
class ScalaCompileTasks {

    static void tuneCrossBuildScalaCompileTask(Project project,
                                               SourceSet sourceSet,
                                               Set<ProjectDependency> projectDependencies//,
                                               /*Set<Dependency> allDependencies*/) {
        project.tasks.withType(ScalaCompile) { ScalaCompile t ->
            if (t.name == sourceSet.getCompileTaskName('scala')) {
                def analysisFile = t.scalaCompileOptions.incrementalOptions.analysisFile
                if (!analysisFile) {
                    t.scalaCompileOptions.incrementalOptions.analysisFile = new File(
                            "$project.buildDir/tmp/scala/compilerAnalysis/" +
                                    "${sourceSet.name}/${project.name}.analysis")
                }
                t.doFirst {
                    project.logger.info(LoggerUtils.logTemplate(project,
                            lifecycle:'task',
                            sourceset:sourceSet.name,
                            msg:'Cross build scala compile task classpath ' +
                                    t.classpath.collect { "${it.name}" }.join(':')
                    ))
                }
//                t.doFirst {
//                    def tuples = projectDependencies.collect {
//                        def projectName = it.dependencyProject.name
//                        def crossBuildJarTaskName = it.dependencyProject.tasks.findByName(sourceSet.getJarTaskName())
//                        new Tuple2(projectName, crossBuildJarTaskName)
//                    }
//
//                    tuples*.first.each { pname ->
//                        t.classpath = t.classpath.filter { file ->
//                            file.name != "${pname}-${project.getVersion()}.jar"
//                        }
//                    }
//                }
//                t.doFirst {
//                    project.logger.info(LoggerUtils.logTemplate(project,
//                            lifecycle:'projectsEvaluated',
//                            sourceset:sourceSet.name,
//                            msg:'NON Modified cross build scala compile task classpath ' +
//                                    t.classpath.collect { "${it.name}" }.join(':')
//                    ))
//                }
            }
        }
    }
}
