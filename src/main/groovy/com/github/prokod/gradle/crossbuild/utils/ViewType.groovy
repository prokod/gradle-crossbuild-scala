package com.github.prokod.gradle.crossbuild.utils

import org.gradle.api.plugins.JavaPlugin

/**
 * ViewType tags:
 * <ul>
 *     <li>canBeConsumed</li>
 *     <li>canBeResolved</li>
 * </ul>
 * tags combinations and their meaning:
 * <ul>
 *     <li>canBeConsumed - user facing configuration (View). A configuration users can manipulate add/remove dependencies to/from</li>
 *     <li>canBeResolved - task facing configuration (View). A configuration that acts as a sink to user facing configurations and resolved within a task (symbolic dependencies are being resolved to actual files/resources)</li>
 *     <li>canBeConsumed, canBeResolved - legacy configurations like compile</li>
 * </ul>
 */
@SuppressWarnings(['ThisReferenceEscapesConstructor', 'PrivateFieldCouldBeFinal'])
enum ViewType {
    COMPILE(JavaPlugin.COMPILE_CONFIGURATION_NAME, ['canBeConsumed', 'canBeResolved']),
    COMPILE_ONLY(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, ['canBeConsumed']),
    COMPILE_CLASSPATH(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, ['canBeResolved']),
    RUNTIME(JavaPlugin.RUNTIME_CONFIGURATION_NAME, ['canBeConsumed', 'canBeResolved']),
    RUNTIME_ONLY(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, ['canBeConsumed']),
    RUNTIME_CLASSPATH(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, ['canBeResolved']),
    IMPLEMENTATION(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, ['canBeConsumed']),
    TEST_COMPILE(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME, ['canBeConsumed', 'canBeResolved', 'test']),
    TEST_COMPILE_ONLY(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME, ['canBeConsumed', 'test']),
    TEST_COMPILE_CLASSPATH(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, ['canBeResolved', 'test']),
    TEST_IMPLEMENTATION(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, ['canBeConsumed', 'test']),
    TEST_RUNTIME(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME, ['canBeConsumed', 'canBeResolved', 'test']),
    TEST_RUNTIME_ONLY(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, ['canBeConsumed', 'test']),
    TEST_RUNTIME_CLASSPATH(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, ['canBeResolved', 'test']),
    DEFAULT('default', ['canBeConsumed', 'canBeResolved'])

    private final String name
    private final List<String> tags

    private static Map<String, ViewType> mappings

    private ViewType(String name, List<String> tags) {
        this.name = name
        this.tags = tags
        mappings = mappings ?: [:]
        mappings.put(name, this)
    }

    String getName() {
        this.name
    }

    static ViewType from(String configurationName) {
        mappings.get(configurationName)
    }

    /**
     * All ViewType filtered by given tags
     *
     * @return
     */
    static Collection<ViewType> filterViewsBy(Closure<Boolean>... tagsPredicates) {
        def views = tagsPredicates.collectMany { tagPredicate ->
            values().findAll { viewType ->
                tagPredicate(viewType.tags) && viewType.name != 'default'
            }
        }

        views.groupBy { view -> view }.findAll { entry ->
            entry.value.size() == tagsPredicates.length
        }.collect { entry ->
            entry.key
        }
    }
}