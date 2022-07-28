package com.github.prokod.gradle.crossbuild

import org.apache.tools.ant.filters.ReplaceTokens
import org.w3c.dom.Node
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.Diff
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.util.Predicate
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.io.File
import java.util.regex.Pattern

abstract class CrossBuildGradleRunnerSpec extends Specification {

    @TempDir
    Path dir

    def testMavenCentralAccess() {
        URL u = new URL ( "https://repo1.maven.org/maven2/")
        HttpURLConnection huc =  ( HttpURLConnection )  u.openConnection ()
        huc.setRequestMethod ("HEAD")
        huc.connect ()
        huc.getResponseCode() == HttpURLConnection.HTTP_OK
    }

    protected File directory(String path) {
        dir.resolve(path).with {
            Files.createDirectories(it).toFile()
        }
    }

    protected File file(String path) {
        def splitted = path.split(File.separator)
        def directory = splitted.size() > 1 ? directory(splitted[0..-2].join(File.separator)) : dir.toFile()
        def file = new File(directory, splitted[-1])
        file.createNewFile()
        file
    }

    protected File findFile(String path) {
        if (Files.isDirectory(dir)) {
            File found = null
            if (path.contains('*')) {
                // Create a Pattern object
                def cpattern = "^" + (path.startsWith(File.separator) ? "" : path.startsWith('*') ? "" : ".*?") + path.replaceAll("\\.", "\\\\.").replaceAll("\\*", ".*?") + "\$"
                def pattern = Pattern.compile(cpattern)

                dir.toFile().traverse { f ->
                    def fPath = f.toPath().toString()
                    def m = pattern.matcher(fPath)
                    if (m.find()) {
                        found = f
                    }
                }
            } else {
                dir.toFile().traverse { f ->
                    def fPath = f.toPath().toString()
                    if (fPath == path) {
                        found = f
                    }
                }
            }
            found
        } else {
            null
        }
    }

    protected boolean fileExists(String path) {
        findFile(path) != null
    }

    protected boolean fileExists(Path path) {
        findFile(path.toString()) != null
    }

    protected String loadResourceAsText(Map tokens, String path) {
        def resource = this.getClass().getResource(path)

        BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream()))

        def replaceTokens = new ReplaceTokens(reader)
        def tokenObjects = tokens.collect { entry ->
            def tkn = new ReplaceTokens.Token()
            tkn.key = entry.key
            tkn.value = entry.value
            tkn
        }
        tokenObjects.each { tkn -> replaceTokens.addConfiguredToken(tkn) }

        replaceTokens.text
    }

    protected String loadResourceAsText(String path) {
        loadResourceAsText([:], path)
    }

    protected Diff pomDiffFor(String expected, File actual) {
        DiffBuilder.compare(Input.fromString(expected))
                .withTest(Input.fromFile(actual))
                .ignoreWhitespace()
                .ignoreComments()
                .normalizeWhitespace()
                .withNodeFilter(new Predicate<Node>() {
                    @Override
                    boolean test(Node toTest) {
                        ['dependency', 'artifactId', 'groupId', 'version', 'scope'].contains(toTest.nodeName)
                    }
                })
                .withNodeMatcher(new DefaultNodeMatcher(
                        ElementSelectors.selectorForElementNamed("dependency",
                                ElementSelectors.byNameAndText)))
                .build()
    }
}
