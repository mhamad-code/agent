package com.agent.ai.agent

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProjectSandboxTest {

    private lateinit var root: File
    private lateinit var sandbox: ProjectSandbox

    @Before
    fun setUp() {
        root = Files.createTempDirectory("sandbox").toFile()
        sandbox = ProjectSandbox(root)
        sandbox.write("src/Main.kt", "fun main() {\n    println(\"hi\")\n}\n")
        sandbox.write("README.md", "hello project\n")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a relative path resolves inside the project`() {
        val file = sandbox.resolve("src/Main.kt")
        assertEquals("src/Main.kt", sandbox.relative(file))
        assertTrue(file.isFile)
    }

    @Test(expected = SandboxException::class)
    fun `a parent traversal is refused`() {
        sandbox.resolve("../outside.txt")
    }

    @Test(expected = SandboxException::class)
    fun `an absolute path outside the project is refused`() {
        val outside = Files.createTempDirectory("outside").toFile()
        try {
            sandbox.resolve(File(outside, "x.txt").absolutePath)
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test(expected = SandboxException::class)
    fun `a symlink that points outside is refused`() {
        val outside = Files.createTempDirectory("outside").toFile()
        outside.resolve("secret.txt").writeText("top secret")
        try {
            Files.createSymbolicLink(
                File(root, "escape").toPath(),
                outside.toPath(),
            )
            sandbox.resolve("escape/secret.txt")
        } finally {
            outside.deleteRecursively()
        }
    }

    @Test
    fun `edit replaces one exact occurrence`() {
        sandbox.edit("README.md", "hello", "goodbye")
        assertEquals("goodbye project\n", sandbox.read("README.md"))
    }

    @Test(expected = SandboxException::class)
    fun `edit refuses a string that appears twice`() {
        sandbox.write("twice.txt", "x\nx\n")
        sandbox.edit("twice.txt", "x", "y")
    }

    @Test(expected = SandboxException::class)
    fun `edit refuses a string that is missing`() {
        sandbox.edit("README.md", "nothing like this", "y")
    }

    @Test
    fun `write creates folders`() {
        sandbox.write("a/b/c.txt", "deep")
        assertTrue(File(root, "a/b/c.txt").isFile)
    }

    @Test
    fun `list puts folders first and marks them`() {
        sandbox.write("src/Other.kt", "")
        val entries = sandbox.list(".")
        assertEquals("src", entries.first().name)
        assertTrue(entries.first().directory)
    }

    @Test
    fun `search reports the file and the line number`() {
        val hits = sandbox.search("println")
        assertEquals(1, hits.size)
        assertEquals("src/Main.kt", hits[0].path)
        assertEquals(2, hits[0].line)
    }

    @Test
    fun `a large file is truncated with a note`() {
        val builder = StringBuilder()
        repeat(4_000) { builder.append("0123456789").append('\n') }
        sandbox.write("big.txt", builder.toString())
        val text = sandbox.read("big.txt", maxBytes = 1_000)
        assertTrue(text.contains("truncated"))
        assertTrue(text.length < 2_000)
    }

    @Test
    fun `the diff shows the changed lines only`() {
        val before = "one\ntwo\nthree\n"
        val after = "one\nTWO\nthree\n"
        val diff = AgentTools.unifiedDiff(before, after)
        assertTrue(diff.contains("-two"))
        assertTrue(diff.contains("+TWO"))
        assertTrue(!diff.contains("+one"))
    }
}
