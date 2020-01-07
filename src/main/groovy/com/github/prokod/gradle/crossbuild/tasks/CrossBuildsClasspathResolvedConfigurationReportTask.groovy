package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.CrossBuildExtension
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import groovy.json.JsonBuilder
import groovy.transform.TupleConstructor
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

/**
 * Custom gradle task for cross building related reporting
 *
 */
// todo 1. add compileOnly, runtimeOnly 2. group under sourceset all configs 3. depict extendsFrom
class CrossBuildsClasspathResolvedConfigurationReportTask extends AbstractCrossBuildsReportTask {
    @Internal
    CrossBuildExtension extension

    CrossBuildsClasspathResolvedConfigurationReportTask() {
        super()

        outputFile = project.file("${getOutputFileBasePath()}${project.name}_builds_resolved_configurations.json")
    }

    @SuppressWarnings(['Println'])
    @TaskAction
    void report() {
        def items = extension.resolvedBuilds.collect { rb ->
            createReportItemAsJsonFor(rb)
        }

        def msg = items.join(',\n')

        if (!this.outputFile.parentFile.exists()) {
            this.outputFile.parentFile.mkdirs()
        }
        this.outputFile.write("[$msg]")
        println("cross build settings for project '$project.name' [$project.path]\n[$msg]")
    }

    String createReportItemAsJsonFor(ResolvedBuildAfterEvalLifeCycle rb) {
        def (String sourceSetId, SourceSet sourceSet) = extension.crossBuildSourceSets.findByName(rb.name)

        def subItem1 = createReportSubItemFor(sourceSet) { SourceSet input ->
            sourceSet.compileConfigurationName
        }
        def subItem2 = createReportSubItemFor(sourceSet) { SourceSet input ->
            sourceSet.implementationConfigurationName
        }
        def subItem3 = createReportSubItemFor(sourceSet) { SourceSet input ->
            sourceSet.compileClasspathConfigurationName
        }
        def subItem4 = createReportSubItemFor(sourceSet) { SourceSet input ->
            sourceSet.runtimeClasspathConfigurationName
        }

        def subItems = [subItem1, subItem2, subItem3, subItem4]
        '[' + subItems.collect { toJson(it) }.join(',\n') + ']'
    }

    ReportSubItem createReportSubItemFor(SourceSet sourceSet, Closure<String> reqConfigurationNameFunc) {
        def reqConfigurationName = reqConfigurationNameFunc(sourceSet)
        def reqConfiguration = extension.project.configurations.findByName(reqConfigurationName)
        def canBeResolved = reqConfiguration.isCanBeResolved()

        def configurationItem =
                new ReportSubItem.ConfigurationItem(reqConfiguration.name, reqConfiguration.allDependencies*.toString())

        def reqResolvedConfigurationFunc = { boolean resolve ->
            if (resolve) {
                def reqResolvedConfiguration = reqConfiguration.resolvedConfiguration
                def resolvedConfigurationItem =
                        new ReportSubItem.ResolvedConfigurationItem(
                                reqResolvedConfiguration.firstLevelModuleDependencies*.toString(),
                                reqResolvedConfiguration.resolvedArtifacts*.toString(),
                                reqResolvedConfiguration.files*.name,
                                reqResolvedConfiguration.hasError())
                resolvedConfigurationItem
            }
            else {
                new ReportSubItem.ResolvedConfigurationItem()
            }
        }

        def subItem = new ReportSubItem(sourceSet.name, configurationItem, reqResolvedConfigurationFunc(canBeResolved))
        subItem
    }

    static String toJson(ReportSubItem subItem) {
        new JsonBuilder(subItem).toPrettyString()
    }

    @TupleConstructor
    static class ReportSubItem {
        String sourceSet
        ConfigurationItem configuration
        ResolvedConfigurationItem resolvedConfiguration

        @TupleConstructor
        static class ConfigurationItem {
            String name
            List<String> allDependencies
        }

        @TupleConstructor
        static class ResolvedConfigurationItem {
            List<String> firstLevelModuleDependencies
            List<String> resolvedArtifacts
            List<String> files
            boolean hasError
        }
    }
}
