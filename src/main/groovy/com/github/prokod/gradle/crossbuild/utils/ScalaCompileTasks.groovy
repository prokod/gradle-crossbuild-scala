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
                                               Set<ProjectDependency> projectDependencies,
                                               Set<Dependency> allDependencies) {
        project.tasks.withType(ScalaCompile) { ScalaCompile t ->
            if (t.name == sourceSet.getCompileTaskName('scala')) {
                def analysisFile = t.scalaCompileOptions.incrementalOptions.analysisFile
                if (!analysisFile) {
                    t.scalaCompileOptions.incrementalOptions.analysisFile = new File(
                            "$project.buildDir/tmp/scala/compilerAnalysis/" +
                                    "${sourceSet.name}/${project.name}.analysis")
                }
                t.doFirst {
                    project.logger.debug(LoggerUtils.logTemplate(project,
                            lifecycle:'projectsEvaluated',
                            sourceset:sourceSet.name,
                            msg:'Modified cross build scala compile task classpath:\n' +
                                    "${t.classpath*.toString().join('\n')}"
                    ))
                }
                t.doFirst {
                    def tuples = projectDependencies.collect {
                        def projectName = it.dependencyProject.name
                        def crossBuildJarTaskName = it.dependencyProject.tasks.findByName(sourceSet.getJarTaskName())
                        new Tuple2(projectName, crossBuildJarTaskName)
                    }
                    def fileCollections = tuples*.second.collect { it.outputs.files }
                    def crossBuildClasspath = fileCollections.inject(project.files()) { result, c -> result + c }

                    def deps = allDependencies - projectDependencies

                    def origClasspathFiltered = t.classpath.filter { classpathFilterPredicate(it, tuples*.first, deps) }

                    t.classpath = crossBuildClasspath + origClasspathFiltered
                }
            }
        }
    }

    static boolean classpathFilterPredicate(File candidate,
                                            List<String> projectNames, Set<Dependency> allDependencies) {
        def fileIsCandidateForRemoval = projectNames.findAll { projectName ->
            def pattern = ~/^$projectName[-|\.].*$/
            candidate.name ==~ pattern
        }.size() > 0

        def fileIsActuallyA3rdPartyDependency = allDependencies.collect { "$it.name-$it.version" }.findAll {
            candidate.name.startsWith(it)
        }.size() > 0

        !fileIsCandidateForRemoval || fileIsActuallyA3rdPartyDependency
    }
}
