## gradle crossbuild scala plugin
- - -
### Getting the plugin
- - -
```groovy
buildscript {
    dependencies {
        classpath("com.github.prokod:gradle-crossbuild-scala:0.1.0")
    }
}
```

### Quick start
- - -
#### cross building with publishing  
Leveraging gradle maven-publish plugin for the actual publishing

```groovy
import com.github.prokod.gradle.crossbuild.model.*

apply plugin: 'com.github.prokod.gradle-crossbuild'

model {
    crossBuild {
        targetVersions {
            v211(ScalaVer) {
                value = '2.11'
                archiveAppendix = "_?_$spark20SparkVersion" // By default the value is "_?" 
                                                            // In the default case will yield '_2.11')
            }
        }
    }
    
    // 'maven-publish' plugin usage for publishing crossbuild artifacts
    publishing {
        publications {
            crossBuild211(MavenPublication) {                                  // Create a publication
                groupId = project.group                                        // groupId is needed
                artifactId = $.crossBuild.targetVersions.v211.artifactId       // artifactId is also needed and can be assigned for convenience from the crossbuild plugin
                artifact $.tasks.crossBuild211Jar                              // actual artifact for this publication as a Jar task from crossbuild plugin
                pom.withXml {                                                  // As of now this is needed to have a populated pom.xml for the cross built artifact
                    def dependenciesNode = asNode().appendNode("dependencies")

                    if (dependenciesNode != null) {
                        configurations.crossBuild211MavenCompileScope.allDependencies.each { dep ->  // crossBuild211MavenCompileScope configuration hold all dependencies for the artifact (see notes).
                            def dependencyNode = dependenciesNode.appendNode('dependency')
                            dependencyNode.appendNode('groupId', dep.group)
                            dependencyNode.appendNode('artifactId', dep.name)
                            dependencyNode.appendNode('version', dep.version)
                            dependencyNode.appendNode('scope', 'runtime')
                        }
                    }
                }
            }
        }
    }
```

```groovy
dependencies {
    compile ("com.google.protobuf:protobuf-java:$protobufVersion") 
    compile ("joda-time:joda-time:$jodaVersion")
    compile ("org.scalaz:scalaz_?:$scalazVersion")                                                    // The question mark is being replaced based on the scala version being built (see notes).
    compileOnly ("org.apache.spark:spark-sql_$spark16ScalaVersion:$spark16SparkVersion")
    crossBuild211CompileOnly ("org.apache.spark:spark-sql_$spark20ScalaVersion:$spark20SparkVersion") // A configuration supplied by the plugin (see notes)
}
```

### Notes
- Configuration crossBuild2XXMavenCompileScope should be used with in `pom.withXml` block.  
It follows a similar line of thought as `conf2ScopeMappings.addMapping()` in Gradle's maven plugin.
- when using a dependency with '?' in compile configuration, the plugin will try to deduce the scala version for task build
based on the neighboring dependencies. If it fails to deduce an exception will be thrown.
- The plugin provides pre defined configurations being used by the matching pre generated Jar tasks:  
crossBuild211Jar -> crossBuild211Compile, crossBuild211CompileOnly
- 