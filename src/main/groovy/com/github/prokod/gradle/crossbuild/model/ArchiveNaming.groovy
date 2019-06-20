package com.github.prokod.gradle.crossbuild.model

import javax.inject.Inject

/**
 * cross build plugin DSL representation for {@code archive.appendixPattern}
 */
class ArchiveNaming {
    String appendixPattern

    @Inject
    ArchiveNaming(String appendixPattern) {
        this.appendixPattern = appendixPattern
    }
}
