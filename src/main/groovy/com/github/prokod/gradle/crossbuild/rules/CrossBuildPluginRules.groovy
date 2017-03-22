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
import com.github.prokod.gradle.crossbuild.model.TargetVerItem
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.model.*

class CrossBuildPluginRules extends RuleSource {
    static final SOURCE_SET_BASE_NAME = "crossBuild"
    static final DEFAULT_SCALA_VERSION_CATALOG = new ScalaVersionCatalog(['2.9': '2.9.3', '2.10': '2.10.6', '2.11': '2.11.8', '2.12': '2.12.1'])

    @Model void crossBuild(CrossBuild crossBuild) {
    }

    @Defaults void setDefaultVersionCatalog(CrossBuild crossBuild) {
        crossBuild.scalaVersionCatalog = DEFAULT_SCALA_VERSION_CATALOG
    }

    @Defaults void setDefaultArchiveAppendix(@Each TargetVerItem item) {
        item.archiveAppendix = '_?'
    }

    @Mutate void setProjectViaBridge(CrossBuild crossBuild, ExtensionContainer extensions) {
        def extension = (BridgingExtension) extensions.bridging
        def project = extension.project
        crossBuild.project = project
        crossBuild.archivesBaseName = project.archivesBaseName
    }

    @Mutate void setTargetVerItems(@Each TargetVerItem targetVersion, @Path("crossBuild.archivesBaseName") String archivesBaseName, @Path("crossBuild.scalaVersionCatalog") ScalaVersionCatalog scalaVersionCatalog) {
        validateTargetVersion(targetVersion)
        def scalaVersionInsights = new ScalaVersionInsights(targetVersion, scalaVersionCatalog)
        def interpretedBaseName = "${archivesBaseName}${qmarkReplace(targetVersion.archiveAppendix, scalaVersionInsights.artifactInlinedVersion)}"
        targetVersion.artifactId = interpretedBaseName
    }

