package com.github.prokod.gradle.crossbuild

import spock.lang.Specification


/**
 * Copyright (C) 13.06.20 - REstore NV
 */

class BuildResolverSpec extends Specification {
    def "generateCrossArchivesNameAppndix should generate the expected tag for the scala version"() {
        setup:
        BuildResolver resolver = new BuildResolver()
        when:
        
        expect:
        resolver.generateCrossArchivesNameAppndix("_?","2.12") == "_2.12"

        resolver.generateCrossArchivesNameAppndix("_?_extra","2.12") == "_2.12_extra"
    }
}
