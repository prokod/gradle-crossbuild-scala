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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

class CrossBuildPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.apply("scala")
        project.extensions.create("crossBuildScalaBridging", BridgingExtension, project)
        project.pluginManager.apply(CrossBuildPluginRules)

        def sourceSets = project.hasProperty('sourceSets') ? (SourceSetContainer) project.sourceSets : null
        assert sourceSets != null: "Missing 'sourceSets' property under Project ${project.name} properties."

        // Create source sets
        CrossBuildPluginRules.DEFAULT_SCALA_VERSION_CATALOG.getCatalog().entrySet().collect {it.key}.each { String scalaVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(new ScalaVerImpl(scalaVersion), CrossBuildPluginRules.DEFAULT_SCALA_VERSION_CATALOG)

            def sourceSetId = "${CrossBuildPluginRules.SOURCE_SET_BASE_NAME}${scalaVersionInsights.strippedArtifactInlinedVersion}"
            project.logger.info(LoggerUtils.logTemplate(project, "Creating source set: [${sourceSetId}]"))

            if (!sourceSets.findByName(sourceSetId)) {
                // Create source set for specific scala version
                sourceSets.create(sourceSetId)
            }
        }
    }
}
