/*
 * Copyright 2021-2022 the original author or authors
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
 * Event to create when {@code ext} is set/updated
 *
 * Used to communicate change in observable {@link ArchiveNaming} to observer
 * {@link com.github.prokod.gradle.crossbuild.CrossBuildExtension}
 */
class ExtUpdateEvent {
    final String name
    final Map<String, Object> source
    final EventType eventType

    ExtUpdateEvent(String name, Map<String, Object> source) {
        def clone = { Map<String, Object> orig ->
            def cloneObject = { Object origObj ->
                def bos = new ByteArrayOutputStream()
                def oos = new ObjectOutputStream(bos)
                oos.writeObject(origObj)
                oos.flush()
                def bin = new ByteArrayInputStream(bos.toByteArray())
                def ois = new ObjectInputStream(bin)
                ois.readObject()
            }

            orig.collect { new Tuple2<String, Object>(new String(it.key), cloneObject(it.value)) }.collectEntries()
        }

        this.name = name
        this.source = source == null ? [:] : clone(source)
        this.eventType = EventType.EXT_UPDATE
    }
}
