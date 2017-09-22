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
import com.github.prokod.gradle.crossbuild.CrossBuildPlugin
import com.github.prokod.gradle.crossbuild.DummyScalaVer
import com.github.prokod.gradle.crossbuild.ScalaVersionCatalog
import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.model.CrossBuild
import com.github.prokod.gradle.crossbuild.model.TargetVerItem
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.model.*

class CrossBuildPluginRules extends RuleSource {
    static final SOURCE_SET_BASE_NAME = "crossBuild"
    static
    final DEFAULT_SCALA_VERSION_CATALOG =
            new ScalaVersionCatalog(['2.9':'2.9.3', '2.10':'2.10.6', '2.11':'2.11.8', '2.12':'2.12.1'])

    @Model
    void crossBuild(CrossBuild crossBuild) {
    }

    @Defaults
    void setDefaultVersionCatalog(CrossBuild crossBuild) {
        crossBuild.scalaVersionCatalog = DEFAULT_SCALA_VERSION_CATALOG
    }

    @Defaults
    void setDefaultArchiveAppendix(@Each TargetVerItem item) {
        item.archiveAppendix = '_?'
    }

    @Mutate
    void setProjectViaBridge(CrossBuild crossBuild, ExtensionContainer extensions) {
        def extension = (BridgingExtension) extensions.bridging
        def project = extension.project
        crossBuild.project = project
        crossBuild.archivesBaseName = project.archivesBaseName
    }

