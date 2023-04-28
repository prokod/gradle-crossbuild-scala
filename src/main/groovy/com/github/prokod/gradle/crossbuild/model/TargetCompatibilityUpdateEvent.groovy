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

/**
 * Event to create when {@link ArchiveNaming} appendixPattern is set/updated
 *
 * Used to communicate change in observable {@link ArchiveNaming} to observer
 * {@link com.github.prokod.gradle.crossbuild.CrossBuildExtension}
 */
class TargetCompatibilityUpdateEvent {
    final TargetCompatibilitySnapshot source
    final EventType eventType

    TargetCompatibilityUpdateEvent(TargetCompatibility source) {
        this.source = TargetCompatibilitySnapshot.from(source)
        this.eventType = EventType.TARGET_COMPATIBILITY_STRATEGY_UPDATE
    }
}
