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

import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction
import com.github.prokod.gradle.crossbuild.CrossBuildExtension

/**
 * Custom gradle task for cross building related Pom files
 */
class CrossBuildPomTask extends AbstractCrossBuildPomTask {

    @TaskAction
    void update() {
        def crossBuildSourceSet = getCrossBuildSourceSet()
        updateCrossBuildPublications(crossBuildSourceSet)
    }

    private void updateCrossBuildPublications(SourceSet crossBuildSourceSet) {
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

        // Publishing POM - Set non matching cross built pub to be alias = true
        // This is where the FIRST piece of "magic" happens and the following error is avoided
        // Publishing is not able to resolve a dependency on a project with multiple publications that have
        // different coordinates
        applyPublicationAliasStrategy()

        // Publishing POM - overlay dependency resolution
        // This is where the SECOND piece of "magic" happens and the correct dependencies resolved from
        // compileClasspath configuration
        // are overlaid on top of:
        // - Compile scope apiElements dependencies
        // - Runtime scope runtimeElements dependencies

        pub.versionMapping {
            it.variant(CrossBuildExtension.SCALA_USAGE_ATTRIBUTE,
                    objectFactory.named(Usage, "scala-${resolvedBuild.scalaVersion}-jar")) {
                it.fromResolutionOf(crossBuildSourceSet.compileClasspathConfigurationName)
            }
        }
    }

    static boolean probablyRelatedPublicationTask(String name, String sourceSetId) {
        name.contains(sourceSetId.capitalize())
    }
}