    @Mutate
    void setTargetVerItems(
            @Each TargetVerItem targetVersion,
            @Path("crossBuild.archivesBaseName") String archivesBaseName,
            @Path("crossBuild.scalaVersionCatalog") ScalaVersionCatalog scalaVersionCatalog) {
        validateTargetVersion(targetVersion)
        def scalaVersionInsights = new ScalaVersionInsights(targetVersion, scalaVersionCatalog)
        def interpretedBaseName = generateCrossArchivesBaseName(
                archivesBaseName, targetVersion.archiveAppendix, scalaVersionInsights.artifactInlinedVersion)
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

            def (String sourceSetId, SourceSet sourceSet) = findScalaCrossBuildSourceSet(
                    scalaVersionInsights, sourceSets)

            def crossBuildScalaSourceDirSetJava = sourceSet.java
            crossBuildScalaSourceDirSetJava.setSrcDirs(main.java.getSrcDirs())

            def crossBuildScalaSourceDirSetScala = (SourceDirectorySet) sourceSet.scala
            crossBuildScalaSourceDirSetScala.setSrcDirs(mainScala.getSrcDirs())

            def crossBuildSourceDirSetResources = sourceSet.resources
            crossBuildSourceDirSetResources.setSrcDirs(main.resources.getSrcDirs())

            project.dependencies.add(sourceSet.compileConfigurationName,
                    "org.scala-lang:scala-library:${scalaVersionInsights.compilerVersion}")

            configureResolution(
                    project, sourceSet.compileConfigurationName, project.configurations.compile,
                    scalaVersionInsights, crossBuild.scalaVersionCatalog)
            configureResolution(
                    project, sourceSet.compileClasspathConfigurationName, project.configurations.compileClasspath,
                    scalaVersionInsights, crossBuild.scalaVersionCatalog)
            configureResolution(
                    project, sourceSet.compileOnlyConfigurationName, project.configurations.compileOnly,
                    scalaVersionInsights, crossBuild.scalaVersionCatalog)
            //TODO: From gradle 3.4 runtime should be subtituted with runtimeClasspath
            configureResolution(
                    project, sourceSet.runtimeConfigurationName, project.configurations.runtime,
                    scalaVersionInsights, crossBuild.scalaVersionCatalog)

            configureTestResolution(project, crossBuild.scalaVersionCatalog)

            createPomAidingCompileScopeConfiguration(
                    sourceSet, project, scalaVersionInsights, scalaVersion.archiveAppendix)

            def interpretedBaseName = generateCrossArchivesBaseName(
                    crossBuild.archivesBaseName, scalaVersion.archiveAppendix,
                    scalaVersionInsights.artifactInlinedVersion)
            project.logger.info(LoggerUtils.logTemplate(project,
                    "Cross build jar ${sourceSetId} baseName = '${interpretedBaseName}'"))

            tasks.create(sourceSet.getJarTaskName(), Jar) {
                group = BasePlugin.BUILD_GROUP
                description = "Assembles a jar archive containing " +
                        "${scalaVersionInsights.strippedArtifactInlinedVersion} classes"
                baseName = interpretedBaseName
                from sourceSet.output
            }

            tasks.get(main.getClassesTaskName()).setEnabled(false)
            tasks.get(main.getCompileTaskName('scala')).setEnabled(false)
            tasks.get(main.getCompileTaskName('java')).setEnabled(false)
            tasks.get(main.getJarTaskName()).setEnabled(false)

            tasks.withType(ScalaCompile) { t ->
                if (t.name == sourceSet.getCompileTaskName('scala')) {
                    def analysisFile = t.scalaCompileOptions.incrementalOptions.analysisFile
                    if (!analysisFile) {
                        t.scalaCompileOptions
                                .incrementalOptions
                                .setAnalysisFile(new File(
                                "$project.buildDir/tmp/scala/compilerAnalysis/" +
                                        "${scalaVersionInsights.compilerVersion}/${project.name}.analysis"))
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
    private static
    final findScalaCrossBuildSourceSet(ScalaVersionInsights scalaVersionInsights, SourceSetContainer sourceSets) {
        def sourceSetId = generateSourceSetId(scalaVersionInsights)
        def sourceSet = sourceSets.findByName(sourceSetId)
        new Tuple2(sourceSetId, sourceSet)
    }

    /**
     * Creates additional {@link SourceSet} per target version enlisted under
     *  mapped top level object crossBuild in model space.
     *
     * @param crossBuild Mapped top level object {@link CrossBuild} in model space
     * @param sourceSets Project source set container
     */
    private static final createCrossBuildScalaSourceSets(CrossBuild crossBuild, SourceSetContainer sourceSets) {
        def project = crossBuild.project
        def components = crossBuild.targetVersions.values()

        def sourceSetIds = components.collect { targetVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(targetVersion, crossBuild.scalaVersionCatalog)

            def (sourceSetId, sourceSet) = createCrossBuildScalaSourceSetIfNotExists(scalaVersionInsights, sourceSets)
            project.logger.info(LoggerUtils.logTemplate(project,
                    "Creating source set (Post Evaluate Lifecycle): [${sourceSetId}]"))
            sourceSetId.toString()
        }

        // Remove unused source sets
        cleanSourceSetsContainer(sourceSets, sourceSetIds)

        // disable unused tasks
        def nonActiveSourceSetIds = findNonActiveSourceSetIds(
                components.collect { targetVersion -> targetVersion.value }.toSet())
        project.logger.info(LoggerUtils.logTemplate(project,
                "Non active source set ids: [${nonActiveSourceSetIds.join(", ")}]"))
        cleanTasksContainer(project.tasks, nonActiveSourceSetIds)
    }

    /**
     * Generates SourceSet id from a scala version info provided through {@link ScalaVersionInsights} object.
     *
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @return A tuple of source set id and its {@link SourceSet} instance
     */
    static final generateSourceSetId(ScalaVersionInsights scalaVersionInsights) {
        "$SOURCE_SET_BASE_NAME${scalaVersionInsights.strippedArtifactInlinedVersion}".toString()
    }

    static final createCrossBuildScalaSourceSetIfNotExists(
            ScalaVersionInsights scalaVersionInsights,
            SourceSetContainer sourceSets) {
        def sourceSetId = generateSourceSetId(scalaVersionInsights)

        def tuple = [sourceSetId]
                .collect { new Tuple2(sourceSetId, sourceSets.findByName(sourceSetId)) }
                .collect {
            if (!it.second) {
                new Tuple(it.first, sourceSets.create(it.first))
            } else {
                new Tuple(it.first, it.second)
            }
        }.first()
        tuple
    }

    private static final qmarkReplace(String template, String replacement) {
        template.replaceAll("\\?", replacement)
    }

    /**
     * Generates archives base name based on 'archivesBaseName', archiveAppendix which might include '?' placeholder and
     *  'artifactInlinedVersion' which will be used to fill '?' placeholder.
     *
     * @param archivesBaseName Name of archive prefixing '_' For example in lib... => 'lib'
     * @param archiveAppendix For example in lib_? => '_?'
     * @param artifactInlinedVersion Scala convention inlined version For example '2.11'
     * @return Interpreted archivesBaseName
     */
    private static final generateCrossArchivesBaseName(archivesBaseName, archiveAppendix, artifactInlinedVersion) {
        "${archivesBaseName}${qmarkReplace(archiveAppendix, artifactInlinedVersion)}".toString()
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
     * Creates a Compile Scope configuration that should be used by the user for generating the dependencies
     *  for a cross build artifact's pom file.
     *
     * @param sourceSet A specific {@link SourceSet} that provides a configuration
     *                   to use as source of dependencies for the new configuration
     * @param project Project space {@link Project}
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @param archiveAppendix {@link TargetVerItem} archiveAppendix to aid with the replacement
     *                         of non cross build {@link ProjectDependency} with its cross build counterpart
     */
    private static final createPomAidingCompileScopeConfiguration(
            SourceSet sourceSet,
            Project project,
            ScalaVersionInsights scalaVersionInsights,
            archiveAppendix) {
        def sourceConfig = project.configurations[sourceSet.runtimeConfigurationName]
        def targetCompileScopeConfig = project.configurations.create("${sourceSet.name}MavenCompileScope")

        createPomAidingConfiguration(
                sourceConfig, targetCompileScopeConfig, project, scalaVersionInsights, archiveAppendix)
    }

    /**
     * Creates a configuration that should be used by the user for generating the dependencies
     *  for a cross build artifact's pom file.
     *
     * @param sourceConfig A specific {@link SourceSet} configuration to use as source for dependencies
     * @param targetCompileScopeConfig Target pom aiding configuration
     * @param project Project space {@link Project}
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @param archiveAppendix {@link TargetVerItem} archiveAppendix to aid with the replacement of
     *                         non cross build {@link ProjectDependency} with its cross build counterpart
     */
    private static final createPomAidingConfiguration(
            sourceConfig,
            targetCompileScopeConfig,
            Project project,
            ScalaVersionInsights scalaVersionInsights,
            archiveAppendix) {
        def allDependencies = sourceConfig.allDependencies
        def dependencySets = findAllNonMatchingScalaVersionDependenciesWithCounterparts(
                allDependencies, scalaVersionInsights.artifactInlinedVersion)

        def moduleNames = findAllNamesForCrossBuildPluginAppliedProjects(project)
        def crossBuildProjectDependencySet = allDependencies.findAll {
            it instanceof ProjectDependency
        }.findAll {
            moduleNames.contains(it.name)
        }

        // Add internal dependencies(modules) as qualified dependencies
        def crossBuildProjectDependencySetTransformed = crossBuildProjectDependencySet.collect {
            project.dependencies.create(
                    group:it.group,
                    name:"${it.name}${qmarkReplace(archiveAppendix, scalaVersionInsights.artifactInlinedVersion)}",
                    version:it.version)
        }
        targetCompileScopeConfig.dependencies.addAll(crossBuildProjectDependencySetTransformed)

        // Add external cross built valid dependencies
        //  (pick latest version if the same dependency module appears multiple times with different versions)
        def crossBuildExternalDependencySet = dependencySets.collect { entry ->
            Set<Tuple> matchingDepTuples = entry[1]
            matchingDepTuples.toSorted {
                t1, t2 -> t1[2].version <=> t2[2].version
            }
        }.findAll {
            !it.isEmpty()
        }.collect {
            it.last()[2]
        }.toSet()
        targetCompileScopeConfig.dependencies.addAll(crossBuildExternalDependencySet)

        // Add external non crossed built dependencies
        def crossBuiltExternalDependencySet = dependencySets.collect { entry ->
            Set<Tuple> matchingDepTuples = entry[1]
            matchingDepTuples.collect { it[2] } }.flatten().toSet()
        def probablyCrossBuiltExternalDependencySet = dependencySets.collect { entry ->
            Tuple nonMatchingDepTuple = entry[0]
            nonMatchingDepTuple[2] }.toSet()
        def nonCrossBuildExternalDependencySet = allDependencies -
                        crossBuildProjectDependencySet -
                (crossBuiltExternalDependencySet + probablyCrossBuiltExternalDependencySet)
        def groupedByGroupAndName = nonCrossBuildExternalDependencySet.groupBy { dep -> dep.group + dep.name }
        def nonCrossBuildExternalDependencySetSanitized = groupedByGroupAndName.collect { entry ->
            if (entry.getValue().size() > 1) {
                entry.getValue().findAll { dep ->
                    !dep.version.contains('+')
                }.findAll { dep ->
                    if (dep.group.equals("org.scala-lang") && dep.name.equals("scala-library")) {
                        dep.version.startsWith(scalaVersionInsights.baseVersion)
                    } else {
                        true
                    }
                }.head()
            } else {
                entry.value.head()
            }
        }
        targetCompileScopeConfig.dependencies.addAll(nonCrossBuildExternalDependencySetSanitized)

        def resolvedCrossBuiltExternalDependencySet = dependencySets.collect { entry ->
            Tuple nonMatchingDepTuple = entry[0]
            nonMatchingDepTuple
        }.findAll {
            it[1] == '?'
        }.collect {
            Dependency dep = it[2]
            project.dependencies.create(
                    group:dep.group,
                    name:"${dep.name.replace('?', scalaVersionInsights.artifactInlinedVersion)}",
                    version:dep.version)
        }
        targetCompileScopeConfig.dependencies.addAll(resolvedCrossBuiltExternalDependencySet)
    }

    /**
     * Resolve dependencies with place holder version '?' for a crossbuild configuration and
     * try to convert mismatched scala version in dependencies coming from parent configuration to matching ones.
     *
     * @param project Project space {@link Project}
     * @param crossBuildConfigurationName A specific {@link SourceSet} configuration to use as source for dependencies
     * @param parentConfiguration A {@link Configuration} to link as extendedFrom
     * @param scalaVersionInsights Holds all version permutations for a specific Scala version
     * @param scalaVersionCatalog A Map of Scala versions to Scala compiler versions that serve as input for the plugin
     */
    private static
    final configureResolution(
            Project project,
            crossBuildConfigurationName,
            Configuration parentConfiguration,
            ScalaVersionInsights scalaVersionInsights,
            ScalaVersionCatalog scalaVersionCatalog) {
        def config = project.configurations[crossBuildConfigurationName]
        config.extendsFrom(parentConfiguration)
        def allDependencies = config.allDependencies
        project.logger.info(LoggerUtils.logTemplate(project,
                "Inherited dependendencies to consider while resolving ${crossBuildConfigurationName} " +
                        "configuration dependencies: " +
                        "[${allDependencies.collect { "${it.group}:${it.name}" }.join(', ')}]"
        ))

        project.configurations.all { c ->
            c.resolutionStrategy.eachDependency { details ->
                if (c.name.equals(crossBuildConfigurationName)) {
                    def requested = details.requested
                    // Replace 3d party scala dependency which ends with '_?' in cross build config scope
                    if (requested.name.endsWith("_?")) {
                        updateTargetName(details, scalaVersionInsights)
                        project.logger.info(LoggerUtils.logTemplate(project,
                                "${crossBuildConfigurationName} | " +
                                        "Found crossbuild glob '?' in dependency name ${requested.name}. " +
                                        "Subtituted with [${details.target.name}]"
                        ))
                        // Try updating target name version only in cross build config context.
                    } else {
                        def updated = tryUpdatingTargetNameVersion(details, allDependencies, scalaVersionInsights)
                        if (updated) {
                            project.logger.info(LoggerUtils.logTemplate(project,
                                    "${crossBuildConfigurationName} | Dependency Scan " +
                                            "| Replaced ${requested.name}:${requested.version} => " +
                                            "${details.target.name}:${details.target.version}"
                            ))
                        }
                    }
                    if (requested.group.equals("org.scala-lang") && requested.name.equals("scala-library")) {
                        details.useVersion(scalaVersionInsights.compilerVersion)
                    }
                } else if (c.name.equals(parentConfiguration.name)) {
                    def requested = details.requested
                    // Replace 3d party scala dependency which ends with '_?' in parent configuration scope
                    if (requested.name.endsWith("_?")) {
                        updateTargetName(details, parentConfiguration.allDependencies, scalaVersionCatalog)
                        project.logger.info(LoggerUtils.logTemplate(
                                project,
                                "${parentConfiguration.name} | Found crossbuild glob '?' in " +
                                        "dependency name ${requested.name}. Subtituted with [${details.target.name}]"
                        ))
                    }
                }
            }
        }
    }

    /**
     * Resolve dependencies with place holder scala version '?' for testCompile configuration.
     *
     * @param project Project space {@link Project}
     * @param scalaVersionInsights Holds all version permutations for a specific Scala version
     */
    private static
    final configureTestResolution(Project project, ScalaVersionCatalog scalaVersionCatalog) {

        project.configurations.all { c ->
            if (c.name.startsWith('test')) {
                c.resolutionStrategy.eachDependency { details ->
                    def requested = details.requested
                    // Replace 3d party scala dependency which ends with '_?'
                    if (requested.name.endsWith("_?")) {
                        updateTargetName(details, c.allDependencies, scalaVersionCatalog)
                        project.logger.info(LoggerUtils.logTemplate(
                                project,
                                "${c.name} | Found crossbuild glob '?' in dependency name ${requested.name}. " +
                                        "Subtituted with [${details.target.name}]"
                        ))
                    }
                }
            }
        }
    }

    /**
     * Resolve dependency names containing question mark to the actual scala version,
     * based on {@link ScalaVersionInsights} provided.
     *
     * @param details {@link DependencyResolveDetails} from resolution strategy
     * @param scalaVersionInsights Holds all version permutations for a specific Scala version
     */
    private static final updateTargetName(DependencyResolveDetails details, ScalaVersionInsights scalaVersionInsights) {
        def requested = details.requested
        def resolvedName = requested.name.replace("_?", "_${scalaVersionInsights.artifactInlinedVersion}")
        details.useTarget requested.group + ':' + resolvedName + ':' + requested.version
    }

    /**
     * Resolve dependency names containing question mark to the actual scala version,
     * based on provided {@link DependencySet} (of the configuration being handled) and {@link ScalaVersionCatalog}.
     * TODO: Base dependency resolution here on scala-library version instead.
     *
     * @param details {@link DependencyResolveDetails} from resolution strategy
     * @param dependencySet All dependencies of the specified configuration.
     * @param scalaVersionCatalog A set of Scala versions that serve as input for the plugin.
     */
    private static final updateTargetName(
            DependencyResolveDetails details,
            DependencySet dependencySet,
            ScalaVersionCatalog scalaVersionCatalog) {

        def probableScalaVersionRaw = scalaVersionCatalog.catalog.collect { it.key }.collect { String scalaVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(new DummyScalaVer(scalaVersion), scalaVersionCatalog)
            def deps = findAllNonMatchingScalaVersionDependenciesQMarksExcluded(
                    dependencySet, scalaVersionInsights.artifactInlinedVersion)
            new Tuple2(scalaVersionInsights.artifactInlinedVersion, deps.size())
        }.findAll { tuple ->
            tuple.second == 0
        }
        def requested = details.requested
        assertWithMsg("Could not infer scala version used in " +
                "configuration to be applied to dependency '$requested.group:$requested.name'") {
            assert probableScalaVersionRaw.size() == 1
        }
        def probableScalaVersion = probableScalaVersionRaw.head().first
        def resolvedName = requested.name.replace("_?", "_${probableScalaVersion}")
        details.useTarget requested.group + ':' + resolvedName + ':' + requested.version
    }

    /**
     * Tries to detect and substitute mismatched scala based dependencies.
     * This can happen when default configurations (compile, compileOnly ...) "pollute"
     * cross build configuration, which inherits from them,
     * with mismatched scala version dependencies.
     *
     * @param details {@link DependencyResolveDetails} from resolution strategy
     * @param dependencies All dependencies of the specified cross build configuration.
     * @param scalaVersionInsights Holds all version permutations for a specific Scala version
     * @return true when target was updated, false when not.
     */
    private static final tryUpdatingTargetNameVersion(
            DependencyResolveDetails details,
            DependencySet dependencies,
            ScalaVersionInsights scalaVersionInsights) {
        def requested = details.requested
        def underscoreIndex = requested.name.indexOf("_")
        if (underscoreIndex > 0) {
            def baseName = requested.name.substring(0, underscoreIndex)
            def matchingDeps = dependencies.findAll {
                it.group.equals(requested.group) && it.name.startsWith(baseName)
            }
            def supposedRequestedScalaVersion = requested.name.substring(underscoreIndex + 1)
            def sameVersionDeps = matchingDeps.findAll {
                it.name.endsWith("_$supposedRequestedScalaVersion")
            }
            if (matchingDeps.size() == 2 && sameVersionDeps.size() == 1 &&
                    !supposedRequestedScalaVersion.equals(scalaVersionInsights.artifactInlinedVersion)) {
                def correctDep = (matchingDeps - sameVersionDeps).first()
                details.useTarget correctDep.group + ':' + correctDep.name + ':' + correctDep.version
                return true
            }
            false
        }
    }

    /**
     * Parses given dependency name to its baseName part and its scala version part.
     * Throws Assertion Exception if dependency name does not contain separating char '_'
     *
     * @param depName dependency name to parse
     * @return tuple in the form of (baseName, scalaVersion) i.e. ('lib', '2.11')
     */
    private static final parseDependencyName(Dependency dep) {
        def index = dep.name.indexOf("_")
        assertWithMsg("Scala dependency naming convention expected") {
            assert index > 0
        }
        def baseName = dep.name.substring(0, index)
        def supposedScalaVersionRaw = dep.name.substring(index + 1)
        def innerIndex = supposedScalaVersionRaw.indexOf("_")
        def supposedScalaVersion = innerIndex > 0 ?
                supposedScalaVersionRaw.substring(0, innerIndex) : supposedScalaVersionRaw
        new Tuple2("${dep.group}:$baseName", supposedScalaVersion)
    }

    /**
     * Filters all dependencies that do not match given 'scalaVersion' from a set of dependencies.
     *
     * @param dependencySet set of dependencies in the form of {@link DependencySet} to scan
     * @param scalaVersion Scala Version to un-match against i.e '2.10', '2.11'
     * @return a list of tuples in the form of
     *          (baseName, scalaVersion, {@link org.gradle.api.artifacts.Dependency})
     *          i.e. ('lib', '2.11', ...)
     */
    private static final findAllNonMatchingScalaVersionDependencies(DependencySet dependencySet, scalaVersion) {
        def maybeNonMatchingDeps = dependencySet.findAll {
            it.name.length() - it.name.replaceAll("_", "").length() > 0
        }.collect {
            def (groupAndBaseName, supposedScalaVersion) = parseDependencyName(it)
            new Tuple(groupAndBaseName, supposedScalaVersion, it)
        }.findAll {
            it[1] != scalaVersion
        }
        maybeNonMatchingDeps
    }

    /**
     * Filters all dependencies that do not match given 'scalaVersion' from a set of dependencies.
     * Excluding dependencies with scala version placeholder '_?'
     *
     * @param dependencySet set of dependencies in the form of {@link DependencySet} to scan
     * @param scalaVersion Scala Version to un-match against i.e '2.10', '2.11'
     * @return a list of tuples in the form of
     *          (groupName:baseArchiveName, scalaVersion, {@link org.gradle.api.artifacts.Dependency})
     *          i.e. ('lib', '2.11', ...)
     */
    private static final findAllNonMatchingScalaVersionDependenciesQMarksExcluded(
            DependencySet dependencySet, scalaVersion) {
        findAllNonMatchingScalaVersionDependencies(dependencySet, scalaVersion).findAll { tuples ->
            tuples[1] != '?'
        }
    }

    /**
     * Filters all dependencies that do not match given 'scalaVersion' from a set of dependencies and then
     * enriches this list by converting each found dependency to a set containing itself and its counterparts.
     *
     * @param dependencySet set of dependencies in the form of {@link DependencySet} to scan
     * @param scalaVersion Scala Version to match against i.e '2.10', '2.11'
     * @return a list containing sets of tuples in the form of
     *          (groupName:baseArchiveName, scalaVersion, {@link org.gradle.api.artifacts.Dependency})
     *          i.e. [[('lib', '2.11', ...), [('lib', '2.10', ...), ('lib', '2.12', ...)]],
     *          [(...), [(...), (...)]], ...]
     */
    private static final findAllNonMatchingScalaVersionDependenciesWithCounterparts(
            DependencySet dependencySet, scalaVersion) {
        def maybeNonMatchingDeps = findAllNonMatchingScalaVersionDependencies(dependencySet, scalaVersion)
        def dependencySets = maybeNonMatchingDeps.collect { nonMatchingDepTuple ->
            def matchingDepTupleSet = dependencySet.findAll { dep ->
                dep.name.length() - dep.name.replaceAll("_", "").length() > 0
            }.collect { dep ->
                def (groupAndBaseName, supposedScalaVersion) = parseDependencyName(dep)
                new Tuple(groupAndBaseName, supposedScalaVersion, dep)
            }.findAll {
                it[0] == nonMatchingDepTuple[0] && it[1] == scalaVersion
            }.collect().toSet()
            [nonMatchingDepTuple, matchingDepTupleSet]
        }
        dependencySets
    }

    /**
     * Finds all the projects (modules) in a multi module project which has {@link CrossBuildPlugin} plugin applied
     *  and return their respective names.
     *
     * @param project project space {@link Project}
     * @return list of project(module) names for multi module project
     */
    private static final findAllNamesForCrossBuildPluginAppliedProjects(Project project) {
        def moduleNames = project.rootProject.allprojects.findAll {
            it.plugins.hasPlugin(CrossBuildPlugin)
        }.collect {
            it.name
        }
        moduleNames
    }
}
