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
package com.github.prokod.gradle.crossbuild

import com.github.prokod.gradle.crossbuild.model.ScalaVer

class ScalaVerImpl extends ScalaVer {
    String name
    String value

    ScalaVerImpl(String value) {
        this.name = value
        this.value = value
    }

    @Override
    void setValue(String value) {
        throw new UnsupportedOperationException("Immutable ScalaVer impl.")
    }

    @Override
    String getValue() {
        return value
    }

    @Override
    void setArchiveAppendix(String archiveAppendix) {
        throw new UnsupportedOperationException("Unsupported member archiveAppendix.")
    }

    @Override
    String getArchiveAppendix() {
        throw new UnsupportedOperationException("Unsupported member archiveAppendix.")
    }

    @Override
    String getName() {
        return name
    }
}
