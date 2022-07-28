/*
 * Copyright 2016-2022 the original author or authors
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

import com.github.prokod.gradle.crossbuild.model.ArchiveNaming
import com.github.prokod.gradle.crossbuild.model.Build
import com.github.prokod.gradle.crossbuild.model.BuildUpdateEvent
import com.github.prokod.gradle.crossbuild.model.BuildUpdateEventStore
import com.github.prokod.gradle.crossbuild.model.DependencyLimitedInsight
import com.github.prokod.gradle.crossbuild.model.EventType
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar

/**
 * Extension class impl. for thec cross build plugin
 */
class CrossBuildExtension {

    final Project project
    final CrossBuildSourceSets crossBuildSourceSets
    final ObjectFactory objects

    Map<String, String> scalaVersionsCatalog = [:]

    ArchiveNaming archive

    Set<Configuration> configurations = []

    NamedDomainObjectContainer<Build> builds

    Collection<ResolvedBuildAfterEvalLifeCycle> resolvedBuilds = []

    CrossBuildExtension(Project project, ObjectFactory objectFactory) {
        this.project = project
        this.objects = objectFactory

        this.archive = project.objects.newInstance(ArchiveNaming,
                'DefaultArchiveNaming', '_?', new BuildUpdateEventStore(project))

        this.crossBuildSourceSets = new CrossBuildSourceSets(project, objects)

        this.builds = project.container(Build, buildFactory)
    }

    private final Closure buildFactory = { String name -> new Build(name, this) }

    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Action<? super ArchiveNaming> action) {
        action.execute(archive)

        def alreadyResolved = resolvedBuilds*.delegate
        builds.all { Build build ->
            if (!alreadyResolved.contains(build)) {
                applyArchiveDefaults(build)
            }
        }
    }

    @SuppressWarnings(['ConfusingMethodName', 'BuilderMethodWithSideEffects', 'FactoryMethodName'])
    void builds(Action<? super NamedDomainObjectContainer<Build>> action) {
        action.execute(builds)

        def alreadyResolved = resolvedBuilds*.delegate
        builds.all { Build build ->
            if (!alreadyResolved.contains(build)) {
                updateExtension(build)
            }
        }
    }

    void applyArchiveDefaults(Build build) {
        build.archive.appendixPattern = this.archive.appendixPattern
    }

    /**
     * Also realize cross build tasks
     *
     * @param build
     */
    void updateExtension(Build build) {
        def project = build.extension.project
        def sv = ScalaVersions.withDefaultsAsFallback(scalaVersionsCatalog)

        build.eventStore.onEvent { BuildUpdateEvent event ->
            def resolvedBuilds = BuildResolver.resolve(build, sv)

            if (event.eventType == EventType.SCALA_VERSIONS_UPDATE) {
                // Create cross build source sets
                project.logger.debug(LoggerUtils.logTemplate(project,
                        lifecycle:'config',
                        msg:'`onEvent` callback triggered. Going to create source-sets accordingly.\n' +
                                'Event source (build):\n-----------------\n' +
                                "${event.source.toString()}\n" +
                                "Current build:\n------------\n${build.toString()}\n" +
                                "Resolved builds:\n------------\n${resolvedBuilds*.toString().join('\n')}"
                ))
                crossBuildSourceSets.fromBuilds(resolvedBuilds)

                this.resolvedBuilds.addAll(resolvedBuilds)

                updateExtraProperties(resolvedBuilds)
                realizeCrossBuildTasks(resolvedBuilds)
            }
            else {
                updateCrossBuildTasks(resolvedBuilds)
            }
        }
    }

    void updateExtraProperties(Collection<ResolvedBuildAfterEvalLifeCycle> resolvedBuilds) {
        resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = crossBuildSourceSets.findByName(rb.name)
            for (extraProperty in rb.delegate.ext) {
                CrossBuildSourceSets.addExtraProperty(sourceSet, extraProperty.key, extraProperty.value)
            }
        }
    }

    void realizeCrossBuildTasks(Collection<ResolvedBuildAfterEvalLifeCycle> resolvedBuilds) {
        resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) =
                                    crossBuildSourceSets.findByName(rb.name)

            def scalaJar = createArtifact(sourceSet, rb)
            // Runtime
            def outgoingConfigurationRuntime =
                    configureOutgoingConfiguration(sourceSet.getRuntimeElementsConfigurationName(),
                            rb.scalaVersion,
                            Usage.JAVA_RUNTIME)
            attachArtifact(scalaJar, outgoingConfigurationRuntime.name)
            // Api
            def outgoingConfigurationApi =
                    configureOutgoingConfiguration(sourceSet.getApiElementsConfigurationName(),
                            rb.scalaVersion,
                            Usage.JAVA_API)
            attachArtifact(scalaJar, outgoingConfigurationApi.name)
        }
    }

    Configuration configureOutgoingConfiguration(String outgoingConfigurationName, String scalaVersion, String usage) {
        Configuration outgoingConfiguration =
                project.configurations.getByName(outgoingConfigurationName) { Configuration cnf ->
            cnf.attributes {
                it.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                it.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, usage))
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
                it.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                        JavaVersion.current().majorVersion.toInteger())
                it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        objects.named(LibraryElements, "scala-${scalaVersion}-jar"))
            }
        }
        outgoingConfiguration
    }

    Jar createArtifact(SourceSet sourceSet, ResolvedBuildAfterEvalLifeCycle rb) {
        def scalaJar = project.tasks.create(sourceSet.getJarTaskName(), Jar) { Jar jar ->
            jar.group = BasePlugin.BUILD_GROUP
            jar.description = 'Assembles a jar archive containing ' +
                    "${rb.scalaVersionInsights.strippedArtifactInlinedVersion} classes"
            jar.archiveBaseName.set(archiveBaseName.get() + rb.archive.appendix)
            jar.from sourceSet.output
        }

        project.logger.info(LoggerUtils.logTemplate(project,
                lifecycle:'config',
                msg:"Created crossbuild Jar task for sourceSet ${sourceSet.name}." +
                        " [Resolved Jar archiveBaseName (w/ appendix): ${scalaJar.archiveBaseName.get()}]"))
        scalaJar
    }

    void attachArtifact(Jar scalaJar, String configurationName) {
        project.artifacts.add(configurationName, scalaJar)
    }

    void updateCrossBuildTasks(Collection<ResolvedBuildAfterEvalLifeCycle> resolvedBuilds) {
        def sv = ScalaVersions.withDefaultsAsFallback(scalaVersionsCatalog)
        resolvedBuilds.findAll { rb ->
            def (String sourceSetId, SourceSet sourceSet) = crossBuildSourceSets.findByName(rb.name)

            // Guard against sourceSet being null or task by name not found.
            // TODO: Eliminate the use of this null guarding by limiting the calls to this code to valid cases only
            def task = sourceSet?.getJarTaskName() != null ? project.tasks.findByName(sourceSet.getJarTaskName()) : null

            if (task != null) {
                def origBaseName =
                        DependencyLimitedInsight.parseByDependencyName(task.archiveBaseName.get(), sv).baseName
                task.configure {
                    archiveBaseName.set(origBaseName + rb.archive.appendix)
                }

                project.logger.info(LoggerUtils.logTemplate(project,
                        lifecycle:'config',
                        msg:"Updated crossbuild Jar task for sourceSet ${sourceSetId}." +
                                " [Resolved Jar archiveBaseName (w/ appendix): ${task.archiveBaseName.get()}]"))
            }
        }
    }
}
