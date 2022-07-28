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
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet

/**
 * Abstraction for CrossBuildPom related tasks
 */
@SuppressWarnings(['AbstractClassWithoutAbstractMethod'])
abstract class AbstractCrossBuildPomTask extends DefaultTask {
    @Internal
    ResolvedBuildAfterEvalLifeCycle resolvedBuild

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

    private static String getMavenScopeSuffix(ScopeType scopeType) {
        "Maven${scopeType.toString().toLowerCase().capitalize()}Scope"
    }

    static enum ScopeType {
        COMPILE,
        RUNTIME,
        PROVIDED
    }
}
