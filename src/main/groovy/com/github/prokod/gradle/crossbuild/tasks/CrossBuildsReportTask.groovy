package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Custom gradle task for cross building related reporting
 */
class CrossBuildsReportTask extends AbstractCrossBuildsReportTask {
    @Internal
    Collection<ResolvedBuildAfterEvalLifeCycle> resolvedBuilds

    CrossBuildsReportTask() {
        super()

        outputFile = project.file("${getOutputFileBasePath()}${project.name}_builds.json")
    }

    @SuppressWarnings(['Println'])
    @TaskAction
    void report() {
        def msg = this.resolvedBuilds*.toString().join(',\n')
        if (!this.outputFile.parentFile.exists()) {
            this.outputFile.parentFile.mkdirs()
        }
        this.outputFile.write("[$msg]")
        println("cross build settings for project '$project.name' [$project.path]\n[$msg]")
    }
}
