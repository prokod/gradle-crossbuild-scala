package com.github.prokod.gradle.crossbuild.model

/**
 * Immutable representation of {@code targetCompatibility} DSL block after resolving has been made
 *
 * @see com.github.prokod.gradle.crossbuild.BuildResolver
 */
class ResolvedTargetCompatibility extends TargetCompatibility {
    final ScalaCompilerTargetStrategyType inferredStrategy

    ResolvedTargetCompatibility(String strategy) {
        super('ResolvedTargetCompatibility', strategy, null)
        this.inferredStrategy = ScalaCompilerTargetStrategyType.valueOf(this.strategy.toUpperCase())
    }
}
