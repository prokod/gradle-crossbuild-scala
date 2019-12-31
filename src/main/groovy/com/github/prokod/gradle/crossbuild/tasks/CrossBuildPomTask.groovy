package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction

/**
 * Custom gradle task for cross building related Pom files
 */
class CrossBuildPomTask extends AbstractCrossBuildPomTask {

    @TaskAction
    void update() {
        def crossBuildSourceSet = getCrossBuildSourceSet()
        def crossBuildPomAidingTuples =
                [createCrossBuildPomAidingConfigurationForMavenScope(ScopeType.COMPILE, crossBuildSourceSet),
                 createCrossBuildPomAidingConfigurationForMavenScope(ScopeType.RUNTIME, crossBuildSourceSet),
                 createCrossBuildPomAidingConfigurationForMavenScope(ScopeType.PROVIDED, crossBuildSourceSet),
                ]

        updateCrossBuildPublications(crossBuildPomAidingTuples.toSet(), crossBuildSourceSet)
    }

    /**
     * Creates a Compile Scope configuration that is used internally for enumerating the dependencies
     * for a cross build artifact's pom file.
     *
     * @param scopeType Maven type scopes COMPILE/RUNTIME...
     * @param crossBuildSourceSet The sourceSet associated with the cross build artifact's pom file.
     * @return Tuple of  requested Scope and the created Configuration
     * @throws org.gradle.api.InvalidUserDataException if an object with the given name already exists in this
     *         container.
     */
    Tuple2<ScopeType, Configuration> createCrossBuildPomAidingConfigurationForMavenScope(ScopeType scopeType,
                                                                                         SourceSet crossBuildSourceSet){

        def resolveSourceSet = { ScopeType scope ->
            def sourceSetName = {
                switch (scope) {
                    case ScopeType.COMPILE:
                        return crossBuildSourceSet.compileClasspathConfigurationName
                    case ScopeType.PROVIDED:
                        return crossBuildSourceSet.compileOnlyConfigurationName
                    case ScopeType.RUNTIME:
                        return crossBuildSourceSet.runtimeClasspathConfigurationName
                }
            }

            project
                .configurations[sourceSetName()]
                .resolvedConfiguration
                .firstLevelModuleDependencies
        }

        def toComparableDependency = { ResolvedDependency dep ->
            "$dep.moduleGroup:$dep.moduleName:$dep.moduleVersion"
        }

        def dependencyFilter = { ScopeType scope ->
            def dependencies = resolveSourceSet(scope)
                .collect { it -> toComparableDependency(it) };

           { ResolvedDependency dep ->
               dependencies.contains(toComparableDependency(dep))
           }
        }

        def resolveDependency = {ResolvedDependency m ->
            project.dependencies.create(
                group:m.moduleGroup,
                name:m.configuration.contains(CrossBuildSourceSets.SOURCESET_BASE_NAME) ?
                    m.moduleArtifacts[0].name : m.moduleName,
                version:m.moduleVersion,
            )
        }

        def dependencySetFunction = { ScopeType scope ->
            switch (scope) {
                case ScopeType.PROVIDED:
                    def providedDeps = resolveSourceSet(ScopeType.PROVIDED)
                    return providedDeps
                        .collect { m ->
                            project.dependencies.create(
                                group:m.moduleGroup,
                                name:m.configuration.contains(CrossBuildSourceSets.SOURCESET_BASE_NAME) ?
                                    m.moduleArtifacts[0].name : m.moduleName,
                                version:m.moduleVersion
                            )
                        }

                case ScopeType.COMPILE:
                    def compileModuleDeps = resolveSourceSet(ScopeType.COMPILE)
                    def inProvidedSourceSet = dependencyFilter(ScopeType.PROVIDED)

                    // If this is called and there were no repositories defined, an Exception will be raised
                    // Caused by: org.gradle.internal.resolve.ModuleVersionNotFoundException: Cannot resolve external
                    // dependency org.scala-lang:scala-library:2.10.6 because no repositories are defined.
                    return compileModuleDeps
                        .findAll{md -> !inProvidedSourceSet(md) }
                        .collect { m -> resolveDependency(m) }

                case ScopeType.RUNTIME:
                    def runtimeModuleDeps = resolveSourceSet(ScopeType.RUNTIME)
                    def inCompileSoureSet = dependencyFilter(ScopeType.COMPILE)


                    return runtimeModuleDeps
                        .findAll { md -> !inCompileSoureSet(md) }
                        .collect { m -> resolveDependency(m) }

            }
        }

        def createdTargetMavenScopeConfig = project.configurations.create(mavenScopeConfigurationNameFor(scopeType)) {
            canBeConsumed = false
            canBeResolved = false
        }

        project.logger.info(LoggerUtils.logTemplate(project,
                lifecycle:'task',
                sourceset:crossBuildSourceSet.name,
                configuration:resolveSourceSet(scopeType).name,
                msg:"Created Maven scope ${scopeType} related configuration: ${createdTargetMavenScopeConfig.name}"
        ))

        setDependencies(createdTargetMavenScopeConfig, dependencySetFunction(scopeType).toSet())
        new Tuple2<>(scopeType, createdTargetMavenScopeConfig)
    }

    private void setDependencies(Configuration target, Set<Dependency> sourceDependencies) {
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

        def jarBaseName = project.tasks.findByName(crossBuildSourceSet.jarTaskName).baseName
        pub.artifactId = jarBaseName

        crossBuildPomAidingTuples.each { tuple ->
            def scope = tuple.first
            def configuration = tuple.second
            pub.pom.withXml { withXmlHandler(it, configuration, scope) }
        }
    }

    static boolean probablyRelatedPublication(MavenPublication pub, String sourceSetId) {
        pub.name.contains(sourceSetId)
    }

    static boolean probablyRelatedPublicationTask(String name, String sourceSetId) {
        name.contains(sourceSetId.capitalize())
    }

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
