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
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import com.github.prokod.gradle.crossbuild.utils.DependencyInsights
import com.github.prokod.gradle.crossbuild.utils.SourceSetInsights
import com.github.prokod.gradle.crossbuild.utils.ViewType
import org.gradle.api.DefaultTask
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

    String mavenScopeConfigurationNameFor(ScopeType scopeType) {
        "${crossBuildSourceSet.name}${getMavenScopeSuffix(scopeType)}".toString()
    }

    @Internal
    protected SourceSet getCrossBuildSourceSet() {
        def extension = project.extensions.findByType(CrossBuildExtension)
        assert extension != null : "Cannot add task ${this.name} of type AbstractCrossBuildPomTask to " +
                "project ${project.name}. Reason: Tasks of that type can be added only to a cross build applied project"
        extension.crossBuildSourceSets.findByName(resolvedBuild.name).second
    }

    /**
     * This method actively sets {@link DefaultMavenPublication#alias } accordingly
     * Accordingly means:
     * Sets matching cross built publication to be alias = false
     * Sets NON matching cross built publication/s to be alias = true
     *
     * NOTE:
     * This is where the FIRST piece of "magic" happens and the following error is avoided
     * {@code Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates }
     * For more details see https://stackoverflow.com/questions/51247830/publishing-is-not-able-to-resolve-a-dependency-on-a-project-with-multiple-public
     *
     * @return
     */
    @Internal
    protected applyPublicationAliasStrategy() {
        def sourceSetInsights = new SourceSetInsights.Builder(resolvedBuild.name)
                .fromPrj(project)
                .build()
        def di = new DependencyInsights(sourceSetInsights)
        di.extractCrossBuildProjectTypeDependencies(ViewType.COMPILE_CLASSPATH).each {
            def publishingExtension = it.dependencyProject.extensions.findByType(PublishingExtension)

            if (publishingExtension != null) {
                def relatedPublications = publishingExtension.publications.withType(MavenPublication).findAll {
                    probablyRelatedPublication(it, crossBuildSourceSet.name)
                }

                if (relatedPublications.size() > 1) {
                    throw new StopExecutionException('Found more than one corresponding publish blocks ' +
                            "[${pubs*.name.join(', ')}] for ${crossBuildSourceSet.jarTaskName} task.")
                }

                def rp = (DefaultMavenPublication)relatedPublications.head()
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
    }

    private static String getMavenScopeSuffix(ScopeType scopeType) {
        "Maven${scopeType.toString().toLowerCase().capitalize()}Scope"
    }

    static enum ScopeType {
        COMPILE,
        RUNTIME,
        PROVIDED
    }
}