    @Mutate
    void realizeCrossBuildTasks(ModelMap<Task> tasks, CrossBuild crossBuild) {
        def project = crossBuild.project

        def sourceSets = getSourceSets(project)

        createCrossBuildScalaSourceSets(crossBuild, sourceSets)

        def main = sourceSets.findByName('main')
        def mainScala = (SourceDirectorySet) main.scala

        crossBuild.targetVersions.findAll { scalaVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(scalaVersion, crossBuild.scalaVersionCatalog)

            def (String sourceSetId, SourceSet sourceSet) = findScalaCrossBuildSourceSet(scalaVersionInsights, sourceSets)

            def crossBuildScalaSourceDirSetJava = sourceSet.java
            crossBuildScalaSourceDirSetJava.setSrcDirs(main.java.getSrcDirs())

            def crossBuildScalaSourceDirSetScala = (SourceDirectorySet) sourceSet.scala
            crossBuildScalaSourceDirSetScala.setSrcDirs(mainScala.getSrcDirs())

            def crossBuildSourceDirSetResources = sourceSet.resources
            crossBuildSourceDirSetResources.setSrcDirs(main.resources.getSrcDirs())

            project.dependencies.add(sourceSet.compileConfigurationName, "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")

            configureResolution(project, sourceSet.compileConfigurationName, project.configurations.compile, scalaVersionInsights)
            configureResolution(project, sourceSet.compileOnlyConfigurationName, project.configurations.compileOnly, scalaVersionInsights)
            //TODO: From gradle 3.4 runtime should be subtituted with runtimeClasspath
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

    /**
     * Run assert statement in assertion Closure. If the assertion fails
     * we catch the exception. We use the message with the error appended with an user message
     * and throw a {@link GradleException}.
     *
     * @param message User message to be appended to assertion error message
     * @param assertion Assert statement(s) to run
     */
    private static final assertWithMsg(final String message, final Closure assertion) {
        try {
            // Run Closure with assert statement(s).
            assertion()
        } catch (AssertionError assertionError) {
            // Use Groovy power assert output from the assertionError
            // exception and append user message.
            final exceptionMessage = new StringBuilder(assertionError.message)
            exceptionMessage << System.properties['line.separator'] << System.properties['line.separator']
            exceptionMessage << message

            // Throw exception so Gradle knows the validation fails.
            throw new GradleException(exceptionMessage.toString(), assertionError)
        }
    }

    /**
     * Validation of the value for a given {@link TargetVerItem} instance.
     *
     * @param targetVersion Target version item created using Gradle model DSL
     */
    private static validateTargetVersion(TargetVerItem targetVersion) {
        def message = """\
            Property value is not set. Set a value in the model configuration.
             
            Example:
            -------
            model {
                crossBuild {
                    V211(ScalaVer) {
                        value = '2.11'
                    }
                    ...
                }
            }
            """.stripIndent()
        assertWithMsg(message) {
            assert targetVersion.value != null
            assert targetVersion.value ==~ /^(\d+\.)?(\d+\.)?(\d+)$/
        }
    }

    /**
     *
     * @param project
     * @return {@link SourceSetContainer} for the given {@link Project} instance or null.
     */
    private static final getSourceSets(Project project) {
        def sourceSets = project.hasProperty('sourceSets') ? (SourceSetContainer) project.sourceSets : null
        assert sourceSets != null: "Missing 'sourceSets' property under Project ${project.name} properties."
        sourceSets
    }

    /**
     * Find Scala source set id and instance in a source set container based on specific Scala version insights.
     *
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @param sourceSets Source set container (per project)
     * @return A tuple of source set id and its {@link SourceSet} instance
     */
    private static final findScalaCrossBuildSourceSet(ScalaVersionInsights scalaVersionInsights, SourceSetContainer sourceSets) {
        def sourceSetId = generateSourceSetId(scalaVersionInsights)
        def sourceSet = sourceSets.findByName(sourceSetId)
        new Tuple2(sourceSetId, sourceSet)
    }

    /**
     * Creates additional {@link SourceSet} per target version enlisted under mapped top level object crossBuild in model space.
     *
     * @param crossBuild Mapped top level object {@link CrossBuild} in model space
     * @param sourceSets Project source set container
     */
    private static final createCrossBuildScalaSourceSets(CrossBuild crossBuild, SourceSetContainer sourceSets) {
        def project = crossBuild.project
        def components = crossBuild.targetVersions.values()

        def sourceSetIds = components.collect { targetVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(targetVersion, crossBuild.scalaVersionCatalog)

            def sourceSetId = createCrossBuildScalaSourceSetIfNotExists(scalaVersionInsights, sourceSets)
            project.logger.info(LoggerUtils.logTemplate(project, "Creating source set (Post Evaluate Lifecycle): [${sourceSetId}]"))
            sourceSetId.toString()
        }

        // Remove unused source sets
        cleanSourceSetsContainer(sourceSets, sourceSetIds)

        // disable unused tasks
        def nonActiveSourceSetIds = findNonActiveSourceSetIds(components.collect { targetVersion -> targetVersion.value }.toSet())
        project.logger.info(LoggerUtils.logTemplate(project, "Non active source set ids: [${nonActiveSourceSetIds.join(", ")}]"))
        cleanTasksContainer(project.tasks, nonActiveSourceSetIds )
    }

    /**
     * Generates SourceSet id from a scala version info provided through {@link ScalaVersionInsights} object.
     *
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @return SourceSet id String
     */
    static final generateSourceSetId(ScalaVersionInsights scalaVersionInsights) { "$SOURCE_SET_BASE_NAME${scalaVersionInsights.strippedArtifactInlinedVersion}".toString() }

    static final createCrossBuildScalaSourceSetIfNotExists(ScalaVersionInsights scalaVersionInsights, SourceSetContainer sourceSets) {
        def sourceSetId = generateSourceSetId(scalaVersionInsights)

        if (!sourceSets.findByName(sourceSetId)) {
            // Create source set for specific scala version
            sourceSets.create(sourceSetId)
        }
        sourceSetId
    }

    private static final qmarkReplace(String template, String replacement) {
        template.replaceAll("\\?", replacement)
    }

    private static final cleanSourceSetsContainer(SourceSetContainer sourceSets, List<String> sourceSetIds) {
        // Remove unused source sets
        sourceSets.removeIf { it.name.contains(SOURCE_SET_BASE_NAME) && !sourceSetIds.contains(it.name) }
    }

    private static final findNonActiveSourceSetIds(Set<String> targetVersions) {
        // disable unused tasks
        def nonActiveTargetVersions = DEFAULT_SCALA_VERSION_CATALOG
                .mkRefTargetVersions()
        nonActiveTargetVersions.removeAll(targetVersions)

        def nonActiveSourceSetIds = nonActiveTargetVersions.collect {
            "${SOURCE_SET_BASE_NAME}${it.replaceAll("\\.", "")}"
        }
        nonActiveSourceSetIds.toSet()
    }

    private static final cleanTasksContainer(TaskContainer tasks, Set<String> nonActiveSourceSetIds) {
        tasks.removeAll(
                tasks.findAll { t ->
                    nonActiveSourceSetIds.findAll {
                        ssid -> t.name.toLowerCase().contains(ssid.toLowerCase())
                    }.size() > 0
                }
        )
    }

    /**
     * Resolve dependencies with globed version '?' for a crossbuild configuration
     *
     * @param project
     * @param crossBuildConfigurationName
     * @param parentConfiguration
     * @param scalaVersionInsights
     */
    private static final configureResolution(Project project, crossBuildConfigurationName, Configuration parentConfiguration, ScalaVersionInsights scalaVersionInsights) {
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
            if (requested.group.equals("org.scala-lang") && requested.name.equals("scala-library")) {
                details.useVersion(scalaVersionInsights.compilerVersion)
            }
        }
        config
    }
}
