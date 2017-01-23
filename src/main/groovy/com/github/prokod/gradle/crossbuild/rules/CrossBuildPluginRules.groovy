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
package com.github.prokod.gradle.crossbuild.rules

import com.github.prokod.gradle.crossbuild.BridgingExtension
import com.github.prokod.gradle.crossbuild.ScalaVersionCatalog
import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.model.CrossBuild
import com.github.prokod.gradle.crossbuild.model.ScalaVer
import com.github.prokod.gradle.crossbuild.model.TargetVerItem
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.model.*

class CrossBuildPluginRules extends RuleSource {
    static final SOURCE_SET_BASE_NAME = "crossBuild"
    static final DEFAULT_SCALA_VERSION_CATALOG = new ScalaVersionCatalog(['2.9': '2.9.3', '2.10': '2.10.6', '2.11': '2.11.8', '2.12': '2.12.1'])

    @Model void crossBuild(CrossBuild crossBuild) {}

    @Defaults void setDefaultVersionCatalog(CrossBuild crossBuild) {
        crossBuild.scalaVersionCatalog = DEFAULT_SCALA_VERSION_CATALOG
    }

    @Defaults void setDefaultArchiveAppendix(@Each TargetVerItem item) {
        item.archiveAppendix = '_?'
        item.value = item.name
    }

    @Mutate void setUnmanaged(CrossBuild crossBuild, ExtensionContainer extensions) {
        def extension = (BridgingExtension) extensions.crossBuildScalaBridging
        crossBuild.project = extension.project
        crossBuild.archivesBaseName = extension.project.archivesBaseName
    }

    @Mutate
    void createTask(ModelMap<Task> tasks, CrossBuild crossBuild) {
        def project = crossBuild.project

        def sourceSets = project.hasProperty('sourceSets') ? (SourceSetContainer) project.sourceSets : null
        assert sourceSets != null: "Missing 'sourceSets' property under Project ${project.name} properties."

        createCrossBuildScalaSourceSets(crossBuild, sourceSets)

        def main = sourceSets.findByName('main')
        def mainScala = (SourceDirectorySet) main.scala

        crossBuild.targetVersions.findAll { it instanceof ScalaVer }.each { ScalaVer scalaVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(scalaVersion, crossBuild.scalaVersionCatalog)

            def sourceSetId = "${SOURCE_SET_BASE_NAME}${scalaVersionInsights.strippedArtifactInlinedVersion}"
            def sourceSet = sourceSets.findByName(sourceSetId)

            def crossBuildScalaSourceDirSetJava = (SourceDirectorySet) sourceSet.java
            crossBuildScalaSourceDirSetJava.setSrcDirs([])

            def crossBuildScalaSourceDirSetScala = (SourceDirectorySet) sourceSet.scala
            crossBuildScalaSourceDirSetScala.setSrcDirs(mainScala.getSrcDirs())

            def crossBuildSourceDirSetResources = (SourceDirectorySet) sourceSet.resources
            crossBuildSourceDirSetResources.setSrcDirs(main.resources.getSrcDirs())

            project.dependencies.add(sourceSet.compileConfigurationName, "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")

            configureResolution(project, sourceSet.compileConfigurationName, project.configurations.compile, scalaVersionInsights)
            configureResolution(project, sourceSet.compileOnlyConfigurationName, project.configurations.compileOnly, scalaVersionInsights)
            configureResolution(project, sourceSet.runtimeConfigurationName, project.configurations.runtime, scalaVersionInsights)

            def interpretedBaseName = "${crossBuild.archivesBaseName}${qmarkReplace(scalaVersion.archiveAppendix, scalaVersionInsights.artifactInlinedVersion)}"
            project.logger.info(LoggerUtils.logTemplate(project, "Cross build jar ${sourceSetId} baseName = '${interpretedBaseName}'"))

            tasks.create(sourceSet.getJarTaskName(), Jar) {
                group = BasePlugin.BUILD_GROUP
                description = "Assembles a jar archive containing ${scalaVersionInsights.strippedArtifactInlinedVersion} classes"
                baseName = interpretedBaseName
                from sourceSet.output
            }

            def compileScala = project.tasks.findByName(sourceSet.getTaskName("compile", "scala"))

            compileScala.configure {
                scalaCompileOptions.incrementalOptions.with {
                    if (!analysisFile) {
                        analysisFile = new File("$project.buildDir/tmp/scala/compilerAnalysis/${project.name}.analysis")
                    }
                }
            }
        }
    }

