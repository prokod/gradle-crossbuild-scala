package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
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
        def crossBuildPomAidingTuples =
                [createCrossBuildPomAidingConfigurationForMavenScope(ScopeType.COMPILE, crossBuildSourceSet),
                 createCrossBuildPomAidingConfigurationForMavenScope(ScopeType.RUNTIME, crossBuildSourceSet)]

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

        def mavenToGradleScope = { ScopeType scope ->
            switch (scope) {
                case ScopeType.COMPILE:
                    return project.configurations[crossBuildSourceSet.compileClasspathConfigurationName]
                case ScopeType.RUNTIME:
                    return project.configurations[crossBuildSourceSet.runtimeClasspathConfigurationName]
            }
        }

        def dependencySetFunction = { ScopeType scope ->
            switch (scope) {
                case ScopeType.COMPILE:
                    def compileConfiguration = mavenToGradleScope(scope)

                    // If this is called and there were no repositories defined, an Exception will be raised
                    // Caused by: org.gradle.internal.resolve.ModuleVersionNotFoundException: Cannot resolve external
                    // dependency org.scala-lang:scala-library:2.10.6 because no repositories are defined.
                    def deps = compileConfiguration.resolvedConfiguration.firstLevelModuleDependencies.collect { m ->
                        project.dependencies.create(
                                group:m.moduleGroup,
                                name:m.configuration.contains(CrossBuildSourceSets.SOURCESET_BASE_NAME) ?
                                        m.moduleArtifacts[0].name : m.moduleName,
                                version:m.moduleVersion)
                    }
                    return deps.toSet()
                case ScopeType.RUNTIME:
                    def runtimeConfiguration = mavenToGradleScope(scope)
                    def compileConfiguration = mavenToGradleScope(ScopeType.COMPILE)

                    def runtimeModuleDeps = runtimeConfiguration.resolvedConfiguration.firstLevelModuleDependencies
                    def compileModuleDeps = compileConfiguration.resolvedConfiguration.firstLevelModuleDependencies

                    def comparableCompileModuleDependencies =
                            compileModuleDeps.collect { "$it.moduleGroup:$it.moduleName:$it.moduleVersion" }
                    def filteredRuntimeModuleDependencies = runtimeModuleDeps.findAll { md ->
                        def comparableRuntimeModuleDependency = "$md.moduleGroup:$md.moduleName:$md.moduleVersion"
                        !comparableCompileModuleDependencies.contains(comparableRuntimeModuleDependency)
                    }
                    def deps = filteredRuntimeModuleDependencies.collect { m ->
                        project.dependencies.create(
                                group:m.moduleGroup,
                                name:m.configuration.contains(CrossBuildSourceSets.SOURCESET_BASE_NAME) ?
                                        m.moduleArtifacts[0].name : m.moduleName,
                                version:m.moduleVersion)
                    }
                    return deps.toSet()
            }
        }

        def createdTargetMavenScopeConfig = project.configurations.create(mavenScopeConfigurationNameFor(scopeType)) {
            canBeConsumed = false
            canBeResolved = false
        }

        project.logger.info(LoggerUtils.logTemplate(project,
                lifecycle:'task',
                sourceset:crossBuildSourceSet.name,
                configuration:mavenToGradleScope(scopeType).name,
                msg:"Created Maven scope ${scopeType} related configuration: ${createdTargetMavenScopeConfig.name}"
        ))

        set(createdTargetMavenScopeConfig, dependencySetFunction(scopeType))
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

        def jarBaseName = project.tasks.findByName(crossBuildSourceSet.jarTaskName).baseName
        pub.artifactId = jarBaseName

        crossBuildPomAidingTuples.each { tuple ->
            def scope = tuple.first
            def configuration = tuple.second
            def defaultMavenPom = pub.getPom() as DefaultMavenPom
            def xmlActions = defaultMavenPom.getXmlAction() as MutableActionSet
            if (xmlActions.isEmpty()) {
                pub.pom.withXml { withXmlHandler(it, configuration, scope) }
            }
            else {
                xmlActions.
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
