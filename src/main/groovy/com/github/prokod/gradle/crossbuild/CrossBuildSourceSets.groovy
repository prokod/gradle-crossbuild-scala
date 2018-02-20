package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.model.CrossBuild
import com.github.prokod.gradle.crossbuild.model.TargetVerItem
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer

/**
 * {@link SourceSetContainer} manipulation class to set and view CrossBuild SourceSets
 *
 */
class CrossBuildSourceSets {
    static final String SOURCESET_BASE_NAME = 'crossBuild'

    private final Project project
    private final ScalaVersions scalaVersions
    private final Collection<TargetVerItem> targetVersionItems
    final SourceSetContainer container

    CrossBuildSourceSets(Project project, ScalaVersions scalaVersions, Collection<TargetVerItem> targetVersionItems) {
        this.project = project
        this.scalaVersions = scalaVersions
        this.targetVersionItems = targetVersionItems
        this.container = getSourceSetContainer(project)
    }

    CrossBuildSourceSets(Project project, ScalaVersions scalaVersions) {
        this(project, scalaVersions, null)
    }

    /**
     * Alt Constructor
     *
     * @param crossBuild Mapped top level object {@link com.github.prokod.gradle.crossbuild.model.CrossBuild}
     *                    in model space
     */
    CrossBuildSourceSets(CrossBuild crossBuild) {
        this(crossBuild.project, crossBuild.scalaVersions, crossBuild.targetVersions.values())
    }

    /**
     * Creates additional {@link org.gradle.api.tasks.SourceSet} per target version enlisted under
     *  mapped top level object crossBuild in model space.
     *
     * @param sourceSets Project source set container
     * @throws AssertionError if targetVersionItems is null
     */
    void reset() {
        assert targetVersionItems != null : 'targetVersionItems should not be null'
        def sourceSetIds = targetVersionItems.collect { targetVersion ->
            def scalaVersionInsights = new ScalaVersionInsights(targetVersion.value, scalaVersions)

            def sourceSetId = getOrCreateCrossBuildScalaSourceSet(scalaVersionInsights).first
            project.logger.info(LoggerUtils.logTemplate(project,
                    "Creating source set (Post Evaluate Lifecycle): [${sourceSetId}]"))
            sourceSetId.toString()
        }

        // Remove unused source sets
        cleanSourceSetsContainer(sourceSetIds)

        // disable unused tasks
        def nonActiveSourceSetIds = findNonActiveSourceSetIds(targetVersionItems*.value.toSet())
        project.logger.info(LoggerUtils.logTemplate(project,
                "Non active source set ids: [${nonActiveSourceSetIds.join(', ')}]"))
        cleanTasksContainer(project.tasks, nonActiveSourceSetIds)
    }

    /**
     * Find Scala source set id and instance in a source set container based on specific Scala version insights.
     *
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @param sourceSets Source set container (per project)
     * @return A tuple of source set id and its {@link SourceSet} instance
     */
    Tuple2<String, SourceSet> findByVersion(ScalaVersionInsights scalaVersionInsights) {
        def sourceSetId = generateSourceSetId(scalaVersionInsights)
        def sourceSet = container.findByName(sourceSetId)
        new Tuple2(sourceSetId, sourceSet)
    }

    private void cleanSourceSetsContainer(List<String> sourceSetIds) {
        // Remove unused source sets
        container.removeIf { it.name.contains(SOURCESET_BASE_NAME) && !sourceSetIds.contains(it.name) }
    }

    private static Set<String> findNonActiveSourceSetIds(Set<String> targetVersions) {
        // disable unused tasks
        def nonActiveTargetVersions = ScalaVersions.DEFAULT_SCALA_VERSIONS
                .mkRefTargetVersions()
        nonActiveTargetVersions.removeAll(targetVersions)

        def nonActiveSourceSetIds = nonActiveTargetVersions.collect {
            "${SOURCESET_BASE_NAME}${it.replaceAll('\\.', '')}".toString()
        }
        nonActiveSourceSetIds.toSet()
    }

    private static boolean cleanTasksContainer(TaskContainer tasks, Set<String> nonActiveSourceSetIds) {
        tasks.removeAll(tasks.findAll { t ->
            nonActiveSourceSetIds.findAll {
                ssid -> t.name.toLowerCase().contains(ssid.toLowerCase())
            }.size() > 0
        })
    }

    Tuple2<String, SourceSet> getOrCreateCrossBuildScalaSourceSet(ScalaVersionInsights scalaVersionInsights) {
        def sourceSetId = generateSourceSetId(scalaVersionInsights)

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
     * @param scalaVersionInsights An object that holds all version permutations for a specific Scala version
     * @return A tuple of source set id and its {@link org.gradle.api.tasks.SourceSet} instance
     */
    static String generateSourceSetId(ScalaVersionInsights scalaVersionInsights) {
        "$SOURCESET_BASE_NAME${scalaVersionInsights.strippedArtifactInlinedVersion}".toString()
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
