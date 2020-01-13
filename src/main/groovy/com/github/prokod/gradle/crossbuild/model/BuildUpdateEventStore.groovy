/*
 * Copyright 2018-2019 the original author or authors
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
package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.BuildResolver
import com.github.prokod.gradle.crossbuild.ScalaVersions
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project

/**
 * A hybrid event sourcing - event listener pattern for {@link Build} instance as Observable
 * The callback capabilities achieved through Gradle's {@link DomainObjectCollection}
 */
class BuildUpdateEventStore {
    private final DomainObjectCollection<BuildUpdateEvent> store

    BuildUpdateEventStore(Project project) {
        this.store = project.container(BuildUpdateEvent)
    }

    /**
     * Fire an onScalaVersionUpdate event
     *
     * There is an evidence that applying removal of sourceSet and corresponding tasks in DSL cases like the following:
     * <pre>
     *     crossBuild {
     *         scalaVersionsCatalog = ['2.13':'2.13.0']
     *         builds {
     *             v213 {
     *                 scalaVersions = ['2.13']
     *             }
     *         }
     *     }
     * </pre>
     *
     * leaves behind some mess on JavaBasePlugin level ..
     *
     * <pre>
     *     * Exception is:
     *     org.gradle.api.ProjectConfigurationException: A problem occurred configuring root project
     *     'junit6363768996989815245'.
     *     Caused by: org.gradle.model.internal.core.ModelRuleExecutionException: Exception thrown while executing model
     *     rule: JavaBasePlugin.Rules#attachBridgedBinaries(BinaryContainer, JavaBasePlugin.BridgedBinaries)
     *     Caused by: org.gradle.model.internal.core.DuplicateModelException: Cannot create 'binaries.crossBuildV213'
     *     using creation rule
     *     'JavaBasePlugin.Rules#attachBridgedBinaries(BinaryContainer, JavaBasePlugin.BridgedBinaries) > put()'
     *     as the rule
     *     'JavaBasePlugin.Rules#attachBridgedBinaries(BinaryContainer, JavaBasePlugin.BridgedBinaries) > put()'
     *     is already registered to create this model element.
     * </pre>
     *
     * TODO: Try to research that.
     *
     * For now no removal of such kind is done. Code checks for such case and recreation of sourceset and tasks are
     * being prevented
     *
     * @param event This instance as event that holds current event source
     */
    @SuppressWarnings(['ConfusingMethodName'])
    void store(BuildUpdateEvent event) {
        def currBuild = event.source
        def extension = currBuild.extension
        def project = extension.project
        def prevEvent = this.store.find { event.name == it.name }
        if (prevEvent != null) {
            def prevBuild = prevEvent.source
            if (currBuild.scalaVersions != prevBuild.scalaVersions) {
                project.logger.info(LoggerUtils.logTemplate(project,
                        lifecycle:'config',
                        msg:"A previous source event already exists for build ${currBuild.name}. " +
                                'Scala versions (Previous/Updated): ' +
                                "[${prevBuild.scalaVersions.join(', ')}]/[${currBuild.scalaVersions.join(', ')}] " +
                                'Previously resolved builds will be dropped and updated ones created.'
                ))
                this.store.remove(prevEvent)
                def catalog = extension.scalaVersionsCatalog
                def resolvedCatalog = ScalaVersions.withDefaultsAsFallback(catalog)
                def resolvedBuildsReplay = BuildResolver.resolveDslBuild(Build.from(prevBuild), resolvedCatalog)
                resolvedBuildsReplay.each { rb ->
                    // Tasks creation in such case are prevented by removing the stale resolvedBuild instance.
                    extension.resolvedBuilds.removeIf { it.name == rb.name }
                }
            } else if (currBuild.archive.appendixPattern != prevEvent.source.archive.appendixPattern) {
                this.store.remove(prevEvent)
                def catalog = extension.scalaVersionsCatalog
                def resolvedCatalog = ScalaVersions.withDefaultsAsFallback(catalog)
                def resolvedBuildsReplay = BuildResolver.resolveDslBuild(Build.from(prevBuild), resolvedCatalog)
                resolvedBuildsReplay.each { rb ->
                    // Tasks creation in such case are prevented by removing the stale resolvedBuild instance.
                    extension.resolvedBuilds.removeIf { it.name == rb.name }
                }
            } else {
                project.logger.info(LoggerUtils.logTemplate(project,
                        lifecycle:'config',
                        msg:"A previous source event already exists for build ${currBuild.name}. " +
                                'Scala versions (Previous/Updated): ' +
                                "[${prevBuild.scalaVersions.join(', ')}]/[${currBuild.scalaVersions.join(', ')}] " +
                                'Resolved builds do not need any update.'
                ))
            }
        }
        this.store.add(event)
    }

    /**
     * Storing {@link ArchiveNamingUpdateEvent} only if store contains previously stored {@link BuildUpdateEvent}
     * This way, triggering callback for {@link ArchiveNaming} appendixPattern change for a specific {@link Build}
     * is done only on top of already triggered callback for the same {@link Build} item cause by scalaVersions
     * update/set event
     *
     * @param event
     */
    @SuppressWarnings(['ConfusingMethodName'])
    void store(ArchiveNamingUpdateEvent event) {
        def eventNameToLookFor = event.source.name + '_' + EventType.SCALA_VERSIONS_UPDATE
        def prevEvent = this.store.find { eventNameToLookFor == it.name }
        if (prevEvent != null) {
            def prevBuild = prevEvent.source
            def newBuildSnapshot = new BuildSnapshot(prevBuild, event.source)
            store(new BuildUpdateEvent(Build.from(newBuildSnapshot), event.eventType))
        }
    }

    /**
     * onBuildUpdate handler
     *
     * @param callback Closure as onBuildUpdate callback
     */
    void onEvent(Closure callback) {
        this.store.all(callback)
    }
}
