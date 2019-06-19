# gradle crossbuild scala plugin
[![Build Status](https://travis-ci.org/prokod/gradle-crossbuild-scala.svg?branch=master)](https://travis-ci.org/prokod/gradle-crossbuild-scala)[![Automated Release Notes by gren](https://img.shields.io/badge/%F0%9F%A4%96-release%20notes-00B2EE.svg)](https://github-tools.github.io/github-release-notes/)

## Getting the plugin
----------------------
#### Using the plugin DSL:
```groovy
plugins {
    id "com.github.prokod.gradle-crossbuild" version "0.5.0"
}
```  
    
#### Using legacy plugin application
```groovy
buildscript {
    dependencies {
        classpath("com.github.prokod:gradle-crossbuild-scala:0.5.0")
    }
}
```

## Quick start
---------------
### cross building
- Applying the plugin and using its DSL

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

    ```groovy
    dependencies {
        compile ("com.google.protobuf:protobuf-java:$protobufVersion")
        compile ("joda-time:joda-time:$jodaVersion")
        // The question mark is being replaced based on the scala version being built
        compile ("org.scalaz:scalaz_?:$scalazVersion")
    }
    ```

- `gradle tasks`

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

- <code>gradle crossBuild*V211*Jar crossBuild*V212*Jar ...</code>

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
    
#### Notes
- When defining `builds {}`, a short hand convention can be used for default values.
  To be able to use that, `build` item should be named by the following convention, for example:
  `xyz211` is translated to `{ "build": { "scalaVersions": ["2.11"], "name": "xyz211" ... }`
- When using a dependency with '?' in `compile` configuration i.e `compile ("org.scalaz:scalaz_?:$scalazVersion")`, the plugin will try to deduce the scala version for task `build` based on the neighboring dependencies and explicit `scala-library` dependency if any. If it fails to deduce an exception will be thrown.
- test/check tasks are not being cross compiled and they use the default scala version.
  If a user would like to run tests with different scala versions, he needs to change the default one by updating the `scala-library` version dependency in build.gradle

### cross building with publishing  
Leveraging gradle maven-publish plugin for the actual publishing

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
- To update 'maven-publish' cross-build related publications, 'gradle-crossbuild' plugin leverages Gradle's pluginManager.
- Behind the scenes Configuration <code>crossBuild*XYZ*MavenCompileScope</code> is being populated and used with in `pom.withXml {}` block.
    It follows a similar line of thought as `conf2ScopeMappings.addMapping()` in Gradle's maven plugin.
    Beware, Behind the scenes the jars and the publications are decoupled, the logical linkage between a cross built Jar and the publication is made by:
    - Either ensuring `artifactId` matches plugin resolved artifactId
    - Or giving the publication item a name of the following convention <code>crossBuild*XYZ*(MavenPublication)<code> where XYZ is the build name from `builds {}` block followed by `_210`, `_211`, `_212` etc in most cases (except short hand scenario).
- For Gradle 5.x beware that `publishing {}` block does not support deferred configuration anymore and in that case `artifact crossBuild211Jar` should be wrapped in `afterEvaluate {}` block<br>
  Please see Gradle documentation [here](https://docs.gradle.org/current/userguide/publishing_maven.html#publishing_maven:deferred_configuration)

### cross building DSL
`targetVersionItem.archiveAppendix`, `crossBuild.scalaVersionsCatalog`, `crossBuild211XXX` pre defined configurations

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

    configurations = [configurations.someUserConfiguration]
}
```

```groovy
dependencies {
    compile ("com.google.protobuf:protobuf-java:$protobufVersion") 
    compile ("joda-time:joda-time:$jodaVersion")
    // The question mark is being replaced based on the scala version being built
    compile ("org.scalaz:scalaz_?:$scalazVersion")
    
    compileOnly ('org.apache.spark:spark-sql_2.10:1.6.3')
    crossBuildV211CompileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')     // A configuration auto generated by the plugin
}
```

#### Notes
- If `crossBuild.scalaVersionsCatalog` is not defined, a default one will be used (might get outdated).
- Per build item in `builds {}` block, Scala version(s) is set either by explicitly setting `build.scalaVersions` or implicitly through `build.name`.<br>
  See the different [build scenarios](#build_scenarios) for more details
- The dependencies "duo":
  ```groovy
  dependencies {
    compileOnly ('org.apache.spark:spark-sql_2.10:1.6.3')
    crossBuild211CompileOnly ('org.apache.spark:spark-sql_2.11:2.2.1')
  }
  ```
  is resolved as follows: the spark version of the dependency specified for `compile/Only` configuration is the default one for `build`, `test/check` tasks. It is also the one to be used when calling `crossBuild210Jar` and `publishToMavenLocal`.
  The other dependency specified for Scala version 2.11 (`crossBuild211Compile/Only`), will be used only for `crossBuild211Jar` and `publishToMavenLocal`
- The plugin provides pre defined configurations (sourceSets) being used by the matching pre generated Jar tasks:<br>
  crossBuild211Jar -> crossBuild211Compile, crossBuild211CompileOnly, ...
- `configurations = [...]` provides users with the option to tie their own Configuration (SourceSet) with the cross build plugin workings. Needed for instance when specifying dependency within that Configuration on a cross build sub project.
  For instance:
  ```groovy
  apply plugin: 'com.github.prokod.gradle-crossbuild'

  crossBuild {
    ...
    configurations = [
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
### <a name="build_scenarios"></a>`builds {}` -> Gradle SourceSets, Configurations and Tasks
The following table shows some commonly build scenarios expressed through the plugin DSL and how they are actually resolved

| build scenario | SourceSet/s    | Configurations/s | Task/s |
|----------------|----------------|------------------|--------|
|`v210`          | crossBuild*V210* | crossBuild*V210*Compile<br/>crossBuild*V210*CompileOnly<br/>crossBuild*V210*Runtime<br/>crossBuild*V210*CompileClasspath | JavaPlugin -> crossBuild*V210*Java<br/>ScalaPlugin -> crossBuild*V210*Scala<br/>crossBuild*V210*Jar |
|`v211 {`<br/>`    scalaVersions = ['2.11', '2.12']`<br/>`}` | crossBuild*V211_211*, crossBuild*V211_212* | crossBuild*V211_211*Compile<br/>...<br/>crossBuild*V211_212*Compile<br/>... | JavaPlugin -> crossBuild*V211_211*Java, crossBuild*V211_212*Java<br/>ScalaPlugin -> crossBuild*V211_211*Scala, crossBuild*V211_212*Scala<br/>crossBuild*V211_211*Jar, crossBuild*V211_212*Jar |
|`v213 {`<br/>`    scalaVersions = ['2.13']`<br/>`}` | crossBuild*V213* | crossBuild*V213*Compile<br/>... | JavaPlugin -> crossBuild*V213*Java<br/>ScalaPlugin -> crossBuild*V213*Scala<br/>crossBuild*V213*Jar |
|`spark24 {`<br/>`    scalaVersions = ['2.11', '2.12']`<br/>`}` |  crossBuild*Spark24_211*, crossBuild*Spark24_212* | crossBuild*Spark24_211*Compile<br/>...<br/>crossBuild*Spark24_212*Compile<br/>... | JavaPlugin -> crossBuild*Spark24_211*Java, crossBuild*Spark24_212*Java<br/>ScalaPlugin -> crossBuild*Spark24_211*Scala, crossBuild*Spark24_212*Scala<br/>crossBuild*Spark24_211*Jar, crossBuild*Spark24_212*Jar |

### multi-module project
To apply cross building to a multi-module project use one of the following suggested layouts:

#### layout 1
- In the root project build.gradle:
```groovy
buildscript {
    dependencies {
        classpath("com.github.prokod:gradle-crossbuild-scala:0.5.0")
    }
}

allprojects {
    apply plugin: 'base'
    group = 'x.y.z'
    version = '1.0-SNAPSHOT'

    repositories {
        mavenCentral()
    }

    project.pluginManager.withPlugin('com.github.prokod.gradle-crossbuild') {
        crossBuild {

            scalaVersionsCatalog = ['2.11':'2.11.12', '2.12':'2.12.8']

            builds {
                spark240_211
                spark243_212
            }
        }
    }

    project.pluginManager.withPlugin('maven-publish') {
        publishing {
            publications {
                crossBuild211(MavenPublication) {
                    artifact crossBuild211Jar
                }
                crossBuild212(MavenPublication) {
                    artifact crossBuild212Jar
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

#### layout 2
- In the root project build.gradle:
```groovy
buildscript {
    dependencies {
        classpath("com.github.prokod:gradle-crossbuild-scala:0.5.0")
    }
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
            spark240_211
            spark243_212
        }
    }

    publishing {
        publications {
            crossBuild211(MavenPublication) {
                artifact crossBuild211Jar
            }
            crossBuild212(MavenPublication) {
                artifact crossBuild212Jar
            }
        }
    }
}
```

### Supported Gradle versions
|plugin version | Tested Gradle versions |
|---------------|------------------------|
|0.5.0          | 4.2, 4.10.3, 5.4.1     |
