/*
 * Copyright 2019-2022 the original author or authors
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

import com.github.prokod.gradle.crossbuild.model.ResolvedBuildConfigLifecycle
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.model.ObjectFactory

/**
 * {@link SourceSetContainer} manipulation class to set and view CrossBuild SourceSets
 *
 */
class CrossBuildSourceSets {
    static final String SOURCESET_BASE_NAME = 'crossBuild'

    private final Project project
    private final ObjectFactory objectFactory
    final SourceSetContainer container

    CrossBuildSourceSets(Project project, ObjectFactory objectFactory) {
        this.project = project
        this.objectFactory = objectFactory
        this.container = getSourceSetContainer(project)
    }

    /**
     * Creates additional {@link org.gradle.api.tasks.SourceSet} per scala version for each build item enlisted under
     * {@code builds {}} block within crossBuild DSL.
     *
     * @param builds resolved builds collection to create/get source-sets from
     * @return set of source-set ids the were created/retrieved
     *
     * @throws AssertionError if builds collection is null
     */
    List<String> fromBuilds(Collection<ResolvedBuildConfigLifecycle> builds) {
        assert builds != null : 'builds should not be null'
        def sourceSetIds = builds.collect { build ->
            def (sourceSetId, SourceSet sourceSet) = getOrCreateCrossBuildScalaSourceSet(build.name)

            addExtraProperty(sourceSet, 'scalaCompilerVersion', build.scalaVersionInsights.compilerVersion)

            def implementationConfig = project.configurations.getByName(sourceSet.getImplementationConfigurationName())

            def runtimeOnlyConfig = project.configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName())

            def createdApiConfig =
                    project.configurations.create(sourceSet.getApiConfigurationName()) { Configuration cnf ->
                cnf.canBeConsumed = false
                cnf.canBeResolved = false
            }

            def createdApiElementsConfig =
                    project.configurations.create(sourceSet.getApiElementsConfigurationName()) { Configuration cnf ->
                cnf.canBeConsumed = true
                cnf.canBeResolved = false

                cnf.attributes {
                    it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                    objectFactory.named(LibraryElements, "scala-${build.scalaVersion}-jar"))
                }
            }

            def createdRuntimeElementsConfig =
                    project.configurations.create(sourceSet.getRuntimeElementsConfigurationName()) { Configuration c ->
                c.canBeConsumed = true
                c.canBeResolved = false

                c.attributes {
                    it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                    objectFactory.named(LibraryElements, "scala-${build.scalaVersion}-jar"))
                }
            }

            implementationConfig.extendsFrom(createdApiConfig)
            createdApiElementsConfig.extendsFrom(createdApiConfig)
            createdRuntimeElementsConfig.extendsFrom(implementationConfig, runtimeOnlyConfig)

            project.logger.info(LoggerUtils.logTemplate(project,
                    lifecycle:'config',
                    msg:"Creating source set (User request): [${sourceSetId}]"))
            sourceSetId.toString()
        }
        sourceSetIds
    }

    /**
     * Inject extra property to ExtensionAware cross build sourceset
     */
    static void addExtraProperty(SourceSet sourceSet, String name, Object value) {
        def extraProperties = sourceSet.extensions.findByType(ExtraPropertiesExtension)
        extraProperties.set(name, value)
    }
    /**
     * Find Scala source set id and instance in a source set container based on specific Scala version insights.
     *
     * @param buildName plugin DSL build item name
     * @return A tuple of source set id and its {@link SourceSet} instance
     */
    Tuple2<String, SourceSet> findByName(String buildName) {
        def sourceSetId = generateSourceSetId(buildName)
        def sourceSet = container.findByName(sourceSetId)
        new Tuple2(sourceSetId, sourceSet)
    }

    Tuple2<String, SourceSet> getOrCreateCrossBuildScalaSourceSet(String buildName) {
        def sourceSetId = generateSourceSetId(buildName)

        def crossBuildSourceSet = [sourceSetId].collect { id ->
            new Tuple2<String, SourceSet>(id, container.findByName(id)) }.collect { tuple ->
            if (tuple.second == null) {
                new Tuple2<String, SourceSet>(tuple.first, container.create(tuple.first))
            } else {
                tuple
            }
        }.first()

        crossBuildSourceSet
    }

    /**
     * Generates SourceSet id from a scala version info provided through {@link ScalaVersionInsights} object.
     *
     * @param buildName plugin DSL build item name
     * @return A tuple of source set id and its {@link org.gradle.api.tasks.SourceSet} instance
     */
    static String generateSourceSetId(String buildName) {
        "$SOURCESET_BASE_NAME${buildName}".toString()
    }

    /**
     * get {@link org.gradle.api.tasks.SourceSetContainer} from the project
     *
     * @param project
     * @return {@link org.gradle.api.tasks.SourceSetContainer} for the given {@link org.gradle.api.Project} instance.
     * @throws AssertionError if {@link org.gradle.api.tasks.SourceSetContainer} is null for the given project
     */
    static SourceSetContainer getSourceSetContainer(Project project) {
        def sourceSets = project.findProperty('sourceSets') ? (SourceSetContainer)project.sourceSets : null
        assert sourceSets != null : "Missing 'sourceSets' property under Project ${project.name} properties."
        sourceSets
    }
}
