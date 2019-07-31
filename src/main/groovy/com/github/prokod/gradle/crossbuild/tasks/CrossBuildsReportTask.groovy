package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import org.gradle.api.tasks.TaskAction

class CrossBuildsReportTask extends AbstractCrossBuildsReportTask {
    Collection<ResolvedBuildAfterEvalLifeCycle> resolvedBuilds

    CrossBuildsReportTask() {
        super()

        outputFile = project.file("${getOutputFileBasePath()}${project.name}_builds.json")
    }

    @TaskAction
    def report() {
        def msg = this.resolvedBuilds.collect { it.toString() }.join(',\n')
        if (!this.outputFile.parentFile.exists()) {
            this.outputFile.parentFile.mkdirs()
        }
        this.outputFile.write("[$msg]")
        println("cross build settings for project '$project.name' [$project.path]\n[$msg]")
    }
}
