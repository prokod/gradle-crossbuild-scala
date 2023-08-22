/*
 * Copyright 2019-2020 the original author or authors
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

import com.github.prokod.gradle.crossbuild.CrossBuildExtension
import com.github.prokod.gradle.crossbuild.ResolutionStrategyHandler
import com.github.prokod.gradle.crossbuild.ScalaVersions
import com.github.prokod.gradle.crossbuild.model.DependencyLimitedInsight
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsights
import com.github.prokod.gradle.crossbuild.utils.ViewType
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.StopExecutionException

/**
 * Abstraction for CrossBuildPom related tasks
 */
@SuppressWarnings(['AbstractClassWithoutAbstractMethod'])
abstract class AbstractCrossBuildPomTask extends DefaultTask {
    @Internal
    ResolvedBuildAfterEvalLifeCycle resolvedBuild

    @Internal
    ObjectFactory objectFactory

    @Internal
    protected SourceSet getCrossBuildSourceSet() {
        def extension = project.extensions.findByType(CrossBuildExtension)
        assert extension != null : "Cannot add task ${this.name} of type AbstractCrossBuildPomTask to " +
                "project ${project.name}. Reason: Tasks of that type can be added only to a cross build applied project"
        extension.crossBuildSourceSets.findByName(resolvedBuild.name).second
    }

    @SuppressWarnings(['LineLength'])
    /**
     * This method actively sets {@link DefaultMavenPublication#alias } accordingly
     * Accordingly means:
     * <ul>
     * <li>Sets matching cross built publication to be alias = false
     * <li>Sets NON matching cross built publication/s to be alias = true
     * </ul>
     *
     * NOTE:
     * <ul>
     * <li>This is where the FIRST piece of "magic" happens and the following error is avoided
     * {@code Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates }
     * <li>We are using here internal api which is not stable and can change without an alternative or head of time notice
     * </ul>
     * <ul>
     * <li>https://stackoverflow.com/questions/51247830/publishing-is-not-able-to-resolve-a-dependency-on-a-project-with-multiple-public
     * <li>https://github.com/gradle/gradle/issues/12324
     * <ul>
     * @return
     */
    protected void applyPublicationAliasStrategy() {
        def sourceSetInsights = new SourceSetInsights.Builder(resolvedBuild.name)
                .fromPrj(project)
                .build()
        def di = new DependencyInsights(sourceSetInsights)

        def prjToJarNameMap = di.extractCrossBuildProjectTypeDependencies(ViewType.COMPILE_CLASSPATH).collectEntries {
            setPublicationAlias(it.dependencyProject)
            Property jarBaseName = it.dependencyProject.tasks.findByName(crossBuildSourceSet.jarTaskName).archiveBaseName
            String projectName = it.dependencyProject.name
            new Tuple2<>(projectName, jarBaseName.get())
        }
        def (publishingExtension, relatedPublication) = getRelatedPublicationAndExtensionFor(project)
        relatedPublication.pom.withXml { withXmlHandler(it, prjToJarNameMap) }
    }

    void setPublicationAlias(Project project) {
        def (publishingExtension, rp) = getRelatedPublicationAndExtensionFor(project)

        if (publishingExtension != null) {
            rp.alias = false

            def nonRelatedPublications = publishingExtension.publications.withType(MavenPublication).findAll {
                !probablyRelatedPublication(it, crossBuildSourceSet.name)
            }

            nonRelatedPublications.each {
                def nrp = (DefaultMavenPublication)it
                nrp.alias = true
            }
        }
    }

    Tuple2<PublishingExtension, DefaultMavenPublication> getRelatedPublicationAndExtensionFor(Project project) {
        def publishingExtension = project.extensions.findByType(PublishingExtension)

        if (publishingExtension != null) {
            def relatedPublications = publishingExtension.publications.withType(MavenPublication).findAll {
                probablyRelatedPublication(it, crossBuildSourceSet.name)
            }

            if (relatedPublications.size() > 1) {
                throw new StopExecutionException('Found more than one corresponding publish blocks ' +
                        "[${pubs*.name.join(', ')}] for ${crossBuildSourceSet.jarTaskName} task.")
            }

            return new Tuple2(publishingExtension, (DefaultMavenPublication) relatedPublications.head())
        }
        null
    }

