package com.github.prokod.gradle.crossbuild.utils

class SourceSetInsight<R, S> {
    private final Tuple2<R, R> rawInsight
    private final Closure<Collection<S>> flatMapFunc

    SourceSetInsight(Tuple2<R, R> rawInsight, Closure<Collection<S>> flatMapFunc) {
        this.rawInsight = rawInsight
        this.flatMapFunc = flatMapFunc
    }

    SourceSetInsight(R rawInsight, Closure<Collection<S>> flatMapFunc) {
        this.rawInsight = new Tuple2(rawInsight, null)
        this.flatMapFunc = flatMapFunc
    }

    List<S> flatMapped() {
        rawInsight.findAll { it != null }.collectMany(flatMapFunc)
    }

    R getCrossBuild() {
        rawInsight.first
    }

    R getMain() {
        rawInsight.second
    }
}
