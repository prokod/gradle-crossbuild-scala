package com.github.prokod.gradle.crossbuild

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

abstract class CrossBuildGradleRunnerSpec extends Specification {

    @Rule
    final TemporaryFolder dir = new TemporaryFolder()

    def testMavenCentralAccess() {
        URL u = new URL ( "https://repo1.maven.org/maven2/")
        HttpURLConnection huc =  ( HttpURLConnection )  u.openConnection ()
        huc.setRequestMethod ("HEAD")
        huc.connect ()
        huc.getResponseCode() == HttpURLConnection.HTTP_OK
    }

    protected File directory(String path) {
        new File(dir.root, path).with {
            mkdirs()
            it
        }
    }

    protected File file(String path) {
        def splitted = path.split(File.separator)
        def directory = splitted.size() > 1 ? directory(splitted[0..-2].join(File.separator)) : dir.root
        def file = new File(directory, splitted[-1])
        file.createNewFile()
        file
    }

    protected File findFile(String path) {
        def proot = Paths.get(dir.root.absolutePath)
        if (Files.isDirectory(proot)) {
            proot.toFile().traverse { f ->
                System.out.println(f.toPath().toString())
            }
        }
        if (path.contains('*')) {
            File found = null
            // Create a Pattern object
            def cpattern = "^" + (path.startsWith(File.separator) ? "" : path.startsWith('*') ? "" : ".*?") + path.replaceAll("\\.", "\\\\.").replaceAll("\\*", ".*?") + "\$"
            System.out.println(cpattern)
            def pattern = Pattern.compile(cpattern);
            if (Files.isDirectory(proot)) {
                proot.toFile().traverse { f -> def m = pattern.matcher(f.toPath().toString()); if (m.find()) found = f }
                found
            } else {
                null
            }
        } else {
            new File(dir.root, path)
        }
    }

    protected boolean fileExists(String path) {
        findFile(path) != null
    }

    /**
     * Prior to Gradle 5.0, the publishing {} block was (by default) implicitly treated as if all the logic inside it
     * was executed after the project is evaluated.
     * This behavior caused quite a bit of confusion and was deprecated in Gradle 4.8, because it was the only block
     * that behaved that way.
     */
    protected boolean publishTaskSupportingDeferredConfiguration(String gradleVersion) {
        def tokens = gradleVersion.tokenize('.').toList()
        def major = tokens.head().toInteger()
        major <= 4
    }
}
