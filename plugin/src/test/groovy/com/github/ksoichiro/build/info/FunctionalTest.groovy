package com.github.ksoichiro.build.info

import org.ajoberstar.grgit.Grgit
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.util.jar.JarFile

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class FunctionalTest {
    private static final String PLUGIN_ID = 'com.github.ksoichiro.build.info'

    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder()
    File rootDir
    File buildFile
    List<File> pluginClasspath
    Grgit grgit

    @Before
    void setup() {
        rootDir = testProjectDir.root
        if (!rootDir.exists()) {
            rootDir.mkdir()
        }
        buildFile = new File(rootDir, "build.gradle")

        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        pluginClasspath = pluginClasspathResource.readLines()
            .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
            .collect { new File(it) }

        new File(rootDir, ".gitignore").text = """\
            |.gradle/
            |/build/
            |""".stripMargin().stripIndent()
        def pkg = new File("${rootDir}/src/main/java/hello")
        pkg.mkdirs()
        new File(pkg, "App.java").text = """\
            |package hello;
            |public class App {
            |    public static void main(String[] args) {
            |        System.out.println("Hello!");
            |    }
            |}
            |""".stripMargin().stripIndent()
        grgit = Grgit.init(dir: rootDir.path)
        grgit.add(patterns: ['.gitignore', 'build.gradle', 'src/main/java/hello/App.java'])
        grgit.commit(message: 'Initial commit.')
    }

    @Test
    public void generateBuildInfo() {
        def buildFileContent = """\
            |plugins {
            |    id '${PLUGIN_ID}'
            |}
            |apply plugin: 'java'
            |archivesBaseName = 'foo'
            |""".stripMargin().stripIndent()
        buildFile.text = buildFileContent

        def result = GradleRunner.create()
            .withProjectDir(rootDir)
            .withArguments("build")
            .withPluginClasspath(pluginClasspath)
            .build()

        assertEquals(result.task(":build").getOutcome(), TaskOutcome.SUCCESS)

        File jarFile = new File("${rootDir}/build/libs/foo.jar")
        assertTrue(jarFile.exists())
        JarFile jar = new JarFile(jarFile)
        def manifestAttrs = jar.manifest.mainAttributes
        assertEquals(grgit.head().abbreviatedId, manifestAttrs.getValue('Git-Commit'))
    }
}