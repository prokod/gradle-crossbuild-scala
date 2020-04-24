package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile

/**
 * Abstraction for CrossBuildReport related tasks
 */
@SuppressWarnings(['AbstractClassWithoutAbstractMethod'])
abstract class AbstractCrossBuildsReportTask extends DefaultTask {
    static final String BASE_TASK_NAME = CrossBuildSourceSets.SOURCESET_BASE_NAME
    static final String REPORTS_DIR = 'crossbuilding-reports'
    static final String TASK_GROUP = 'crossbuilding'

    protected File outputFile

    protected AbstractCrossBuildsReportTask() {
        super()
        group = TASK_GROUP
    }

    @OutputFile
    File getOutputFile() {
        this.outputFile
    }

    @Internal
    protected String getOutputFileBasePath() {
        "$project.buildDir.path${File.separator}$REPORTS_DIR$File.separator"
    }

}