    @Internal
    protected static boolean probablyRelatedPublication(MavenPublication pub, String sourceSetId) {
        pub.name.contains(sourceSetId)
    }

    @SuppressWarnings(['LineLength'])
    /**
     * This method takes care of modifying cross build pom file dependencies artifactId
     * Current Gradle maven-publish plugin (up to 8.x) is very limited when it comes to generating dependency tree for
     * pom xml dependencies section. It only resolves dependency coordinates based on default configuration.
     * It means that any information stored in cross build jars is ignores like for instance:
     * <ul>
     * <li>The maven-publish plugin does not take care of artifactId for local built crossbuild dependencies.
     * <li>The maven-publish plugin is not aware of 3rd party cross build dependencies and so it only "cares" for the
     * default crossbuild variant.
     * </ul>
     * <p>
     * An example to illustrate this better:
     * <p>
     * Given a multi module project with :lib2 and :lib where :lib2 depends on :lib
     * One would expect that if the artifact jar name for the publication of :lib2 is lib2_2.12
     * and according to dependency graph for compile classpath the local dependency :lib has jar name of lib_2.12,
     * then pom xml for lib2 should have lib dependency artifactId as lib_2.12.
     * Well not, it will be just lib as it defaults to project name
     * <p>
     * How this method works ?
     * <ul>
     * <li>It scans the pom xml dependencies for local dependencies and according to {@code prjToJarNamesMap} which holds
     * project name as key and jarBaseName for the relevant crossbuild variant as value, it updates them.
     * <li> It also scans the pom xml dependencies for 3rd party lib dependencies, checks their scala version and
     * accordingly modifies to match the current crossbuild module for the local project
     * </ul>
     * <p>
     * NOTE:
     * <ul>
     * <li>We could not use here QName as we try to maintain compatibility with multiple Gradle major versions. Gradle 7 switch to Groovy 3
     * and this conflicts with our compile time Groovy which is still 2.x as we use Gradle 6
     * </ul>
     *
     * https://github.com/gradle/gradle/issues/11299
     *
     */
    @Internal
    Closure<Void> withXmlHandler = {
        XmlProvider xmlProvider, Map<String, String> prjToJarNamesMap ->
            Node dependenciesNode = xmlProvider.asNode()['dependencies']?.getAt(0)
            if (dependenciesNode != null) {
                dependenciesNode.children().collect { Node dependency -> dependency.children() }
                        .each { List<Node> coordinates ->
                            def newValue = prjToJarNamesMap.get(coordinates[1].text())
                            // Replacing offending local dependency
                            if (newValue != null) {
                                coordinates[1].setValue(newValue)
                            }
                            // Replacing Scala module name/version
                            if (coordinates[0].text() == 'org.scala-lang') {
                                def newCoordinates =
                                        ResolutionStrategyHandler.handleScalaModuleCase(
                                                ResolutionStrategyHandler.Coordinates.fromXmlNodes(coordinates),
                                                resolvedBuild.scalaVersionInsights)
                                coordinates[1].setValue(newCoordinates.name.id)
                                coordinates[2].setValue(newCoordinates.version.id)
                            }
                            // Replace offending default configuration 3rd party scala lib
                            def extension = project.extensions.findByType(CrossBuildExtension)
                            def scalaVersions = ScalaVersions.withDefaultsAsFallback(extension.scalaVersionsCatalog)
                            def dependencyInsight =
                                    DependencyLimitedInsight.parseByDependencyName(coordinates[1].text(), scalaVersions)
                            def targetScalaVersion = resolvedBuild.scalaVersionInsights.artifactInlinedVersion
                            if (dependencyInsight.supposedScalaVersion != targetScalaVersion) {
                                def newCoordinates =
                                        ResolutionStrategyHandler.handle3rdPartyScalaLibCase(
                                                ResolutionStrategyHandler.Coordinates.fromXmlNodes(coordinates),
                                                targetScalaVersion, scalaVersions)

                                coordinates[1].setValue(newCoordinates.name.id)
                            }
                        }
            }
    }
}
