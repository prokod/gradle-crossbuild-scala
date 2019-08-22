package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildPlugin
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Util class for CrossBuildPlugin/Rules
 */
class CrossBuildPluginUtils {

    /**
     * This method returns stable result (No matter how the user decides to use the plugin DSL in build.gradle)
     * when executed from within {@code Gradle.projectsEvaluated {}} block.
     *
     * NOTE: In certain cases of build.gradle composition together with this method being used from within
     * {@code project.afterEvaluate {}} block the result will be the same.
     *
     * @return Set of {@link org.gradle.api.Project}s with {@link com.github.prokod.gradle.crossbuild.CrossBuildPlugin}
     *         applied.
     *
     * @param insightsView context information for logging sake only
     *
     */
    // todo find a way to achieve the same result in a stable way from {@code project.afterEvaluate {}} block
    // todo insightView is needed only because of logging. Consider for removal.
    static Set<Project> findAllCrossBuildPluginAppliedProjects(SourceSetInsightsView insightsView) {
        def project = insightsView.project
        def names = insightsView.names
        def configurationName = names.crossBuild
        def parentConfigurationName = names.main
        def moduleNames = project.gradle.rootProject.allprojects.findAll { it.plugins.hasPlugin(CrossBuildPlugin) }

        project.logger.debug(LoggerUtils.logTemplate(project,
                lifecycle:'afterEvaluate',
                configuration:configurationName,
                parentConfiguration:parentConfigurationName,
                msg:"Found the following crossbuild modules ${moduleNames.join(', ')}."))
        moduleNames
    }

    /**
     * Run assert statement in assertion Closure. If the assertion fails
     * we catch the exception. We use the message with the error appended with an user message
     * and throw a {@link org.gradle.api.GradleException}.
     *
     * @param message User message to be appended to assertion error message
     * @param assertion Assert statement(s) to run
     */
    static void assertWithMsg(final String message, final Closure assertion) {
        try {
            // Run Closure with assert statement(s).
            assertion()
        } catch (AssertionError assertionError) {
            // Use Groovy power assert output from the assertionError
            // exception and append user message.
            def exceptionMessage = new StringBuilder(assertionError.message)
            exceptionMessage << System.properties['line.separator'] << System.properties['line.separator']
            exceptionMessage << message

            // Throw exception so Gradle knows the validation fails.
            throw new GradleException(exceptionMessage.toString(), assertionError)
        }
    }

    static String qmarkReplace(String template, String replacement) {
        template.replaceAll('\\?', replacement)
    }
}
