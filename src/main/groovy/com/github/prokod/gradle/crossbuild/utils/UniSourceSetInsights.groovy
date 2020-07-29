package com.github.prokod.gradle.crossbuild.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.tasks.SourceSet

/**
 * Used within {@link DependencyInsights} mainly
 *
 * Semantically binding main source set and cross build source set over the same configuration type see
 * {@link ViewType}
 *
 * For each such pair the following are available respectively: Configuration names, Configurations and dependency sets
 *
 */
class UniSourceSetInsights {
    final SourceSet sourceSet
    final Project project

    UniSourceSetInsights(SourceSet sourceSet, Project project) {
        this.sourceSet = sourceSet
        this.project = project
    }

    String getNameFor(ViewType configurationType) {
        switch (configurationType) {
            case ViewType.COMPILE:
                return getCompileName()
            case ViewType.COMPILE_ONLY:
                return getCompileOnlyName()
            case ViewType.COMPILE_CLASSPATH:
                return getCompileClasspathName()
            case ViewType.IMPLEMENTATION:
                return getImplementationName()
            case ViewType.RUNTIME:
                return getRuntimeName()
            case ViewType.RUNTIME_ONLY:
                return getRuntimeOnlyName()
            case ViewType.RUNTIME_CLASSPATH:
                return getRuntimeClasspathName()
            default:
                return getConfigurationNameUsing { SourceSet srcSet ->
                    defaultName(srcSet, configurationType)
                }
        }
    }

    Configuration getConfigurationFor(ViewType configurationType) {
        switch (configurationType) {
            case ViewType.COMPILE:
                return getCompileConfiguration()
            case ViewType.COMPILE_ONLY:
                return getCompileOnlyConfiguration()
            case ViewType.COMPILE_CLASSPATH:
                return getCompileClasspathConfiguration()
            case ViewType.IMPLEMENTATION:
                return getImplementationConfiguration()
            case ViewType.RUNTIME:
                return getRuntimeConfiguration()
            case ViewType.RUNTIME_ONLY:
                return getRuntimeOnlyConfiguration()
            case ViewType.RUNTIME_CLASSPATH:
                return getRuntimeClasspathConfiguration()
            default:
                return getConfigurationUsing { it ->
                    getConfigurationNameUsing { SourceSet srcSet ->
                        defaultName(srcSet, configurationType)
                    }
                }
        }
    }

    Collection<Configuration> getUserFacingConfigurations() {
        ViewType.getUserFacingViews().collect { viewType ->
            getConfigurationFor(viewType)
        }
    }

    DependencySet getDependencySetFor(ViewType configurationType, DependencySetType dependencySetType) {
        switch (configurationType) {
            case ViewType.COMPILE:
                return getCompileDependencySet(dependencySetType)
            case ViewType.COMPILE_ONLY:
                return getCompileOnlyDependencySet(dependencySetType)
            case ViewType.COMPILE_CLASSPATH:
                return getCompileClasspathDependencySet(dependencySetType)
            case ViewType.IMPLEMENTATION:
                return getImplementationDependencySet(dependencySetType)
            case ViewType.RUNTIME:
                return getRuntimeDependencySet(dependencySetType)
            case ViewType.RUNTIME_ONLY:
                return getRuntimeOnlyDependencySet(dependencySetType)
            case ViewType.RUNTIME_CLASSPATH:
                return getRuntimeClasspathDependencySet(dependencySetType)
            default:
                return getDependencySetUsing(dependencySetType) { it0 ->
                    getConfigurationUsing { it1 ->
                        getConfigurationNameUsing { SourceSet srcSet ->
                            defaultName(srcSet, configurationType)
                        }
                    }
                }
        }
    }

    /**
     * In case the configuration is not supported fully by this class, use this default naming scheme
     *
     * @param sourceSet
     * @param configurationType
     * @return default configuration name
     */
    //TODO right now used for test configurations. This is a bit of a hack as test is by its own a different source set
    private static String defaultName(SourceSet sourceSet, ViewType configurationType) {
        if (sourceSet.name == 'main') {
            configurationType.name
        }
        else {
            sourceSet.name + configurationType.name.capitalize()
        }
    }