    static final createCrossBuildScalaSourceSets(CrossBuild crossBuild, SourceSetContainer sourceSets) {
        def project = crossBuild.project
        // Create source sets
        def sourceSetIds = crossBuild.targetVersions.findAll { it instanceof ScalaVer }.collect { ScalaVer targetVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(targetVersion, crossBuild.scalaVersionCatalog)

            def sourceSetId = "${SOURCE_SET_BASE_NAME}${scalaVersionInsights.strippedArtifactInlinedVersion}"
            project.logger.info(LoggerUtils.logTemplate(project, "Creating source set: [${sourceSetId}]"))

            if (!sourceSets.findByName(sourceSetId)) {
                // Create source set for specific scala version
                sourceSets.create(sourceSetId)
            }
            sourceSetId.toString()
        }

        // Remove unused source sets
        cleanSourceSetsContainer(sourceSets, sourceSetIds)

        // disable unused tasks
        cleanTasksContainer(project.tasks, crossBuild.targetVersions.findAll { it instanceof ScalaVer }.collect { it.value }.toSet() )
    }

    static final qmarkReplace(String template, String replacement) {
        template.replaceAll("\\?", replacement)
    }

    static final cleanSourceSetsContainer(SourceSetContainer sourceSets, List<String> sourceSetIds) {
        // Remove unused source sets
        sourceSets.removeIf { it.name.contains(SOURCE_SET_BASE_NAME) && !sourceSetIds.contains(it.name) }
    }

    static final cleanTasksContainer(TaskContainer tasks, Set<String> targetVersions) {
        // disable unused tasks
        def nonActiveTargetVersions = DEFAULT_SCALA_VERSION_CATALOG
                .mkRefTargetVersions()
        nonActiveTargetVersions.removeAll(targetVersions)

        def nonActiveSourceSetIds = nonActiveTargetVersions.collect {
            "${SOURCE_SET_BASE_NAME}${it.replaceAll("\\.", "")}"
        }
        tasks.removeAll(
                tasks.findAll { t ->
                    nonActiveSourceSetIds.collect {
                        ssid -> t.name.toLowerCase().contains(ssid.toLowerCase())
                    }.findAll {
                        it.equals(true)
                    }.size() > 0
                }
        )
    }

    static final Configuration configureResolution(Project project, crossBuildConfigurationName, Configuration parentConfiguration, ScalaVersionInsights scalaVersionInsights) {
        def config = project.configurations[crossBuildConfigurationName]
        config.extendsFrom(parentConfiguration)
        def allDependenciesNames = ""
        config.dependencies.each { allDependenciesNames += ", ${it.name}" }
        config.extendsFrom
                .findAll { it.name.contains(scalaVersionInsights.underscoredArtifactInlinedVersion) }
                .each { it.dependencies.each { allDependenciesNames += ", " + it.name } }
        project.logger.info(LoggerUtils.logTemplate(project, "${crossBuildConfigurationName} | Inherited dependendencies to consider while resolving this configuration dependencies: [${allDependenciesNames}]"))

        config.resolutionStrategy.eachDependency { details ->
            def requested = details.requested
            // Replace 3d party scala which end with '_?'
            if (requested.name.endsWith("_?")) {
                def forceReplace = allDependenciesNames.contains(requested.name.replace("_?", ""))
                def resolvedName = requested.name.replace("_?", "_${scalaVersionInsights.artifactInlinedVersion}")
                project.logger.info(LoggerUtils.logTemplate(project, "${crossBuildConfigurationName} | Found dependency ${requested.name} to further process. Custom replacement dependency was specified in build.gradle ? ${forceReplace}. ${forceReplace ? 'Going to use it.' : "Going to use by convention replacement [${resolvedName}]"}"))
                if (forceReplace) {
                    details.useTarget group: requested.group, name: requested.name.replace("_?", "_${scalaVersionInsights.artifactInlinedVersion}"), version: requested.version
                } else {
                    details.useTarget group: requested.group, name: resolvedName, version: requested.version
                }
            } else {
                def versionToReplace = requested.name.substring(requested.name.lastIndexOf("_"))
                def forceReplace = allDependenciesNames.contains(requested.name.replace(versionToReplace, ""))
                project.logger.info(LoggerUtils.logTemplate(project, "${crossBuildConfigurationName} | name: ${requested.name} v2replace: ${versionToReplace} forceReplace: ${forceReplace}"))
                if (forceReplace) {
                    details.useTarget group: requested.group, name: requested.name.replace(versionToReplace, "_${scalaVersionInsights.artifactInlinedVersion}"), version: requested.version
                }
            }
            //
            if (requested.group.equals("org.scala-lang") && requested.name.equals("scala-library")) {
                details.useVersion(scalaVersionInsights.compilerVersion)
            }
        }
        config
    }
}
