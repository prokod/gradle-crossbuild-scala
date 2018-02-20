package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.CrossBuildPlugin
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Util class for CrossBuildPlugin/Rules
 */
class CrossBuildPluginUtils {

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

    /**
     * Finds all the projects (modules) in a multi module project which has
     *  {@link com.github.prokod.gradle.crossbuild.CrossBuildPlugin} plugin applied
     *  and return their respective names.
     *
     * @param project project space {@link Project}
     * @return list of project(module) names for multi module project
     */
    static List<String> findAllNamesForCrossBuildPluginAppliedProjects(Project project) {
        def moduleNames = project.rootProject.allprojects.findAll { it.plugins.hasPlugin(CrossBuildPlugin) }*.name
        moduleNames
    }

    static String qmarkReplace(String template, String replacement) {
        template.replaceAll('\\?', replacement)
    }
}
