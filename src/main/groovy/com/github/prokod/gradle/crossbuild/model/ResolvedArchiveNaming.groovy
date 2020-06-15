package com.github.prokod.gradle.crossbuild.model

/**
 * Immutable representation of {@code archive} DSL block after resolving has been made
 *
 * @see com.github.prokod.gradle.crossbuild.BuildResolver
 */
class ResolvedArchiveNaming extends ArchiveNaming {
    final String appendix
    final String scalaTag

    ResolvedArchiveNaming(String appendixPattern, String appendix, String scalaTag) {
        super('ResolvedArchiveNaming', appendixPattern,scalaTag, null)
        this.appendix = appendix
        this.scalaTag = scalaTag
    }
}
