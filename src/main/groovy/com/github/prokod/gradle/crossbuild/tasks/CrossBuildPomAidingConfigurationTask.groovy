package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.CrossBuildSourceSets
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction

class CrossBuildPomAidingConfigurationTask extends AbstractCrossBuildPomTask {

    @TaskAction
    def update() {
        def crossBuildSourceSet = getCrossBuildSourceSet()
        def crossBuildPomAidingTuples = [createAndSetForMavenScope(ScopeType.COMPILE, crossBuildSourceSet),
        createAndSetForMavenScope(ScopeType.RUNTIME, crossBuildSourceSet)] as Set

        updateCrossBuildPublications(crossBuildPomAidingTuples, crossBuildSourceSet)
    }

    /**
     * Creates a Compile Scope configuration that should be used by the user for generating the dependencies
     *  for a cross build artifact's pom file.
     *
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @return Created configuration
     * @throws org.gradle.api.InvalidUserDataException if an object with the given name already exists in this
     *         container.
     */
    Tuple2<ScopeType, Configuration> createAndSetForMavenScope(ScopeType scopeType, SourceSet crossBuildSourceSet) {

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
                    def deps = compileConfiguration.resolvedConfiguration.firstLevelModuleDependencies.collect { module ->
                        project.dependencies.create(
                                group: module.moduleGroup,
                                name: module.configuration.contains(CrossBuildSourceSets.SOURCESET_BASE_NAME) /*&& moduleNames.contains(module.moduleName)*/ ? module.moduleArtifacts[0].name : module.moduleName,
                                version: module.moduleVersion)
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
                    def deps = filteredRuntimeModuleDependencies.collect { module ->
                        project.dependencies.create(
                                group: module.moduleGroup,
                                name: module.configuration.contains(CrossBuildSourceSets.SOURCESET_BASE_NAME) /*&& moduleNames.contains(module.moduleName)*/ ? module.moduleArtifacts[0].name : module.moduleName,
                                version: module.moduleVersion)
                    }
                    return deps.toSet()
            }
        }

        // todo consider switching to detached configuration
        def createdTargetMavenScopeConfig =
                project.configurations.create(mavenScopeConfigurationNameFor(scopeType)) {
                    canBeConsumed = false
                    canBeResolved = false
                }

        project.logger.info(LoggerUtils.logTemplate(project,
                lifecycle: 'task',
                sourceset: crossBuildSourceSet.name,
                configuration: mavenToGradleScope(scopeType).name,
                msg: "Created Maven scope ${scopeType} related configuration: ${createdTargetMavenScopeConfig.name}"
        ))

        set(createdTargetMavenScopeConfig, dependencySetFunction(scopeType))
        new Tuple2<>(scopeType, createdTargetMavenScopeConfig)
    }

    private void set(Configuration target, Set<Dependency> sourceDependencies) {
        target.dependencies.addAll(sourceDependencies)
    }

    private void updateCrossBuildPublications(Set<Tuple2<ScopeType, Configuration>> crossBuildPomAidingTuples, SourceSet crossBuildSourceSet) {
        def publishing = project.extensions.findByType(PublishingExtension)

        if (publishing == null) throw new StopExecutionException("Publishing extension ('maven-publish' plugin) was " +
                "not found in this project.")

        def pubs = publishing.publications.withType(MavenPublication).findAll {
            probablyRelatedPublication(it, resolvedBuild, crossBuildSourceSet.name)
        }

        if (pubs.size() == 0) throw new StopExecutionException("Could not find corresponding publish block for " +
                "${crossBuildSourceSet.jarTaskName} task.")

        if (pubs.size() > 1) throw new StopExecutionException("Found more than one corresponding publish blocks " +
                "[${pubs*.name.join(', ')}] for ${crossBuildSourceSet.jarTaskName} task.")

        def pub = pubs.head()

        def jarBaseName = project.tasks.findByName(crossBuildSourceSet.jarTaskName).baseName
        pub.artifactId = jarBaseName

        crossBuildPomAidingTuples.each { tuple ->
            def scope = tuple.first
            def configuration = tuple.second
            pub.pom.withXml { xmlProvider ->
                //withXmlHandler(it, configuration, scope)
                def dependenciesNodeFunction = { XmlProvider xml ->
                    def dependenciesNode = xml.asNode()['dependencies']?.getAt(0)
                    if (dependenciesNode == null) {
                        return xmlProvider.asNode().appendNode('dependencies')
                    }
                    dependenciesNode
                }

                def dependenciesNode = dependenciesNodeFunction(xmlProvider)

                configuration.allDependencies.each { dep ->
                    def dependencyNode = dependenciesNode.appendNode('dependency')
                    dependencyNode.appendNode('groupId', dep.group)
                    dependencyNode.appendNode('artifactId', dep.name)
                    dependencyNode.appendNode('version', dep.version)
                    dependencyNode.appendNode('scope', scope.toString().toLowerCase())
                }
            }
        }
    }

    static boolean probablyRelatedPublication(MavenPublication pub,
                                              ResolvedBuildAfterEvalLifeCycle targetVersion,
                                              String sourceSetId) {
        pub.artifactId.endsWith(targetVersion.archive.appendix) || pub.name.contains(sourceSetId)
    }

    static boolean probablyRelatedPublication(String name,
                                              String sourceSetId) {
        name.contains(sourceSetId.capitalize())
    }

    private static void withXmlHandler(XmlProvider xmlProvider,
                                Configuration pomAidingConfiguration,
                                ScopeType scopeType) {
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