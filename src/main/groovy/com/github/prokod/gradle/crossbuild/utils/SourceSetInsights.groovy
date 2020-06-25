package com.github.prokod.gradle.crossbuild.utils

import com.github.prokod.gradle.crossbuild.utils.SourceSetInsightsView.DependencySetType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet

/**
 * Used within {@link DependencyInsights} mainly
 *
 * Semantically binding main source set and cross build source set over the same configuration type see
 * {@link SourceSetInsights.ViewType}
 *
 * For each such pair the following are available respectively: Configuration names, Configurations and dependency sets
 *
 */
class SourceSetInsights {
    final SourceSet crossBuild
    final SourceSet main
    final Project project

    SourceSetInsights(SourceSet crossBuild, SourceSet main, Project project) {
        this.crossBuild = crossBuild
        this.main = main
        this.project = project
    }

    Tuple2<String, String> getNamesFor(ViewType configurationType) {
        switch (configurationType) {
            case ViewType.COMPILE:
                return getCompileNames()
            case ViewType.COMPILE_ONLY:
                return getCompileOnlyNames()
            case ViewType.COMPILE_CLASSPATH:
                return getCompileClasspathNames()
            case ViewType.IMPLEMENTATION:
                return getImplementationNames()
            case ViewType.RUNTIME:
                return getRuntimeNames()
            case ViewType.RUNTIME_ONLY:
                return getRuntimeOnlyNames()
            case ViewType.RUNTIME_CLASSPATH:
                return getRuntimeClasspathNames()
            default:
                return getConfigurationNamesUsing { SourceSet srcSet ->
                    defaultName(srcSet, configurationType)
                }
        }
    }

    Tuple2<Configuration, Configuration> getConfigurationsFor(ViewType configurationType) {
        switch (configurationType) {
            case ViewType.COMPILE:
                return getCompileConfigurations()
            case ViewType.COMPILE_ONLY:
                return getCompileOnlyConfigurations()
            case ViewType.COMPILE_CLASSPATH:
                return getCompileClasspathConfigurations()
            case ViewType.IMPLEMENTATION:
                return getImplementationConfigurations()
            case ViewType.RUNTIME:
                return getRuntimeConfigurations()
            case ViewType.RUNTIME_ONLY:
                return getRuntimeOnlyConfigurations()
            case ViewType.RUNTIME_CLASSPATH:
                return getRuntimeClasspathConfigurations()
            default:
                return getConfigurationsUsing { it ->
                    getConfigurationNamesUsing { SourceSet srcSet ->
                        defaultName(srcSet, configurationType)
                    }
                }
        }
    }

