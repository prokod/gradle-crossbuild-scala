/*
 * Copyright 2016-2020 the original author or authors
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

import org.gradle.api.artifacts.ModuleVersionSelector

/**
 * Dependency resolution strategy handler. Holds dependency coordinated abstraction that supports both
 * <hl>
 *     <li>{@link ModuleVersionSelector}
 *     <li> Pom XMl Node
 * </hl>
 */
class ResolutionStrategyHandler {

    static Coordinates handleScalaModuleCase(Coordinates requested, ScalaVersionInsights scalaVersionInsights) {
        if (requested.group.id == 'org.scala-lang') {
            def targetModuleName = ScalaModuleType.convert(requested.name.id, scalaVersionInsights.majorVersion)
            return new Coordinates(requested.group.id, targetModuleName, scalaVersionInsights.compilerVersion)
        }
        requested
    }

    static Coordinates handle3rdPartyScalaLibCase(Coordinates requested,
                                                  String targetScalaVersion,
                                                  ScalaVersions scalaVersions) {
        def preRegex = scalaVersions.mkRefTargetVersions().collect { '_' + it }.join('|')
        def regex = "($preRegex)"
        new Coordinates(requested.group.id,
                requested.name.id.replaceFirst(regex, "_${targetScalaVersion}"),
                requested.version.id )
    }

    static class Coordinates {
        private final Tuple3<Coordinate, Coordinate, Coordinate> coordinates

        Coordinates(String group, String name, String version) {
            coordinates = new Tuple3(new Coordinate(group), new Coordinate(name), new Coordinate(version))
        }

        static Coordinates from(ModuleVersionSelector src) {
            new Coordinates(src.group, src.name, src.version)
        }

        static Coordinates fromXmlNodes(List<Node> src) {
            new Coordinates(src[0].text(), src[1].text(), src[2].text())
        }

        static Coordinates fromXmlArtifactIdNode(Node artifactId) {
            new Coordinates('', artifactId.text(), '')
        }

        Coordinate getGroup() {
            coordinates[0]
        }

        Coordinate getName() {
            coordinates[1]
        }

        Coordinate getVersion() {
            coordinates[2]
        }

        @Override
        String toString() {
            "$group.id:$name.id:$version.id".toString()
        }

        static class Coordinate {
            private final String id

            Coordinate(String id) {
                this.id = id
            }

            String getId() {
                id
            }
        }
    }
}
