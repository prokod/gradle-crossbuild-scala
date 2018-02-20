/*
 * Copyright 2016-2017 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.rules.CrossBuildPluginRules
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

/**
 * Crossbuild plugin entry point
 */
class CrossBuildPlugin implements Plugin<Project> {
    void apply(Project project) {
        if (project.plugins.hasPlugin(MavenPublishPlugin)) {
            throw new GradleException("Applying both 'maven-publish' and '${this.class.getSimpleName()}' " +
                    "is Illegal. Please remove relevant apply code from build.gradle for 'maven-publish' plugin.")
        }
        project.pluginManager.apply('scala')
        project.extensions.create('bridging', BridgingExtension, project)
        project.pluginManager.apply(CrossBuildPluginRules)
        project.pluginManager.apply(MavenPublishPlugin)
        // enforce maven-publish plugin AFTER cross build plugin (Model shortcomings - cyclic rules)

        def crossBuildSourceSets = new CrossBuildSourceSets(project, ScalaVersions.DEFAULT_SCALA_VERSIONS)

        // Create default source sets early enough to be used in build.gradle dependencies block
        ScalaVersions.DEFAULT_SCALA_VERSIONS.catalog*.key.each { String scalaVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(scalaVersion, ScalaVersions.DEFAULT_SCALA_VERSIONS)

            def sourceSetId = crossBuildSourceSets.getOrCreateCrossBuildScalaSourceSet(scalaVersionInsights).first

            project.logger.info(LoggerUtils.logTemplate(project,
                    "Creating source set (Pre Evaluate Lifecycle): [${sourceSetId}]"))
        }
    }
}
