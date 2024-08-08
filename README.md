# Gradle crossbuild Scala plugin

[![Build Status](https://github.com/prokod/gradle-crossbuild-scala/actions/workflows/main.yml/badge.svg)](https://github.com/prokod/gradle-crossbuild-scala/actions)
[![codecov](https://codecov.io/gh/prokod/gradle-crossbuild-scala/branch/master/graph/badge.svg)](https://codecov.io/gh/prokod/gradle-crossbuild-scala)
[![Automated Release Notes by gren](https://img.shields.io/badge/%F0%9F%A4%96-release%20notes-00B2EE.svg)](https://github-tools.github.io/github-release-notes/)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/github/prokod/gradle-crossbuild/com.github.prokod.gradle-crossbuild.gradle.plugin/maven-metadata.xml.svg?colorB=007ec6&label=gradle%20plugin)](https://plugins.gradle.org/plugin/com.github.prokod.gradle-crossbuild)

## Features

- **Scala3 support** Seamlessly cross building of both Scala 2.x and Scala 3.x with unified DSL.
- **Multi-module projects support** Supports both simple projects and multi-module projects.<br/>In multi-module projects support mixed cases where only some of the modules needs cross compiling.
- **Powerful DSL** Plugin DSL can be written once for all subprojects using `subprojects {}` block.<br/>Specific DSL definition can be afterward added to individual subprojects.<br/>It supports shorthands to avoid repetitions.<br/>Operates in both eager and lazy (wrapped in `pluginManager.withPlugin {}` block) `apply` modes.
- **Multi-aspect cross building** Supports cross building for Scala aspect and on top of that any other custom aspect, for instance Spark. Further details [multi-aspect cross building](#multi_aspect_cross_building)
- **Integrates with maven-publish plugin** When used, can be leveraged to publish cross building artifacts.
- **Implicit/Explicit scala lib dependency declaration** Supports declaring both<br/>simple case implicit `implementation 3rd-party-scala-lib_2.12` type of dependencies<br/>and also finer granular explicit `crossBuildSpark24_212Implementation spark-streaming-kafka-0-10_2.12` type of dependencies.
- **Applied easily on existing projects** As the plugin maintains a strict separation between `main` source set configurations and <code>crossBuild*XYZ*</code> ones, a simple non-cross build project can be easily and gradually transformed to a cross build one.
- **Testing support**  As mentioned above strict separation of source sets, keeps `main` source set test configurations intact.

## Shortcomings

- **test/check tasks** are supported for [default-variant](#default_variant) of Scala version only.

## <a name="getting_plugin"></a>Getting the plugin

### Using the `plugins` DSL

```groovy
plugins {
    id "com.github.prokod.gradle-crossbuild-scala" version "0.17.0"
}
```

### Using legacy `buildscript`

```groovy
buildscript {
    dependencies {
        classpath("com.github.prokod:gradle-crossbuild-scala:0.17.0")
    }
}
```

## Quick start

### Recommended cross building apply strategy

This is especially true for multi-module projects but not just.<br/>

- Wire up your build scripts in the project in such a way that you are able to successfully build it for single scala version.

  > [!NOTE]  
  > Do not worry in this stage about publishing artifacts as cross building with publishing is supported by the plugin.<br/>It will be somewhat wasted effort to do that here and then modify it to the cross build scenario.

- After that add the cross building plugin without changing any of the internal and external dependencies. Follow base step - [getting the plugin](#getting_plugin) and then configure it further using the following guidelines: [plugin configuration options](#plugin_configuration_options) and [basic plugin configuration](#basic_plugin_configuration)

  > [!NOTE]  
  > If you have to change your dependencies because of applying the plugin and trying explicitly `gradle build`, something is fishy.<br/>
    You see, the plugin is designed in such a way that it borrows from the state of the project's dependency tree already in place without changing it. It then adds a somewhat parallel dependency tree for each of the cross building variants.

- To configure the plugin efficiently please follow [Multi module projects apply patterns](#multi_module_apply_patterns).

  > [!NOTE]  
  > From version `0.12.x` there is no need to have any special glob pattern to express cross build dependency for `implementation`/`api`/`runtime`/`...` configurations - the plugin will add a correct dependency resolution according to the provided `crossBuild {}` plugin dsl block.<br/>
    Up to version `0.11.x` (inclusive) use the '?' question mark to express cross build dependency inside `implementation`/`api`/`runtime`/`...` configurations.<br/>
    Use the provided explicit <code>crossBuild*XYZ*</code>`Implementation`/`api`/`Runtime`/`...` configuration when you need a finer granularity in expressing the cross build dependencies

- Publish cross building artifacts, for that please take a look at [publishing](#publishing)

  > [!NOTE]  
  > cross build artifact naming is governed by `archive.appendixPattern` which by default is `_?` meaning for example, that module `lib` will be resolved to `lib_2.11`/`_2.12`/`...` according to the correlating `crossBuild {}` plugin dsl block

- To test that everything works as expected:
  1. `gradle build` - which runs build and tests for a single Scala version - default one.
  1. `gradle crossBuildAssemble` - which builds ans assembles **all** cross build into respective artifacts.
  1. `gradle publishToMavenLocal` - which goes from cross building, artifact creation (as above) and then publishing to local maven.

  > [!NOTE]  
  > Look under `build/libs` , `~/.m2/repository/...` respectively, to assert the end result is the one you have wished for.  `crossBuildAssemble` task is available from version **`0.15.0`**
  
### Multi-module projects and applying cross build plugin only for some

From version **`0.11.x`** the plugin supports multi-module projects where **only** some modules have cross build plugin applied to.<br/>
This helps with cases where some modules depend on legacy plugins that do not play nicely with the cross build plugin like legacy `play` plugin for instance :)<br/>
Thanks [borissmidt](https://github.com/borissmidt) for the collaboration on that.

### <a name="basic_plugin_configuration"></a>Cross building - basic plugin configuration

#### Applying the plugin

1. Apply the plugin and use the provided DSL. For example:

    ```groovy
    archivesBaseName = 'lib'

    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'

    crossBuild {
        builds {
            v211
            v212
        }
    }
    ```

   > [!NOTE]  
   > Another variant which might appeal aesthetically better to some
   >
   > ```groovy
   > archivesBaseName = 'lib'
   > 
   > apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
   >
   > crossBuild {
   >     builds {
   >         scala {
   >             scalaVersions = ['2.11', '2.12']
   >         }
   >     }
   > }
   > ```

    ```groovy
    dependencies {
        implementation ("com.google.protobuf:protobuf-java:$protobufVersion")
        implementation ("joda-time:joda-time:$jodaVersion")
        // Scala 2.12 is the default cross built Scala version
        // the plugin replaces the default based on the Scala version being built
        implementation ("org.scalaz:scalaz_2.12:$scalazVersion")
    }
    ```

   > [!NOTE]  
   > **Up to** version `0.11.x` (inclusive) 3rd party Scala lib dependencies are expressed using '?'
   > question mark (when using implicit pattern type dependencies)
   >
   > ```groovy
   > dependencies {
   >     implementation ("com.google.protobuf:protobuf-java:$protobufVersion")
   >     implementation ("joda-time:joda-time:$jodaVersion")
   >     // The question mark is being replaced based on the Scala version being built
   >     implementation ("org.scalaz:scalaz_?:$scalazVersion")
   > }
   > ```

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

> [!NOTE]  
> - <a name="builds_dsl_shorthand"></a>When defining `builds {}` block, a shorthand convention can be used for default values.<br/>
  To be able to use that, `build` item should be named by the following convention, for example:<br/>
  `xyz211` is translated to `{ "build": { "scalaVersions": ["2.11"], "name": "xyz211" ... }`
> - `test/check` tasks are not being cross compiled, and they use the default Scala version.<br/>
  If a user would like to run tests with different Scala versions, he needs to change the relevant `scala-library` library version and neighbouring 3rd party scala dependencies in build.gradle

### <a name="publishing"></a>Cross building with publishing

#### Leveraging gradle maven-publish plugin

1. Apply the plugin and add maven-publish plugin. For example:

    ```groovy
    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
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
                // crossbuild plugin provide with a software component per crossbuild version
                from components.crossBuildV211
            }
        }
    }
   
    // ...
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

> [!NOTE]  
> - **Using `pluginManager`** 'gradle-crossbuild' plugin leverages Gradle's `pluginManager` To update 'maven-publish' cross-build related publications
> - Beware, Behind the scenes the software components and the publications are decoupled, the logical linkage between a cross built software component and the publication is made by giving the publication item a name of the following convention <code>crossBuild*XYZ*(MavenPublication)</code> where XYZ is the build name from `builds {}` block following the pattern examples in [table](#build_scenarios) under **SourceSet/s** column.

#### Gradle software components for Scala

The `java` plugin is providing the users with a `java` software component that later can be used to publish artifacts.<br>
Similarly the `cross build plugin` does the same and provide the users with a software component per scala version.<br>
The plugin is relying on maven-publish plugin to generate pom file based on the software component you have indicated in your publication.<br>
The provided software component names for each Scala version is exactly matching the provided sourceset for each Scala version defined see [Build scenarios table](#build_scenarios).<br>

> [!NOTE]  
> Up to plugin version 0.15.x included, adding a publication required the user to add an artifact entry to the publication block.
> From plugin version 0.16.x onward, adding a publication requires using `from components` instead

#### overriding plugin's internal pom.withXml

The plugin handles pom generation in an opinionated way and augment the output pom xml as needed per Scala version.<br>
If one wants to add more transformations on top, he can do that by providing his own `pom.withXml` handler for the relevant publications.<br/>

An example for a custom `pom.withXml` handler:

```groovy
crossBuild {
    builds {
        scala {
            scalaVersions = ['2.10', '2.11']
        }
    }
}

publishing {
    publications {
        maven(MavenPublication) {
            from components.java
        }
        crossBuildScala_210(MavenPublication) {
            from components.crossBuildScala_210

            pom.withXml { xml ->
                def dependenciesNode = xml.asNode().dependencies?.getAt(0)

                def dependencyNode = dependenciesNode.appendNode('dependency')
                dependencyNode.appendNode('groupId', 'org.apache.spark')
                dependencyNode.appendNode('artifactId', 'spark-sql_2.10')
                dependencyNode.appendNode('version', '1.6.3')
                dependencyNode.appendNode('scope', 'provided')
            }
        }
        crossBuildScala_211(MavenPublication) {
            from components.crossBuildScala_211
        }
    }
}
```

In this example, we add some arbitrary provided scope dependency to the generated pom.

> [!NOTE]  
> - Up to plugin version 0.15.x included, publishing was handled almost exclusively by the plugin when it comes to generating the pom xml dependencies section.
> - From plugin version 0.16.x onward this has been changed and now most of the heavy lifting is done by the maven-publish plugin.
> - As a result, from plugin 0.16.x onward, when providing your own `pom.withXml` handler, the crossbild plugin does not skip its internal handler anymore

### <a name="plugin_configuration_options"></a>Cross building - configuration options (DSL Reference)

`targetVersionItem.archiveAppendix`, `crossBuild.scalaVersionsCatalog`, <code>crossBuild211*XYZ*</code> pre defined configurations

```groovy
apply plugin: 'com.github.prokod.gradle-crossbuild-scala'

crossBuild {
    scalaVersionsCatalog = ['2.11':'2.11.12', '2.12':'2.12.17', '2.13':'2.13.10', '3':'3.2.2']

    archive.appendixPattern = '_?'                  // Default appendix pattern for all builds

    targetCompatibility.strategy = 'default'        // Default JVM target compatibility strategy oneof
                                                    // default/max/fail
    builds {
        v210
        v211 {
            scalaVersions = ['2.11']                // By default derived from build name in short hand
                                                    // build name
            archive.appendixPattern = '_?'          // By default the value is "_?"
                                                    // In the default case will yield '_2.11'
                                                    // If different from upper level config, it will override it.
            targetCompatibility.strategy = 'default'// By default the value is 'default'
                                                    // If different from upper level config, it will override it.
            ext = [:]                               // By default empty Map
                                                    // Once populated entries are propagated
                                                    // as SourceSet ExtraProperties
        }
    }
}
```

```groovy
dependencies {
    implementation ("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation ("joda-time:joda-time:$jodaVersion")
    implementation ("org.scalaz:scalaz_2.11:$scalazVersion")
    implementation ('org.scala-lang:scala-library:2.11.12')                        // 2.11 is the 'default' building flavour Scala version
    
    compileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')
    crossBuildV210CompileOnly ('org.apache.spark:spark-sql_2.10:1.6.3')            // A configuration auto generated by the plugin
    crossBuildV211CompileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')            // A configuration auto generated by the plugin
}
```

#### <a name="default_variant"></a>default-variant
In the above example, spark version of the dependency specified for `compileOnly` configuration which we refer here as **default-variant**, is important for `build`, `test/check` standard tasks.<br/>
The other dependencies specified for Scala versions **2.10**, **2.11** respectively (`crossBuild210CompileOnly`, `crossBuild211CompileOnly`), will be used only for `crossBuild210Jar`, `crossBuild211Jar` tasks, and other corresponding task variants (`publishCrossBuild210PublicationToMavenLocal`, `publishCrossBuild211PublicationToMavenLocal` ...) which can be referred as **cross-build-variants**

> [!NOTE]  
> - **Backward compatibility** maintained behaviours (still):
>   - Dependency with question mark Scala tag e.g. `org.scalaz:scalaz_?:$scalazVersion` is being replaced based on the Scala version being built
>   - Derived from previous point, `scala-library` library is needed for test/check tasks in case '?' dependencies are declared
> - If `crossBuild.scalaVersionsCatalog` is not defined, a default one will be used (might get outdated).
> - Per build item in `builds {}` block, Scala version(s) is set either by explicitly setting a build `scalaVersions` or implicitly through a build `name`.<br/>
    See the different [build scenarios](#build_scenarios) for more details
> - **Declaring cross building dependencies explicitly**:
>
>  ```groovy
>  crossBuild {
>    builds {
>      v210
>      v211
>    }
>  }
>  
>  dependencies {
>    compileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')
>    crossBuildV210CompileOnly ('org.apache.spark:spark-sql_2.10:1.6.3')
>    crossBuildV211CompileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')
>  }
>  ```
>  
>  The plugin DSL defines in the above `crossBuild {}` block two cross building variants. One for Scala **2.10** and one for **2.11**.<br/>
>  When declaring **explicit** cross building dependency, for instance when using Spark or Kafka 3rd party libraries, when dependency library name contains platform version, All the different variants should be declared, like shown above.  
>
> - The plugin provides predefined sourceSets and configurations which are linked to the matching pre generated Jar tasks like so:<br/>
  `(sourceSet)crossBuild211 -> (task)crossBuild211Jar -> (configuration)crossBuild211Implementation, (configuration)crossBuild211CompileOnly, ...`

### <a name="scala_3_cross_building"></a>Scala compilation for cross building

#### Opinionated scalac arguments - only where needed

- The plugin uses Gradle's Scala plugin `ScalaCompile` task. The base task already add scalac argument according to
  some predefined rules. Those rules do not cover all the scenarios obviously introduced by the plugin itself.
- There are multiple scenarios where the compilation will fail without intervention.
- To prevent cross building from failing due to incorrect arguments being set by the base task, the plugin has internal
  rules based on the Scala version being cross built, the target compatibility (target JVM) and scalac argument set
  by the user.
- Other compilation scenarios that do not fail are left to the base task without intervention from the plugin

> [!NOTE]
> 1. Internal plugin rules augmenting the base `ScalaCompile` task were tuned and fixed from plugin version `0.17.0`
>    onwards

#### <a name="scala_3_cross_building"></a>Scala 3 cross building - `targetCompatibilty.strategy`

From plugin version `0.15.0` Scala 3 is supported

As Scala 3 default JVM for later Scala 3.x versions changed to Java 11 and to support any new developments in that area,</br>
the plugin DSL was extended to support JVM targetCompatibility through strategy
- The available values: `default`/`max`/`fail`
- toolchain_or_`default` **strategy**
  In this strategy, the Scala compiler will try to adhere to the targetCompatibility JVM version.</br>
  If the targetCompatibility is not supported by the Scala version to be compiled, the default JVM for that compilation is changed to the **default** JVM for that specific Scala version.
- toolchain_or_`max` **strategy**
  In this strategy, if the targetCompatibility is not supported by the Scala version to be compiled, the default JVM for that compilation is changed to the **latest** JVM for that specific Scala version.
- toolchain_or_`fail` **strategy**
  In this strategy, if the targetCompatibility is not supported by the Scala version to be compiled, the build is stopped

> [!NOTE]  
> 1. Gradle supports scala 3 compilation (via zinc) - beware that Gradle 5.x is not supporting Scala 3 compilation
> 1. If you want to use Scala 3 instead of the scala-library dependency you should add the scala3-library_3 dependency.  
>    However, for the sake of the cross building across Scala 2/3 the plugin takes care of modifying the scala library dependency accordingly, internally

### <a name="multi_aspect_cross_building"></a>Multi-aspect cross building and Extra Properties propagation per aspects combination

- **Cross building DSL programmatically** - In the next code snippet you can observe how programmatically we are generating cross builds using the plugin DSL.
- **Extra properties per cross build** - Not only the cross building is described in a programmatic manner, you can also observe that specific unique meta data for each cross build permutation is generated using the plugin's `ext` entry

> [!NOTE]  
> The plugin injects a default extra property that holds the value of the respective Scala Compiler Version, named `scalaCompilerVersion`. This feature is supported in the plugin from version `0.14.x` onwards.
  
```groovy
// ...

subprojects {
    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
    apply plugin: 'maven-publish'

    crossBuild {
        scalaVersionsCatalog = ["2.13": "2.13.8", "2.12": "2.12.15"]

        def sparkVersionsForBoth = ["3.3.0", "3.2.1", "3.2.0"]
        def sparkVersionsFor2_12 = ["3.1.3", "3.1.2", "3.1.1", "3.1.0", "3.0.3", "3.0.2", "3.0.1", "3.0.0"]

        builds {
            for (spark in sparkVersionsForBoth) {
                create(spark) {
                    scalaVersions = ["2.12", "2.13"]
                    archive.appendixPattern = "-${spark}_?"
                    ext = ['sparkVersion':spark]
                }
            }

            for (spark in sparkVersionsFor2_12) {
                create(spark) {
                    scalaVersions = ["2.12"]
                    archive.appendixPattern = "-${spark}_?"
                    ext = ['sparkVersion':spark]
                }
            }
        }
    }
    
    dependencies {
        implementation "org.scala-lang:scala-library:2.13.8"
    }
}
```

### Scala Version Specific Source

- Each sourceSet that the plugin creates based on the DSL is assigned with its own `main` Scala source dir.

> [!NOTE]  
> Gradle's intrinsic convention. Supported in the plugin from version `0.14.x` onwards
  
  For instance, if a sourceSet id is `crossBuild211` then the source dir by convention is `src/crossBuild211/scala`
- We can now combine the previous topic of [multi-aspect cross building](#multi_aspect_cross_building) with the current topic and provide powerful way of maintaining different cross builds not only with their differences in library dependencies but also with their code differences

> [!NOTE]  
> The plugin handles cases where some class is present in both `/src/main/scala` and `/src/<crossBuildSourceSetId>/scala`. The original class in `/src/main/scala` is being excluded from the compile task for the relevant cross build

- Together with previous code snippet, the following one shows an example of leveraging jcp plugin to modify code per given cross build `ext` based meta data

```groovy
import com.igormaznitsa.jcp.gradle.JcpTask

plugins {
    id 'com.igormaznitsa.jcp'
}

group = 'com.github.prokod.it'
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

sourceSets.findAll { it.name.startsWith('crossBuild') }.each { sourceSet ->
    def compileTaskName = sourceSet.getCompileTaskName('scala')

    def spark = sourceSet.ext.sparkVersion
    def sparkStripped = spark.replaceAll('\\.', '')
    def scala = sourceSet.ext.scalaCompilerVersion
    def sparkMinor = spark.substring(0, spark.lastIndexOf('.'))
    def scalaCompat = scala.substring(0, scala.lastIndexOf('.'))
    def scalaCompatStripped = scalaCompat.replaceAll('\\.', '')
    
    // Here we use the preprocessing step done via jcp to generate compatible source code for the specific cross build
    // based on vars populated through the sourceSet ExtaProperties from previous code sinppet
    tasks.register("preprocess_${sourceSet.name}", JcpTask) {
        sources = sourceSets.main.scala.srcDirs
        target = file("src/${sourceSet.name}/scala")
        clearTarget = true
        fileExtensions = ["java", "scala"]

        vars = ["spark": spark, "sparkMinor": sparkMinor, "scala": scala, "scalaCompat": scalaCompat]
        outputs.upToDateWhen { target.get().exists() }
    }

    // Here we make tasks dependency so preprocessing will kick in at the right moment
    project.tasks.findByName(sourceSet.getCompileTaskName('scala')).with { ScalaCompile t ->
        t.dependsOn tasks.findByName("preprocess_${sourceSet.name}")
    }

    // Here we add in a programmatic way dependencies per cross build.
    // SourceSet ExtraProperties are leveraged again for that
    project.dependencies.add(sourceSet.getImplementationConfigurationName(), [group: "org.apache.spark", name: "spark-sql_${scalaCompat}", version: "${spark}"])

    // Here we add in a programmatic way publication per cross build.
    publishing {
        publications {
            create("crossBuild${sparkStripped}_${scalaCompatStripped}", MavenPublication) {
                afterEvaluate {
                    artifact project.tasks.findByName("crossBuild${sparkStripped}_${scalaCompatStripped}Jar")
                }
            }
        }
    }
}

```

## <a name="build_scenarios"></a>`builds {}` -> Gradle SourceSets, Configurations and Tasks

The following table shows some commonly build scenarios expressed through the plugin DSL and how they are actually resolved

| build scenario | SourceSet/s    | Configuration/s                                                                                                                                                                           | Task/s |
|----------------|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
|<pre>`v210`</pre>          | crossBuild*V210* | <ul><li>crossBuild*V210*Implementation</li><li>crossBuild*V210*Api</li><li>crossBuild*V210*CompileOnly</li><li>crossBuild*V210*Runtime</li><li>crossBuild*V210*CompileClasspath</li></ul> | <ul><li>JavaPlugin -> crossBuild*V210*Java</li><li>ScalaPlugin -> crossBuild*V210*Scala</li><li>crossBuild*V210*Jar</li></ul> |
|<pre>`v211 {`<br/>`    scalaVersions = ['2.11', '2.12']`<br/>`}`</pre> | crossBuild*V211_211*, crossBuild*V211_212* | <ul><li>crossBuild*V211_211*Implementation<br/>...</li><li>crossBuild*V211_212*Implementation<br/>...</li></ul>                                                                           | <ul><li>JavaPlugin -> crossBuild*V211_211*Java, crossBuild*V211_212*Java</li><li>ScalaPlugin -> crossBuild*V211_211*Scala, crossBuild*V211_212*Scala</li><li>crossBuild*V211_211*Jar, crossBuild*V211_212*Jar</li></ul> |
|<pre>`v213 {`<br/>`    scalaVersions = ['2.13']`<br/>`}`</pre> | crossBuild*V213* | <ul><li>crossBuild*V213*Implementation<br/>...</li></ul>                                                                                                                                  | <ul><li>JavaPlugin -> crossBuild*V213*Java</li><li>ScalaPlugin -> crossBuild*V213*Scala</li><li>crossBuild*V213*Jar</li></ul> |
|<pre>`spark24 {`<br/>`    scalaVersions = ['2.11', '2.12']`<br/>`}`</pre> |  crossBuild*Spark24_211*, crossBuild*Spark24_212* | <ul><li>crossBuild*Spark24_211*Implementation<br/>...</li><li>crossBuild*Spark24_212*Implementation<br/>...</li></ul>                                                                     | <ul><li>JavaPlugin -> crossBuild*Spark24_211*Java, crossBuild*Spark24_212*Java</li><li>ScalaPlugin -> crossBuild*Spark24_211*Scala, crossBuild*Spark24_212*Scala</li><li>crossBuild*Spark24_211*Jar, crossBuild*Spark24_212*Jar</li></ul> |

## <a name="java_library"></a>`implementation` configuration and `java-library` plugin

- `implementation` configuration (java plugin) and `api` configuration (java-library plugin) are both supported by the cross build plugin. Cross build variants for the `implementation`/`api` configurations will be added to the cross build projects. <br/> 
- When using the cross build plugin in a Multi module project, a suggestion is to read [java-library plugin doc](https://docs.gradle.org/current/userguide/java_library_plugin.html) beforehand, to better understand how Gradle treats dependencies with relation to configurations. This is highly recommended also for newcomers from Maven, where `compile`/`runtime` concepts are used. <br/>
- `api` configuration is supported from version **`0.13.0`**

## <a name="multi_module_apply_patterns"></a>Multi-module project plugin apply patterns

To apply cross building to a multi-module project use one of the following suggested layouts:

### Layout 1 (a.k.a lazy apply)

- In the root project build.gradle:

```groovy
plugins {
    id "com.github.prokod.gradle-crossbuild-scala" version '0.12.0' apply false
}

allprojects {
    apply plugin: 'base'
    group = 'x.y.z'
    version = '1.0-SNAPSHOT'

    repositories {
        mavenCentral()
    }

    pluginManager.withPlugin('com.github.prokod.gradle-crossbuild-scala') {
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

- In subprojects' build.gradle:

```groovy
apply plugin: 'com.github.prokod.gradle-crossbuild-scala'

// ...
```

### Layout 2 (a.k.a eager apply)

- In the root project build.gradle:

```groovy
plugins {
    id "com.github.prokod.gradle-crossbuild-scala" version '0.15.0' apply false
}

allprojects {
    group = 'x.y.z'
    version = '1.0-SNAPSHOT'

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'com.github.prokod.gradle-crossbuild-scala'
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

| plugin version | Tested Gradle versions           | Tested JVM | Tested Scala Compilers          |
|----------------|----------------------------------|------------|---------------------------------|
| 0.17.x         | 7.6.4, 8.7                       | 8, 11, 17  | 2.10.0-2.13.x</br>3.0.0 - 3.3.x |
| 0.16.x         | 5.6.4(1), 6.9.4(1), 7.6.2, 8.3   | 8, 11      | 2.10.0-2.13.x</br>3.0.0 - 3.2.x |
| 0.15.x         | 5.6.4(1), 6.9.4(1), 7.6.1, 8.0.2 | 8, 11      | 2.10.0-2.13.x</br>3.0.0 - 3.2.x |
| 0.14.x         | 5.6.4, 6.9.2, 7.3.3              | 8          | 2.10.0-2.13.x                   |
| 0.13.x         | 5.6.4, 6.9.2, 7.3.3              | 8          | 2.10.0-2.13.x                   |
| 0.12.x         | 4.10.3, 5.6.4, 6.5               | 8          |                                 |
| 0.11.x         | 4.10.3, 5.6.4, 6.5               | 8          |                                 |
| 0.10.x         | 4.10.3, 5.6.4, 6.0.1             | 8          |                                 |
| 0.9.x          | 4.2, 4.10.3, 5.4.1               | 8          |                                 |
| 0.4.x          | 2.14, 3.0, 4.1                   | 8          |                                 |

1. Scala 3 is supported officially by Gradle from version 7.3 onward 

## Contributing

- This project uses gitflow process. PRs should be done against develop branch
- PRs to develop should be style checked/tested locally by running `./gradlew clean check`

## Building

- refer to github actions used by this repo
