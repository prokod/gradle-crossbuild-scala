package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.model.ResolvedBuildConfigLifecycle
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

/**
 * {@link SourceSetContainer} manipulation class to set and view CrossBuild SourceSets
 *
 */
class CrossBuildSourceSets {
    static final String SOURCESET_BASE_NAME = 'crossBuild'

    private final Project project
    final SourceSetContainer container

    CrossBuildSourceSets(Project project) {
        this.project = project
        this.container = getSourceSetContainer(project)
    }

    void fromDefault(ScalaVersions scalaVersions) {
        // Create default source sets early enough to be used in build.gradle dependencies block
        scalaVersions.catalog*.key.each { String scalaVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(scalaVersion, scalaVersions)

            def sourceSetId = getOrCreateCrossBuildScalaSourceSet(scalaVersionInsights).first

            project.logger.info(LoggerUtils.logTemplate(project,
                    lifecycle:'config',
                    msg:"Creating source set (Default): [${sourceSetId}]"))
        }
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
            project.logger.info(LoggerUtils.logTemplate(project,
                    lifecycle:'config',
                    msg:"Creating source set (User request): [${sourceSetId}]"))
            sourceSetId.toString()
        }
        sourceSetIds
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
        def sourceSets = project.hasProperty('sourceSets') ? (SourceSetContainer)project.sourceSets : null
        assert sourceSets != null : "Missing 'sourceSets' property under Project ${project.name} properties."
        sourceSets
    }
}
