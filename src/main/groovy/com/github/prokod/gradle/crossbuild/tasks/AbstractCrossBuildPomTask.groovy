package com.github.prokod.gradle.crossbuild.tasks

import com.github.prokod.gradle.crossbuild.CrossBuildExtension
import com.github.prokod.gradle.crossbuild.model.ResolvedBuildAfterEvalLifeCycle
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSet

abstract class AbstractCrossBuildPomTask extends DefaultTask {
    ResolvedBuildAfterEvalLifeCycle resolvedBuild

    String mavenScopeConfigurationNameFor(ScopeType scopeType) {
        "${crossBuildSourceSet.name}${getMavenScopeSuffix(scopeType)}".toString()
    }

    protected SourceSet getCrossBuildSourceSet() {
        def extension = project.extensions.findByType(CrossBuildExtension)
        assert extension != null : "Cannot add task ${this.name} of type CrossBuildPomAidingConfigurationTask to " +
                "project ${project.name}. Reason: Tasks of that type can be added only to a cross build applied project"
        extension.crossBuildSourceSets.findByName(resolvedBuild.name).second
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