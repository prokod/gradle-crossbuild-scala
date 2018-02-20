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

import org.gradle.api.Named
import org.gradle.api.Task
import org.gradle.model.Managed
import org.gradle.model.Unmanaged

/**
 * Gradle Managed type used in plugin's DSL
 */
@Managed
interface TargetVerItem extends Named {
    void setValue(String value)
    String getValue()

    void setArchiveAppendix(String archiveAppendix)
    String getArchiveAppendix()

    void setArtifactId(String artifactId)
    String getArtifactId()

    @Unmanaged
    Task getTask()
    void setTask(Task task)
}
