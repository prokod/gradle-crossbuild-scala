package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.BuildResolver
import com.github.prokod.gradle.crossbuild.CrossBuildExtension
import com.github.prokod.gradle.crossbuild.ScalaVersionInsights
import com.github.prokod.gradle.crossbuild.ScalaVersions
import com.github.prokod.gradle.crossbuild.utils.LoggerUtils
import groovy.json.JsonOutput
import groovy.transform.AutoClone
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection

import javax.inject.Inject

/**
 * cross build plugin DSL representation for individual build items in {@code builds} block
 */
@AutoClone
class Build {
    final String name

    final CrossBuildExtension extension

    // Used as a dispatcher impl. for `onScalaVersionUpdate` events
    final DomainObjectCollection<ScalaVersionsUpdateEvent> eventDispatcher

    ArchiveNaming archive

    Set<String> scalaVersions

    @Inject
    Build(String name, CrossBuildExtension extension) {
        this.name = name
        this.extension = extension
        this.eventDispatcher = extension.project.container(ScalaVersionsUpdateEvent)
        trySettingScalaVersionImplicitlyFrom(name)
    }

    Build(Build other) {
        this.name = other.name
        this.extension = other.extension
        this.eventDispatcher = other.eventDispatcher
        this.archive = other.archive
        this.scalaVersions = other.scalaVersions.clone()
    }

    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Action<? super ArchiveNaming> action) {
        action.execute(archive)
    }

    /**
     * Needed even though it should be auto generated according to Gradle documentation
     * Probably it is not working per documentation because this DSL is nested.
     *
     * @param c
     */
    @SuppressWarnings(['ConfusingMethodName'])
    void archive(Closure c) {
        org.gradle.util.ConfigureUtil.configure(c, archive)
    }

    private void trySettingScalaVersionImplicitlyFrom(String name) {
        // TODO: Is catalog already resolved in this stage ? To add test!
        def catalog = extension.scalaVersionsCatalog
        def resolvedCatalog = ScalaVersions.withDefaultsAsFallback(catalog)
        def versionInsights = resolvedCatalog.catalog.collect { new ScalaVersionInsights(it.value) }
                        .find { vi -> name.endsWith("${vi.strippedArtifactInlinedVersion}") }
        if (versionInsights != null) {
            setScalaVersions([versionInsights.artifactInlinedVersion] as Set)
        }
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
     * Leaves behind some mess on JavaBasePlugin level ..
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
    private void fireEvent(ScalaVersionsUpdateEvent event) {
        def oldEvent = this.eventDispatcher.find { event.name == it.name }
        if (oldEvent != null) {
            if (this.scalaVersions != oldEvent.source.scalaVersions) {
                extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                        lifecycle:'config',
                        msg:"A previous source event already exists for build ${this.name}. " +
                                'Scala versions (Previous/Updated): ' +
                                "[${oldEvent.source.scalaVersions.join(', ')}]/[${this.scalaVersions.join(', ')}] " +
                                'Previously resolved builds will be dropped and updated ones created.'
                ))
                this.eventDispatcher.remove(oldEvent)
                def catalog = extension.scalaVersionsCatalog
                def resolvedCatalog = ScalaVersions.withDefaultsAsFallback(catalog)
                def resolvedBuildsReplay = BuildResolver.resolveDslBuild(oldEvent.source, resolvedCatalog)
                resolvedBuildsReplay.each { rb ->
                    // TODO: consider removal of previously created and obsolete sourceset
                    // TODO: for that, propagate crossBuildSourceSets from extension object

                    // Tasks creation in such case are prevented by removing the stale resolvedBuild instance.
                    extension.resolvedBuilds.removeIf { it.name == rb.name }
                }
            }
            else {
                extension.project.logger.info(LoggerUtils.logTemplate(extension.project,
                        lifecycle:'config',
                        msg:"A previous source event already exists for build ${this.name}. " +
                                'Scala versions (Previous/Updated): ' +
                                "[${oldEvent.source.scalaVersions.join(', ')}]/[${this.scalaVersions.join(', ')}] " +
                                'Resolved builds do not need any update.'
                ))
            }
        }
        this.eventDispatcher.add(event)
    }

    /**
     * onScalaVersionsUpdate handler
     *
     * @param callback Closure as onScalaVersionUpdate callback
     */
    void onScalaVersionsUpdate(Closure callback) {
        this.eventDispatcher.all(callback)
    }

    void setScalaVersions(Set<String> scalaVersions) {
        this.scalaVersions = scalaVersions
        fireEvent(new ScalaVersionsUpdateEvent(this))
    }

    String toString() {
        JsonOutput.toJson([name:name,
                           scalaVersions:scalaVersions,
                           archive:[appendixPattern:archive?.appendixPattern]])
    }
}
