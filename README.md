# gradle crossbuild scala plugin
[![Build Status](https://travis-ci.org/prokod/gradle-crossbuild-scala.svg?branch=master)](https://travis-ci.org/prokod/gradle-crossbuild-scala)

## Getting the plugin
----------------------
```groovy
buildscript {
    dependencies {
        classpath("com.github.prokod:gradle-crossbuild-scala:0.4.4")
    }
}
```

## Quick start
---------------
### cross building
- Applying the plugin and using its DSL

    ```groovy
    import com.github.prokod.gradle.crossbuild.model.*

    apply plugin: 'com.github.prokod.gradle-crossbuild'

    model {
        crossBuild {
            targetVersions {
                v210(ScalaVer)
                v211(ScalaVer)
            }
        }
    }
    ```

    ```groovy
    dependencies {
        compile ("com.google.protobuf:protobuf-java:$protobufVersion")
        compile ("joda-time:joda-time:$jodaVersion")
        // The question mark is being replaced based on the scala version being built //(3)
        compile ("org.scalaz:scalaz_?:$scalazVersion")
    }
    ```

- `gradle tasks`

    `gradle-crossbuild` plugin adds the following user faced tasks to the project `crossBuild210Classes`, `crossBuild210Jar`, `crossBuild211Classes`, `crossBuild211Jar`

    ```sh
    > ./gradlew tasks

    ------------------------------------------------------------
    All tasks runnable from project :lib
    ------------------------------------------------------------

    Build tasks
    -----------
    assemble - Assembles the outputs of this project.
    build - Assembles and tests this project.
    buildDependents - Assembles and tests this project and all projects that depend on it.
    buildNeeded - Assembles and tests this project and all projects it depends on.
    classes - Assembles main classes.
    clean - Deletes the build directory.
    crossBuild210Classes - Assembles cross build210 classes.
    crossBuild210Jar - Assembles a jar archive containing 210 classes
    crossBuild211Classes - Assembles cross build211 classes.
    crossBuild211Jar - Assembles a jar archive containing 211 classes
    jar - Assembles a jar archive containing the main classes.
    testClasses - Assembles test classes.
    ...

    Publishing tasks
    ----------------
    publish - Publishes all publications produced by this project.
    publishToMavenLocal - Publishes all Maven publications produced by this project to the local Maven cache.
    ```

- `gradle crossBuild210Jar crossBuild211Jar`

    ```sh
    > ./gradlew crossBuild210Jar crossBuild211Jar
    ...
    Tasks to be executed: [task ':compileCrossBuild210Java', task ':compileCrossBuild210Scala', task ':processCrossBuild210Resources', task ':crossBuild210Classes', task ':crossBuild210Jar', task ':compileCrossBuild211Java', task ':compileCrossBuild211Scala', task ':processCrossBuild211Resources', task ':crossBuild211Classes', task ':crossBuild211Jar']
    ...
    :crossBuild210Jar (Thread[Connection worker,5,main]) completed. Took 0.04 secs.
    ...
    :crossBuild211Jar (Thread[Connection worker,5,main]) completed. Took 0.007 secs.

    > ls ./build/libs
    lib_2.10.jar  lib_2.11.jar
    ```
    
#### Notes
- When defining `targetVersions`, a short hand convention can be used for default values.
  To be able to use that, `targetVersion` item should be named by the following convention, for example:
  `[v|V]210(ScalaVer)` is translated to `{ "targetVersion": { "value": "2.10", "name": "[v|V]210" }`
- When using a dependency with '?' in `compile` configuration i.e `compile ("org.scalaz:scalaz_?:$scalazVersion")`, the plugin will try to deduce the scala version for task `build` based on the neighboring dependencies and explicit `scala-library` dependency if any. If it fails to deduce an exception will be thrown.

### cross building with publishing  
Leveraging gradle maven-publish plugin for the actual publishing

```groovy
import com.github.prokod.gradle.crossbuild.model.*

apply plugin: 'com.github.prokod.gradle-crossbuild'

model {
    crossBuild {
        targetVersions {
            v211(ScalaVer)
        }
    }

    // 'maven-publish' plugin usage for publishing crossbuild artifacts
    publishing {
        publications {
            // Create a publication
            crossBuild211(MavenPublication) {
                // groupId is needed
                groupId = project.group
                // artifactId is also needed and can be assigned for convenience from the crossbuild plugin
                artifactId = $.crossBuild.targetVersions.v211.artifactId
                // actual artifact for this publication as a Jar task from crossbuild plugin
                artifact $.tasks.crossBuild211Jar
            }
        }
    }
}
...
```

- `gradle tasks`

    Notice that `gradle-crossbuild` plugin now adds the following publish related user faced tasks to the project:

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
    publishCrossBuild211PublicationToMavenRepository - Publishes Maven publication 'crossBuild211' to Maven repository 'maven'.
    publishToMavenLocal - Publishes all Maven publications produced by this project to the local Maven cache.
    ```

- `gradle publishToMavenLocal`

    ```sh
    > ./gradlew publishToMavenLocal
    ...
    Tasks to be executed: [task ':compileCrossBuild211Java', task ':compileCrossBuild211Scala', task ':processCrossBuild211Resources', task ':crossBuild211Classes', task ':crossBuild211Jar', task ':generatePomFileForCrossBuild211Publication', task ':publishCrossBuild211PublicationToMavenLocal', task ':publishToMavenLocal']
    ...
    ```

#### Notes
- To tap into the lifecycle of 'maven-publish' plugin it is being applied from within crossbuild-scala plugins.
When using this plugin and applying 'maven-publish' plugin explicitly in build.gradle for the same module, an Exception will be raised.
- Behind the scenes Configuration crossBuild2XXMavenCompileScope is being populated and used with in `pom.withXml` block.
    It follows a similar line of thought as `conf2ScopeMappings.addMapping()` in Gradle's maven plugin.
    Beware, Behind the scenes the jars and the publications are decoupled, the logical linkage between a cross built Jar and the publication is made by:
    - Either ensuring `artifactId = $.crossBuild.targetVersions.v211.artifactId` in the model
    - Or giving the publication item a name of the following convention `crossBuildXXX(MavenPublication)` where XXX can be 210, 211, 212 etc.
- test/check tasks are not being cross compiled and they use the default scala version.
  If a user would like to run tests with different scala versions, he needs to change the default one by updating the `scala-library` version dependency in build.gradle

### cross building DSL
`targetVersionItem.archiveAppendix`, `crossBuild.scalaVersions`, `crossBuild211XXX` pre defined configurations

```groovy
import com.github.prokod.gradle.crossbuild.model.*

apply plugin: 'com.github.prokod.gradle-crossbuild'

model {
    crossBuild {
        targetVersions {
            v210(ScalaVer) {
                ...
            }
            v211(ScalaVer) {
                value = '2.11'
                archiveAppendix = "-$spark20SparkVersion_?" // By default the value is "_?" 
                                                            // In the default case will yield '_2.11')
            }
        }

        scalaVersions = ['2.10': '2.10.6', '2.11': '2.11.11', ...]

        dependencyResolution {
            includes = [configurations.someUserConfiguration]
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
    
    compileOnly ('org.apache.spark:spark-sql_2.10:1.6.3')
    crossBuild211CompileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')     // A configuration supplied by the plugin
}
```

#### Notes
- If `crossBuild.scalaVersions` catalog is not defined a default one will be used (might get outdated).
- The dependecies "duo":
  ```groovy
  dependencies {
    compileOnly ('org.apache.spark:spark-sql_2.10:1.6.3')
    crossBuild211CompileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')
  }
  ```
  is resolved as follows: the spark version of the dependency specified for `compile/Only` configuartion is the default one for `build`, `test/check` tasks. It is also the one to be use when calling `crossBuild210Jar` and `publishToMavenLocal`.
  The other dependency specified for Scala version 2.11 (`crossBuild211Compile/Only`), will be used only for `crossBuild211Jar` and `publishToMavenLocal`
- The plugin provides pre defined configurations (sourceSets) being used by the matching pre generated Jar tasks:
crossBuild211Jar -> crossBuild211Compile, crossBuild211CompileOnly, ...
- `dependencyResolution.includes = [...]` provides users with the option to tie their own Configuration (SourceSet) with the cross build plugin workings. Needed for instance when specifying dependency within that Configuration on a cross build sub project.
  For instance:
  ```groovy
  apply plugin: 'com.github.prokod.gradle-crossbuild'

  crossBuild {
    ...
    dependencyResolution.includes = [
        configurations.integrationTestCompileClasspath,
        configurations.integrationTestRuntimeClasspath
    ]
  }
  ...
  sourceSets {
    integrationTest {
        ...
    }
  }

  configurations {
    integrationTestCompile.extendsFrom testCompile
    ...
  }

  dependencies {
    ...
  }
  ```