    String getCompileName() {
        getConfigurationNameUsing { SourceSet srcSet -> srcSet.getCompileConfigurationName() }
    }

    Configuration getCompileConfiguration() {
        getConfigurationUsing { it -> getCompileName() }
    }

    DependencySet getCompileDependencySet(DependencySetType dependencySetType) {
        getDependencySetUsing(dependencySetType) { it -> getCompileConfiguration() }
    }

    String getCompileOnlyName() {
        getConfigurationNameUsing { SourceSet srcSet -> srcSet.getCompileOnlyConfigurationName() }
    }

    Configuration getCompileOnlyConfiguration() {
        getConfigurationUsing { it -> getCompileOnlyName() }
    }

    DependencySet getCompileOnlyDependencySet(DependencySetType dependencySetType) {
        getDependencySetUsing(dependencySetType) { it -> getCompileOnlyConfiguration() }
    }

    String getCompileClasspathName() {
        getConfigurationNameUsing { SourceSet srcSet -> srcSet.getCompileClasspathConfigurationName() }
    }

    Configuration getCompileClasspathConfiguration() {
        getConfigurationUsing { it -> getCompileClasspathName() }
    }

    DependencySet getCompileClasspathDependencySet(DependencySetType dependencySetType) {
        getDependencySetUsing(dependencySetType) { it -> getCompileClasspathConfiguration() }
    }

    String getImplementationName() {
        getConfigurationNameUsing { SourceSet srcSet -> srcSet.getImplementationConfigurationName() }
    }

    Configuration getImplementationConfiguration() {
        getConfigurationUsing { it -> getImplementationName() }
    }

    DependencySet getImplementationDependencySet(DependencySetType dependencySetType) {
        getDependencySetUsing(dependencySetType) { it -> getImplementationConfiguration() }
    }

    String getRuntimeName() {
        getConfigurationNameUsing { SourceSet srcSet -> srcSet.getRuntimeConfigurationName() }
    }

    Configuration getRuntimeConfiguration() {
        getConfigurationUsing { it -> getRuntimeName() }
    }

    DependencySet getRuntimeDependencySet(DependencySetType dependencySetType) {
        getDependencySetUsing(dependencySetType) { it -> getRuntimeConfiguration() }
    }

    String getRuntimeOnlyName() {
        getConfigurationNameUsing { SourceSet srcSet -> srcSet.getRuntimeOnlyConfigurationName() }
    }

    Configuration getRuntimeOnlyConfiguration() {
        getConfigurationUsing { it -> getRuntimeOnlyName() }
    }

    DependencySet getRuntimeOnlyDependencySet(DependencySetType dependencySetType) {
        getDependencySetUsing(dependencySetType) { it -> getRuntimeOnlyConfiguration() }
    }

    String getRuntimeClasspathName() {
        getConfigurationNameUsing { SourceSet srcSet -> srcSet.getRuntimeClasspathConfigurationName() }
    }

    Configuration getRuntimeClasspathConfiguration() {
        getConfigurationUsing { it -> getRuntimeClasspathName() }
    }

    DependencySet getRuntimeClasspathDependencySet(DependencySetType dependencySetType) {
        getDependencySetUsing(dependencySetType) { it -> getRuntimeClasspathConfiguration() }
    }

    private String getConfigurationNameUsing(Closure<String> configurationNameFunc) {
        configurationNameFunc(sourceSet)
    }

    /**
     *
     * @param configurationNamesFunc
     * @return May return a tuple with first element null!
     */
    private Configuration getConfigurationUsing(Closure<String> configurationNameFunc) {
        def name = configurationNameFunc()
        def configuration = project.configurations.findByName(name)
        configuration
    }

    /**
     *
     * @param dependencySetType
     * @param configurationsFunc
     * @return May return a tuple with first element null!
     */
    private DependencySet getDependencySetUsing(DependencySetType dependencySetType,
                                                                        Closure<Configuration> configurationFunc) {
        def dependencySetFunc = { Configuration configuration, DependencySetType dst ->
            switch (dst) {
                case DependencySetType.ALL: return configuration?.allDependencies
                case DependencySetType.SINGLE: return configuration?.dependencies
            }
        }
        def dependencySet = dependencySetFunc(configurationFunc(), dependencySetType)
        dependencySet
    }
}
