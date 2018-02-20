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
package com.github.prokod.gradle.crossbuild.model

import com.github.prokod.gradle.crossbuild.ScalaVersions
import org.gradle.api.Project
import org.gradle.model.Managed
import org.gradle.model.ModelMap
import org.gradle.model.Unmanaged

/**
 * Managed top level object in model space for configuring the plugin.
 *
 * Example:
 * <code>
 * model {
 *     crossBuild {
 *         archivesBaseName = 'artifact'            // Defaults to project archivesBaseName
 *
 *         targetVersions {
 *             V211(ScalaVer) {
 *                 value = '2.11'                   // Defaults to 'V211' in this example, if not set at all
 *                 archiveAppendix = '_?_2.0.2'     // '?' will be replaced by value.
 *                                                  // Could be set explicitly also for simplicity.
 *             }
 *         }
 *     }
 * }
 * <code>
 */

@Managed
interface CrossBuild {
    ModelMap<TargetVerItem> getTargetVersions()

    void setArchivesBaseName( String archivesBaseName )
    String getArchivesBaseName()

    @Unmanaged
    ScalaVersions getScalaVersions()
    void setScalaVersions(ScalaVersions scalaVersions)

    @Unmanaged
    Project getProject()
    void setProject(Project project)
}
