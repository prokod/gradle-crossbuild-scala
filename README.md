# gradle crossbuild scala plugin
[![Build Status](https://travis-ci.org/prokod/gradle-crossbuild-scala.svg?branch=master)](https://travis-ci.org/prokod/gradle-crossbuild-scala)
[![codecov](https://codecov.io/gh/prokod/gradle-crossbuild-scala/branch/master/graph/badge.svg)](https://codecov.io/gh/prokod/gradle-crossbuild-scala)
[![Automated Release Notes by gren](https://img.shields.io/badge/%F0%9F%A4%96-release%20notes-00B2EE.svg)](https://github-tools.github.io/github-release-notes/)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/github/prokod/gradle-crossbuild/com.github.prokod.gradle-crossbuild.gradle.plugin/maven-metadata.xml.svg?colorB=007ec6&label=gradle%20plugin)](https://plugins.gradle.org/plugin/com.github.prokod.gradle-crossbuild)

## Features
- **Multi-module projects support** Supports both simple projects and multi-module projects.<br>In multi-module projects support mixed cases where only some of the modules needs cross compiling.
- **Powerful DSL** Plugin DSL can be written once for all sub projects using `subprojects {}` block.<br>Specific DSL definition can be afterwards added to individual sub projects.<br>It supports shorthands to avoid repetitions.<br>Operates in both eager and lazy (wrapped in `pluginManager.withPlugin {}` block) `apply` modes.
- **Integrates with maven-publish plugin** When used, can be leveraged to publish cross building artifacts.
- **Implicit/Explicit scala lib dependency declaration** Supports declaring both<br>simple case implicit `compile 3rd-party-scala-lib_?` type of dependencies<br>and also more complex explicit `crossBuild212Compile 3rd-party-platform-scala-lib-x-y-z_2.12` platform type of dependencies.
- **Applied easily on existing projects** As the plugin maintains a strict separation between `main` source set configurations and <code>crossBuild*XYZ*</code> ones, a simple non cross build project can be easily and gradually transformed to a cross build one.
- **Testing support**  As mentioned above strict separation of source sets, keeps `main` source set test configurations intact.

## Shortcomings
- *Cross building for test/check tasks* are not supported.
- *No support for java-library plugin* java-library `api`, `apiComponents` and `runtimeComponents` configurations are not supported yet. 

## <a name="getting_plugin"></a>Getting the plugin
#### Using the plugin DSL:
```groovy
plugins {
    id "com.github.prokod.gradle-crossbuild" version "0.10.0"
}
```  
    
#### Using legacy plugin application
```groovy
buildscript {
    dependencies {
        classpath("com.github.prokod:gradle-crossbuild-scala:0.10.0")
    }
}
```

## Quick start
### recommended cross building apply strategy
This is especially true for multi module projects but not just.<br/>
- Wire up your build scripts in the project in such a way that you are able to successfully build it for single scala version.<br/> Beware that this plugin does not support `api` configuration so avoid using it, for further details see [java-library related section](#java_library).

  > **_NOTE:_** Do not worry in this stage about publishing artifacts as cross building with publishing is supported by the plugin.<br/>It will be some what wasted effort to do that here and then modify it to the cross build scenario.
- After that add the cross building plugin without changing any of the inter and external dependencies. Follow base step - [getting the plugin](#getting_plugin) and then configure it, see please both [plugin configuration options](#plugin_configuration_options) and [basic plugin configuration](#basic_plugin_configuration)

  > **_NOTE:_** If you have to change your dependencies because of applying the plugin and trying explicitly `gradle build`, something is fishy.<br/>
    You see, the plugin is designed in such a way that it borrows from the state of the project's dependency tree already in place without changing it and then it adds a somewhat parallel dependency tree for each of the cross building variants. 
- To configure the plugin efficiently please see recommended [multi module projects apply patterns](#multi_module_apply_patterns).

  > **_NOTE:_** Use the '?' question mark to express cross build dependency for `implementation`/`compile`/`runtime`/`...` configurations - the plugin will add a correct dependency resolution according to the provided `crossBuild {}` block.<br/>
    Use the provided explicit <code>crossBuild*XYZ*</code>`Implementation`/`Compile`/`Runtime`/`...` configuration when you need a finer granularity in expressing the cross build dependencies
- Publish cross building artifacts, for that please have a look [here](#publishing)

  > **_NOTE:_** cross build artifact naming is governed by `archive.appendixPattern` which by default is `_?` meaning module `lib` will be resolved a cross build to `lib_2.11`/`_2.12`/`...` 
- To test that everything works as expected, both `gradle build` (which also runs the tests) and `gradle publishToMavenLocal` (which goes from cross building, artifact creation and publishing) should succeed.

  > **_NOTE:_** Look under ~/.m2/repository/... to assert the end result is the one you have wished for.<br/>
        
### <a name="basic_plugin_configuration"></a>cross building - basic plugin configuration
#### applying the plugin
1. Apply the plugin and use the provided DSL. For example:

    ```groovy
    archivesBaseName = 'lib'

    apply plugin: 'com.github.prokod.gradle-crossbuild'

    crossBuild {
        builds {
            v211
            v212
        }
    }
    ```
   > **_NOTE:_** Another variant which might appeal aesthetically better to some
   > ```groovy
   > archivesBaseName = 'lib'
   > 
   > apply plugin: 'com.github.prokod.gradle-crossbuild'
   >
   > crossBuild {
   >     builds 
   >         scala {
   >             scalaVersions = ['2.11', '2.12']
   >         }
   >     }
   > }
   > ```

    ```groovy
    dependencies {
        compile ("com.google.protobuf:protobuf-java:$protobufVersion")
        compile ("joda-time:joda-time:$jodaVersion")
        // The question mark is being replaced based on the scala version being built
        compile ("org.scalaz:scalaz_?:$scalazVersion")
    }
    ```

1. `gradle tasks`

    `gradle-crossbuild` plugin adds the following user faced tasks to the project `crossBuild211Classes`, `crossBuild211Jar`, `crossBuild212Classes`, `crossBuild212Jar` based on the plugin DSL `builds {}` block

    ```sh
    > ./gradlew tasks

    ------------------------------------------------------------
    All tasks runnable from root project
    ------------------------------------------------------------

    Build tasks
    -----------
    assemble - Assembles the outputs of this project.
    build - Assembles and tests this project.
    buildDependents - Assembles and tests this project and all projects that depend on it.
    buildNeeded - Assembles and tests this project and all projects it depends on.
    classes - Assembles main classes.
    clean - Deletes the build directory.
    crossBuildV211Classes - Assembles cross build v211 classes.
    crossBuildV211Jar - Assembles a jar archive containing 211 classes
    crossBuildV212Classes - Assembles cross build v212 classes.
    crossBuildV212Jar - Assembles a jar archive containing 212 classes
    jar - Assembles a jar archive containing the main classes.
    testClasses - Assembles test classes.
    ...
    ```

1. <code>gradle crossBuild*V211*Jar crossBuild*V212*Jar ...</code>

    ```sh
    > ./gradlew crossBuildV211Jar crossBuildV212Jar
    ...
    Tasks to be executed: [task ':compileCrossBuildV211Java', task ':compileCrossBuildV211Scala', task ':processCrossBuildV211Resources', task ':crossBuildV211Classes', task ':crossBuildV211Jar', task ':compileCrossBuildV212Java', task ':compileCrossBuildV212Scala', task ':processCrossBuildV212Resources', task ':crossBuildV212Classes', task ':crossBuildV212Jar']
    ...
    :crossBuildV211Jar (Thread[Connection worker,5,main]) completed. Took 0.04 secs.
    ...
    :crossBuildV212Jar (Thread[Connection worker,5,main]) completed. Took 0.007 secs.

    > ls ./build/libs
    lib_2.11.jar  lib_2.12.jar
    ```

#### notes
> -  <a name="builds_dsl_short_hand"></a>When defining `builds {}` block, a short hand convention can be used for default values.<br/>
  To be able to use that, `build` item should be named by the following convention, for example:<br/>
  `xyz211` is translated to `{ "build": { "scalaVersions": ["2.11"], "name": "xyz211" ... }`
> - When using a dependency with '?' in `compile/implementation` configuration i.e `compile ("org.scalaz:scalaz_?:$scalazVersion")`, the plugin will replace this placeholder with the scala version defined in `builds {}` block according to the requested cross build variant/s.
> - `test/check` tasks are not being cross compiled and they use the default scala version.<br/>
  However, `compile/implementation` scala dependencies with '?' are being resolved according to the explicit neighbour `scala-library` dependency.<br/>
  If a user would like to run tests with different scala versions, he needs to change the relevant scala library version and neighbouring 3rd party scala dependencies in build.gradle

### <a name="publishing"></a>cross building with publishing  
#### leveraging gradle maven-publish plugin
1. Apply the plugin and add maven-publish plugin. For example:

    ```groovy
    apply plugin: 'com.github.prokod.gradle-crossbuild'
    apply plugin: 'maven-publish'
    
    group = 'x.y.z'
    archivesBaseName = 'lib'
    
    crossBuild {
        builds {
            v211
        }
    }
    
    // 'maven-publish' plugin usage for publishing crossbuild artifacts
    publishing {
        publications {
            // Create a publication
            crossBuildV211(MavenPublication) {
                // By default groupId equals group
                groupId = 'x.y.z'
                // By default artifactId is set to crossBuildJar task `baseName`
                artifactId = 'lib_2.11'
                // actual artifact for this publication as a Jar task from crossbuild plugin
                artifact crossBuildV211Jar
            }
        }
    }
    ...
    ```

1. `gradle tasks`

    Notice that now the following publish related user faced tasks are added to the project:

    ```sh
    > ./gradlew tasks

    ------------------------------------------------------------
    All tasks runnable from project :lib
    ------------------------------------------------------------
    ...
    Publishing tasks
    ----------------
    generatePomFileForCrossBuild211Publication - Generates the Maven POM file for publication 'crossBuild211'.
    publish - Publishes all publications produced by this project.
    publishCrossBuild211PublicationToMavenLocal - Publishes Maven publication 'crossBuild211' to the local Maven repository.
    publishToMavenLocal - Publishes all Maven publications produced by this project to the local Maven cache.
    ```

1. `gradle publishToMavenLocal`

    ```sh
    > ./gradlew publishToMavenLocal
    ...
    Tasks to be executed: [task ':compileCrossBuild211Java', task ':compileCrossBuild211Scala', task ':processCrossBuild211Resources', task ':crossBuild211Classes', task ':crossBuild211Jar', task ':generatePomFileForCrossBuild211Publication', task ':publishCrossBuild211PublicationToMavenLocal', task ':publishToMavenLocal']
    Tasks to be executed: [task ':compileCrossBuild211Java', task ':compileCrossBuild211Scala', task ':processCrossBuild211Resources', task ':crossBuild211Classes', task ':crossBuild211Jar', task ':generatePomFileForCrossBuild211Publication', task ':publishCrossBuild211PublicationToMavenLocal', task ':publishToMavenLocal']
    ...
    ```

#### Gradle configurations -> Maven Scopes
The plugin handles pom generation for the different user defined publications.
The transformation from Gradle's own Configurations to Maven's Scopes is done using the following transformation matrix
  
| Maven Scope | Gradle transformation function |
|-------------|--------------------------------|
| `COMPILE`   | `GCC - (GCC - GRC)`            |
| `RUNTIME`   | `GRC - GCC`                    |
| `PROVIDED`  | `GCC - GRC`                    |
  
Where `GCC` is Gradle's CompileClasspath dependency set; `GRC`is Gradle's RuntimeClasspath dependency set

#### overriding plugin's internal pom.withXml
The plugin as seen above handles pom generation in an opinionated way. If one wants to override this behavior, he can do that by providing his own `pom.withXml` handler for the relevant publications.<br>
When the plugin detects custom `pom.withXml` handler, the internal handler is skipped altogether.

An example for a custom `pom.withXml` handler:
```groovy
  pom.withXml { xml ->
      def dependenciesNode = xml.asNode().dependencies?.getAt(0)
      if (dependenciesNode == null) {
          dependenciesNode = xml.asNode().appendNode('dependencies')
      }

      project.configurations.crossBuildScala_210MavenCompileScope.allDependencies.each { dep ->
          def dependencyNode = dependenciesNode.appendNode('dependency')
          dependencyNode.appendNode('groupId', dep.group)
          dependencyNode.appendNode('artifactId', dep.name)
          dependencyNode.appendNode('version', dep.version)
          dependencyNode.appendNode('scope', 'compile')
      }

      project.configurations.crossBuildScala_210MavenRuntimeScope.allDependencies.each { dep ->
          def dependencyNode = dependenciesNode.appendNode('dependency')
          dependencyNode.appendNode('groupId', dep.group)
          dependencyNode.appendNode('artifactId', dep.name)
          dependencyNode.appendNode('version', dep.version)
          dependencyNode.appendNode('scope', 'runtime')
      }
  }
```
In this example, we override default behaviour, dropping provided scope dependencies from generated pom.

#### notes
> - **Using `pluginManager`** 'gradle-crossbuild' plugin leverages Gradle's `pluginManager` To update 'maven-publish' cross-build related publications
> - Behind the scenes Configurations <code>crossBuild*XYZ*MavenCompileScope</code>, <code>crossBuild*XYZ*MavenRuntimeScope</code> are being populated from corresponding <code>crossBuild*XYZ*CompileClasspath</code>, <code>crossBuild*XYZ*RuntimeClasspath</code> and afterwards being used within `pom.withXml {}` block.<br/>
    It follows a similar line of thought as `conf2ScopeMappings.addMapping()` in Gradle's maven plugin.<br/>
    Beware, Behind the scenes the jars and the publications are decoupled, the logical linkage between a cross built Jar and the publication is made by giving the publication item a name of the following convention <code>crossBuild*XYZ*(MavenPublication)</code> where XYZ is the build name from `builds {}` block following the pattern examples in [table](#build_scenarios) under **SourceSet/s** column.
> - For Gradle 5.x beware that `publishing {}` block does not support deferred configuration anymore and in that case `artifact crossBuild211Jar` should be wrapped in `afterEvaluate {}` block<br>
    Please see Gradle documentation [here](https://docs.gradle.org/current/userguide/publishing_maven.html#publishing_maven:deferred_configuration)

### <a name="plugin_configuration_options"></a>cross building - configuration options
`targetVersionItem.archiveAppendix`, `crossBuild.scalaVersionsCatalog`, <code>crossBuild211*XYZ*</code> pre defined configurations

```groovy
apply plugin: 'com.github.prokod.gradle-crossbuild'

crossBuild {
    scalaVersionsCatalog = ['2.10': '2.10.6', '2.11': '2.11.12', '2.12':'2.12.8' ...]

    archive.appendixPattern = '_?'          // Default appendix pattern for all builds

    builds {
        v210
        v211 {
            scalaVersions = ['2.11']        // By default derived from build name in short hand build name
            archive.appendixPattern = '_?'  // By default the value is "_?"
                                            // In the default case will yield '_2.11')
                                            // If different from upper level config, it will override it.
        }
    }
}
```

```groovy
dependencies {
    compile ("com.google.protobuf:protobuf-java:$protobufVersion") 
    compile ("joda-time:joda-time:$jodaVersion")
    // The question mark is being replaced based on the scala version being built
    compile ("org.scalaz:scalaz_?:$scalazVersion")
    compile ('org.scala-lang:scala-library:2.11.12')                        // 'default' building flavour scala library needed for test/check tasks in case '?' dependencies are declared
    
    compileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')                   // 'default' building flavour (when calling gradle build)
    crossBuildV210CompileOnly ('org.apache.spark:spark-sql_2.10:1.6.3')     // A configuration auto generated by the plugin
    crossBuildV211CompileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')     // A configuration auto generated by the plugin
}
```

#### Notes
> - If `crossBuild.scalaVersionsCatalog` is not defined, a default one will be used (might get outdated).
> - Per build item in `builds {}` block, Scala version(s) is set either by explicitly setting `build.scalaVersions` or implicitly through `build.name`.<br/>
  See the different [build scenarios](#build_scenarios) for more details
> - **Declaring cross building dependencies explicitly**:
  ```groovy
  crossBuild {
    builds {
      v210
      v211
    }
  }
  
  dependencies {
    compileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')
    crossBuildV210CompileOnly ('org.apache.spark:spark-sql_2.10:1.6.3')
    crossBuildV211CompileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')
  }
  ```
>  The plugin DSL defines in the above `crossBuild {}` block two cross building variants. One for Scala **2.10** and one for **2.11**.<br/>
  When declaring **explicit** cross building dependency, for instance when using Spark or Kafka 3rd party libraries, when dependency library name contains platform version, All the different variants should be declared, like shown above.  
> - **default-variant** In the above example, the spark version of the dependency specified for `compileOnly` configuration which we refer here as **default-variant** one for `build`, `test/check` tasks only.<br/>
  The other dependency specified for Scala versions **2.10**, **2.11** respectively (`crossBuild210Compile/Only`, `crossBuild211Compile/Only`), will be used only for `crossBuild210Jar`, `crossBuild211Jar`, and other corresponding task variants (`publishCrossBuild210PublicationToMavenLocal`, `publishCrossBuild211PublicationToMavenLocal` ...)<br/>
> - The plugin provides pre defined configurations (sourceSets) being used by the matching pre generated Jar tasks:<br/>
  crossBuild211Jar -> crossBuild211Compile, crossBuild211CompileOnly, ...

## <a name="build_scenarios"></a>`builds {}` -> Gradle SourceSets, Configurations and Tasks
The following table shows some commonly build scenarios expressed through the plugin DSL and how they are actually resolved

| build scenario | SourceSet/s    | Configuration/s | Task/s |
|----------------|----------------|------------------|--------|
|<pre>`v210`</pre>          | crossBuild*V210* | <ul><li>crossBuild*V210*Implementation</li><li>crossBuild*V210*Compile</li><li>crossBuild*V210*CompileOnly</li><li>crossBuild*V210*Runtime</li><li>crossBuild*V210*CompileClasspath</li></ul> | <ul><li>JavaPlugin -> crossBuild*V210*Java</li><li>ScalaPlugin -> crossBuild*V210*Scala</li><li>crossBuild*V210*Jar</li></ul> |
|<pre>`v211 {`<br/>`    scalaVersions = ['2.11', '2.12']`<br/>`}`</pre> | crossBuild*V211_211*, crossBuild*V211_212* | <ul><li>crossBuild*V211_211*Compile<br/>...</li><li>crossBuild*V211_212*Compile<br/>...</li></ul> | <ul><li>JavaPlugin -> crossBuild*V211_211*Java, crossBuild*V211_212*Java</li><li>ScalaPlugin -> crossBuild*V211_211*Scala, crossBuild*V211_212*Scala</li><li>crossBuild*V211_211*Jar, crossBuild*V211_212*Jar</li></ul> |
|<pre>`v213 {`<br/>`    scalaVersions = ['2.13']`<br/>`}`</pre> | crossBuild*V213* | <ul><li>crossBuild*V213*Compile<br/>...</li></ul> | <ul><li>JavaPlugin -> crossBuild*V213*Java</li><li>ScalaPlugin -> crossBuild*V213*Scala</li><li>crossBuild*V213*Jar</li></ul> |
|<pre>`spark24 {`<br/>`    scalaVersions = ['2.11', '2.12']`<br/>`}`</pre> |  crossBuild*Spark24_211*, crossBuild*Spark24_212* | <ul><li>crossBuild*Spark24_211*Compile<br/>...</li><li>crossBuild*Spark24_212*Compile<br/>...</li></ul> | <ul><li>JavaPlugin -> crossBuild*Spark24_211*Java, crossBuild*Spark24_212*Java</li><li>ScalaPlugin -> crossBuild*Spark24_211*Scala, crossBuild*Spark24_212*Scala</li><li>crossBuild*Spark24_211*Jar, crossBuild*Spark24_212*Jar</li></ul> |

## <a name="java_library"></a>`implementation` configuration and `java-library` plugin
`implementation` java plugin based configuration is supported by the plugin and cross build variants will be added to the cross build projects. <br/> 
When using `implementation` configuration in a multi module project together with cross build plugin applied a suggestion is to read [java-library plugin doc](https://docs.gradle.org/current/userguide/java_library_plugin.html) before hand, especially for new comers from Maven, `compile`/`runtime` users. <br/>
As the cross building plugin is not supporting yet `java-library` plugin there is no counter part to `implementation` by the form of `api` configuration and to emulate that one should use `compile` configuration instead

## <a name="multi_module_apply_patterns"></a>multi-module project plugin apply patterns
To apply cross building to a multi-module project use one of the following suggested layouts:

### layout 1 (a.k.a lazy apply)
- In the root project build.gradle:
```groovy
plugins {
    id "com.github.prokod.gradle-crossbuild" version '0.10.0' apply false
}

allprojects {
    apply plugin: 'base'
    group = 'x.y.z'
    version = '1.0-SNAPSHOT'

    repositories {
        mavenCentral()
    }

    pluginManager.withPlugin('com.github.prokod.gradle-crossbuild') {
        crossBuild {

            scalaVersionsCatalog = ['2.11':'2.11.12', '2.12':'2.12.8']

            builds {
                spark240_211
                spark243_212
            }
        }
    }

    pluginManager.withPlugin('maven-publish') {
        publishing {
            publications {
                crossBuildSpark240_211(MavenPublication) {
                    artifact crossBuildSpark240_211Jar
                }
                crossBuildSpark243_212(MavenPublication) {
                    artifact crossBuildSpark243_212Jar
                }
            }
        }
    }
}
```
- In sub projects' build.gradle:
```groovy
apply plugin: 'com.github.prokod.gradle-crossbuild'
...
```

### layout 2 (a.k.a eager apply)
- In the root project build.gradle:
```groovy
plugins {
    id "com.github.prokod.gradle-crossbuild" version '0.10.0' apply false
}

allprojects {
    group = 'x.y.z'
    version = '1.0-SNAPSHOT'

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'com.github.prokod.gradle-crossbuild'
    apply plugin: 'maven-publish'

    crossBuild {

        scalaVersionsCatalog = ['2.11':'2.11.12', '2.12':'2.12.8']

        builds {
            spark233_211 {
                archive.appendixPattern = '-2-3-3_?'
            }
            spark243 {
                scalaVersions = ['2.11', '2,12']
                archive.appendixPattern = '-2-4-3_?'
            }
        }
    }

    publishing {
        publications {
            crossBuildSpark233_211(MavenPublication) {
                artifact crossBuildSpark233_211Jar
            }
            crossBuildSpark243_211(MavenPublication) {
                artifact crossBuildSpark243_211Jar
            }
            crossBuildSpark243_212(MavenPublication) {
                artifact crossBuildSpark243_212Jar
            }
        }
    }
}
```

## Supported Gradle versions
|plugin version | Tested Gradle versions |
|---------------|------------------------|
|0.10.x         | 4.10.3, 5.6.4, 6.0.1   |
|0.9.x          | 4.2, 4.10.3, 5.4.1     |
|0.4.x          | 2.14, 3.0, 4.1         |

## Contributing
- This project uses gitflow process. PRs should be done against develop branch
- PRs to develop should be style checked/tested locally by running `./gradlew clean check`

## Building
- `./gradlew clean build`
