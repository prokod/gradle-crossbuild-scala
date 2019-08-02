package com.github.prokod.gradle.crossbuild.model

/**
 * Immutable representation of {@code archive} DSL block after resolving has been made
 *
 * @see com.github.prokod.gradle.crossbuild.BuildResolver
 */
class ResolvedArchiveNaming extends ArchiveNaming {
    final String appendix

    ResolvedArchiveNaming(String appendixPattern, String appendix) {
        super('ResolvedArchiveNaming', appendixPattern, null)
        this.appendix = appendix
    }
}