    Tuple2<DependencySet, DependencySet> getDependencySetsFor(ViewType configurationType,
                                                              DependencySetType dependencySetType) {
        switch (configurationType) {
            case ViewType.COMPILE:
                return getCompileDependencySets(dependencySetType)
            case ViewType.COMPILE_ONLY:
                return getCompileOnlyDependencySets(dependencySetType)
            case ViewType.COMPILE_CLASSPATH:
                return getCompileClasspathDependencySets(dependencySetType)
            case ViewType.IMPLEMENTATION:
                return getImplementationDependencySets(dependencySetType)
            case ViewType.RUNTIME:
                return getRuntimeDependencySets(dependencySetType)
            case ViewType.RUNTIME_ONLY:
                return getRuntimeOnlyDependencySets(dependencySetType)
            case ViewType.RUNTIME_CLASSPATH:
                return getRuntimeClasspathDependencySets(dependencySetType)
            default:
                return getDependencySetsUsing(dependencySetType) { it0 ->
                    getConfigurationsUsing { it1 ->
                        getConfigurationNamesUsing { SourceSet srcSet ->
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

    Tuple2<String, String> getCompileNames() {
        getConfigurationNamesUsing { SourceSet srcSet -> srcSet.getCompileConfigurationName() }
    }

    Tuple2<Configuration, Configuration> getCompileConfigurations() {
        getConfigurationsUsing { it -> getCompileNames() }
    }

    Tuple2<DependencySet, DependencySet> getCompileDependencySets(DependencySetType dependencySetType) {
        getDependencySetsUsing(dependencySetType) { it -> getCompileConfigurations() }
    }

    Tuple2<String, String> getCompileOnlyNames() {
        getConfigurationNamesUsing { SourceSet srcSet -> srcSet.getCompileOnlyConfigurationName() }
    }

    Tuple2<Configuration, Configuration> getCompileOnlyConfigurations() {
        getConfigurationsUsing { it -> getCompileOnlyNames() }
    }

    Tuple2<DependencySet, DependencySet> getCompileOnlyDependencySets(DependencySetType dependencySetType) {
        getDependencySetsUsing(dependencySetType) { it -> getCompileOnlyConfigurations() }
    }

    Tuple2<String, String> getCompileClasspathNames() {
        getConfigurationNamesUsing { SourceSet srcSet -> srcSet.getCompileClasspathConfigurationName() }
    }

    Tuple2<Configuration, Configuration> getCompileClasspathConfigurations() {
        getConfigurationsUsing { it -> getCompileClasspathNames() }
    }

    Tuple2<DependencySet, DependencySet> getCompileClasspathDependencySets(DependencySetType dependencySetType) {
        getDependencySetsUsing(dependencySetType) { it -> getCompileClasspathConfigurations() }
    }

    Tuple2<String, String> getImplementationNames() {
        getConfigurationNamesUsing { SourceSet srcSet -> srcSet.getImplementationConfigurationName() }
    }

    Tuple2<Configuration, Configuration> getImplementationConfigurations() {
        getConfigurationsUsing { it -> getImplementationNames() }
    }

    Tuple2<DependencySet, DependencySet> getImplementationDependencySets(DependencySetType dependencySetType) {
        getDependencySetsUsing(dependencySetType) { it -> getImplementationConfigurations() }
    }

    Tuple2<String, String> getRuntimeNames() {
        getConfigurationNamesUsing { SourceSet srcSet -> srcSet.getRuntimeConfigurationName() }
    }

    Tuple2<Configuration, Configuration> getRuntimeConfigurations() {
        getConfigurationsUsing { it -> getRuntimeNames() }
    }

    Tuple2<DependencySet, DependencySet> getRuntimeDependencySets(DependencySetType dependencySetType) {
        getDependencySetsUsing(dependencySetType) { it -> getRuntimeConfigurations() }
    }

    Tuple2<String, String> getRuntimeOnlyNames() {
        getConfigurationNamesUsing { SourceSet srcSet -> srcSet.getRuntimeOnlyConfigurationName() }
    }

    Tuple2<Configuration, Configuration> getRuntimeOnlyConfigurations() {
        getConfigurationsUsing { it -> getRuntimeOnlyNames() }
    }

    Tuple2<DependencySet, DependencySet> getRuntimeOnlyDependencySets(DependencySetType dependencySetType) {
        getDependencySetsUsing(dependencySetType) { it -> getRuntimeOnlyConfigurations() }
    }

    Tuple2<String, String> getRuntimeClasspathNames() {
        getConfigurationNamesUsing { SourceSet srcSet -> srcSet.getRuntimeClasspathConfigurationName() }
    }

    Tuple2<Configuration, Configuration> getRuntimeClasspathConfigurations() {
        getConfigurationsUsing { it -> getRuntimeClasspathNames() }
    }

    Tuple2<DependencySet, DependencySet> getRuntimeClasspathDependencySets(DependencySetType dependencySetType) {
        getDependencySetsUsing(dependencySetType) { it -> getRuntimeClasspathConfigurations() }
    }

    private Tuple2<String, String> getConfigurationNamesUsing(Closure<String> configurationNameFunc) {
        new Tuple2<>(configurationNameFunc(crossBuild), configurationNameFunc(main))
    }

    /**
     *
     * @param configurationNamesFunc
     * @return May return a tuple with first element null!
     */
    private Tuple2<Configuration, Configuration> getConfigurationsUsing(Closure<Tuple2> configurationNamesFunc) {
        def names = configurationNamesFunc()
        def crossBuildConfiguration = project.configurations.findByName(names.first)
        def mainConfiguration = project.configurations[names.second]
        new Tuple2<>(crossBuildConfiguration, mainConfiguration)
    }

    /**
     *
     * @param dependencySetType
     * @param configurationsFunc
     * @return May return a tuple with first element null!
     */
    private Tuple2<DependencySet, DependencySet> getDependencySetsUsing(DependencySetType dependencySetType,
                                                                        Closure<Tuple2> configurationsFunc) {
        def configurations = configurationsFunc()
        def dpendencySetFunc = { Configuration configuration, DependencySetType dst ->
            switch (dst) {
                case DependencySetType.ALL: return configuration?.allDependencies
                case DependencySetType.SINGLE: return configuration?.dependencies
            }
        }
        def crossBuildDependencySet = dpendencySetFunc(configurations.first, dependencySetType)
        def mainDependencySet = dpendencySetFunc(configurations.second, dependencySetType)
        new Tuple2<>(crossBuildDependencySet, mainDependencySet)
    }

    @SuppressWarnings(['ThisReferenceEscapesConstructor', 'PrivateFieldCouldBeFinal'])
    static enum ViewType {
        COMPILE(JavaPlugin.COMPILE_CONFIGURATION_NAME),
        COMPILE_ONLY(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME),
        COMPILE_CLASSPATH(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME),
        RUNTIME(JavaPlugin.RUNTIME_CONFIGURATION_NAME),
        RUNTIME_ONLY(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME),
        RUNTIME_CLASSPATH(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME),
        IMPLEMENTATION(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME),
        TEST_COMPILE(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME),
        TEST_COMPILE_ONLY(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME),
        TEST_COMPILE_CLASSPATH(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME),
        TEST_IMPLEMENTATION(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME),
        TEST_RUNTIME(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME),
        TEST_RUNTIME_ONLY(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME),
        TEST_RUNTIME_CLASSPATH(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME),
        DEFAULT('default')

        private final String name

        private static Map<String, ViewType> mappings

        private ViewType(String name) {
            this.name = name
            mappings = mappings ?: [:]
            mappings.put(name, this)
        }

        String getName() {
            this.name
        }

        static ViewType from(String configurationName) {
            mappings.get(configurationName)
        }
    }
}
