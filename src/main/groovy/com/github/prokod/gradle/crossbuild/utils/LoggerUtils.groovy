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
package com.github.prokod.gradle.crossbuild.utils

import org.gradle.api.Project

/**
 * Logger utils
 */
class LoggerUtils {
    private static final int SUB_ORIENTATION_MAX_SIZE = 17

    static String logTemplate(Map conf, Project project) {
        def msg = conf.msg

        "${generateMessageOrientation(project, conf)} | ${msg}".toString()
    }

    private static String generateMessageOrientation(Project project, Map conf) {
        def lifecycle = conf.lifecycle
        def sourceset = conf.sourceset
        def configuration = conf.configuration
        def parentConfiguration = conf.parentConfiguration

        def prj = "PRJ=${project.path}"
        def lc = lifecycle != null ? "LC=$lifecycle" : 'LC=N/A'
        def sset = sourceset != null ? "SSET=$sourceset" : null
        def c = configuration != null ? "C=$configuration" : null
        def pc = parentConfiguration != null ? "PC=$parentConfiguration" : null

        [prj, lc, sset, c, pc]
                .findAll { it != null }
                .collect { it.take(SUB_ORIENTATION_MAX_SIZE).padRight(SUB_ORIENTATION_MAX_SIZE) }.join('/')
    }
}
