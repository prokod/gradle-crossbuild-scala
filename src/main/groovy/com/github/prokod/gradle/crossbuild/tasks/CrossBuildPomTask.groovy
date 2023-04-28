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
package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPom
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.MutableActionSet

/**
 * Custom gradle task for cross building related Pom files
 */
class CrossBuildPomTask extends AbstractCrossBuildPomTask {

    @TaskAction
    void update() {
        def crossBuildSourceSet = getCrossBuildSourceSet()

        def originatedFromCrossBuildConfiguration = { ResolvedDependency dep ->
            dep.configuration.contains(CrossBuildSourceSets.SOURCESET_BASE_NAME)
        }

        def dependencyNameSelectorFunction = { ResolvedDependency dep ->
            originatedFromCrossBuildConfiguration(dep) ? dep.moduleArtifacts[0].name : dep.moduleName
        }

        def gradleClasspathConfigurationBasedDependencySetFunction = { Configuration configuration ->
            // If this is called and there were no repositories defined, an Exception will be raised
            // Caused by: org.gradle.internal.resolve.ModuleVersionNotFoundException: Cannot resolve external
            // dependency org.scala-lang:scala-library:2.10.6 because no repositories are defined.
            def deps = configuration.resolvedConfiguration.firstLevelModuleDependencies.collect { m ->
                project.dependencies.create(
                        group:m.moduleGroup,
                        name:dependencyNameSelectorFunction(m),
                        version:m.moduleVersion)
            }
            deps.toSet()
        }

        def compileConfiguration = project.configurations[crossBuildSourceSet.compileClasspathConfigurationName]
        def runtimeConfiguration = project.configurations[crossBuildSourceSet.runtimeClasspathConfigurationName]

        def gcc = gradleClasspathConfigurationBasedDependencySetFunction(compileConfiguration)
        def grc = gradleClasspathConfigurationBasedDependencySetFunction(runtimeConfiguration)

        def crossBuildPomAidingTuples =
                [createCrossBuildPomAidingConfigurationForMavenScope(ScopeType.COMPILE, gcc, grc, crossBuildSourceSet),
                 createCrossBuildPomAidingConfigurationForMavenScope(ScopeType.RUNTIME, gcc, grc, crossBuildSourceSet),
                 createCrossBuildPomAidingConfigurationForMavenScope(ScopeType.PROVIDED, gcc, grc, crossBuildSourceSet)]

        updateCrossBuildPublications(crossBuildPomAidingTuples.toSet(), crossBuildSourceSet)
    }

    /**
     * Creates a Compile Scope configuration that is used internally for enumerating the dependencies
     * for a cross build artifact's pom file.
     *
     * @param scopeType Maven type scopes COMPILE/RUNTIME...
     * @param gcc Gradle compileClasspath configuration for the sourceset
     * @param grc Gradle runtimeClasspath configuration for the sourceset
     * @param crossBuildSourceSet The sourceSet associated with the cross build artifact's pom file.
     * @return Tuple of  requested Scope and the created Configuration
     * @throws org.gradle.api.InvalidUserDataException if an object with the given name already exists in this
     *         container.
     */
    Tuple2<ScopeType, Configuration> createCrossBuildPomAidingConfigurationForMavenScope(ScopeType scopeType,
                                                                                         Set<Dependency> gcc,
                                                                                         Set<Dependency> grc,
                                                                                         SourceSet crossBuildSourceSet){

        def mavenScopeBasedDependencySetFunction = { ScopeType scope ->
            switch (scope) {
                case ScopeType.COMPILE:
                    def mrs = grc.intersect(gcc)
                    def mcs = gcc - mrs
                    // Maven compile scope
                    return mcs
                case ScopeType.RUNTIME:
                    // Maven runtime scope
                    def mrs = grc.intersect(gcc)
                    return mrs
                case ScopeType.PROVIDED:
                    // Short circuit for now
                    return [] as Set<Dependency>
            }
        }

        def createdTargetMavenScopeConfig = project.configurations.create(mavenScopeConfigurationNameFor(scopeType)) {
            canBeConsumed = false
            canBeResolved = false
        }

        project.logger.info(LoggerUtils.logTemplate(project,
                lifecycle:'task',
                sourceset:crossBuildSourceSet.name,
                msg:"Created Maven scope ${scopeType} related configuration: ${createdTargetMavenScopeConfig.name}"
        ))

        set(createdTargetMavenScopeConfig, mavenScopeBasedDependencySetFunction(scopeType))
        new Tuple2<>(scopeType, createdTargetMavenScopeConfig)
    }

    private void set(Configuration target, Set<Dependency> sourceDependencies) {
        target.dependencies.addAll(sourceDependencies)
    }

    private void updateCrossBuildPublications(Set<Tuple2<ScopeType, Configuration>> crossBuildPomAidingTuples,
                                              SourceSet crossBuildSourceSet) {
        def publishing = project.extensions.findByType(PublishingExtension)

        if (publishing == null) {
            throw new StopExecutionException('Publishing extension (\'maven-publish\' plugin) was not found in this ' +
                    'project.')
        }

        def pubs = publishing.publications.withType(MavenPublication).findAll {
            probablyRelatedPublication(it, crossBuildSourceSet.name)
        }

        if (pubs.size() == 0) {
            throw new StopExecutionException('Could not find corresponding publish block for ' +
                    "${crossBuildSourceSet.jarTaskName} task.")
        }

        if (pubs.size() > 1) {
            throw new StopExecutionException('Found more than one corresponding publish blocks ' +
                    "[${pubs*.name.join(', ')}] for ${crossBuildSourceSet.jarTaskName} task.")
        }

        def pub = pubs.head()

        Property jarBaseName = project.tasks.findByName(crossBuildSourceSet.jarTaskName).archiveBaseName
        pub.artifactId = jarBaseName.get()

        // todo try to replace internal api DefaultMavenPom getXmlAction ... with stable external api
        def defaultMavenPom = pub.getPom() as DefaultMavenPom
        def xmlActions = defaultMavenPom.getXmlAction() as MutableActionSet
        if (xmlActions.isEmpty()) {
            crossBuildPomAidingTuples.each { tuple ->
                def scope = tuple.first
                def configuration = tuple.second
                pub.pom.withXml { withXmlHandler(it, configuration, scope) }
            }
        }
    }

    static boolean probablyRelatedPublication(MavenPublication pub, String sourceSetId) {
        pub.name.contains(sourceSetId)
    }

    static boolean probablyRelatedPublicationTask(String name, String sourceSetId) {
        name.contains(sourceSetId.capitalize())
    }

    @Internal
    Closure<Void> withXmlHandler = {
        XmlProvider xmlProvider, Configuration pomAidingConfiguration, ScopeType scopeType ->
        def dependenciesNodeFunction = { XmlProvider xml ->
            def dependenciesNode = xml.asNode()['dependencies']?.getAt(0)
            if (dependenciesNode == null) {
                return xmlProvider.asNode().appendNode('dependencies')
            }
            dependenciesNode
        }

        def dependenciesNode = dependenciesNodeFunction(xmlProvider)

        pomAidingConfiguration.allDependencies.each { dep ->
            def dependencyNode = dependenciesNode.appendNode('dependency')
            dependencyNode.appendNode('groupId', dep.group)
            dependencyNode.appendNode('artifactId', dep.name)
            dependencyNode.appendNode('version', dep.version)
            dependencyNode.appendNode('scope', scopeType.toString().toLowerCase())
        }
    }
}
