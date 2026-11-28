package com.agent.ai.agent

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApprovalPolicyTest {

    @Test
    fun `reading and searching never ask`() {
        val args = """{"path":"src/Main.kt"}"""
        assertEquals(
            ApprovalDecision.Automatic,
            ApprovalPolicy.decide(AgentToolNames.ReadFile, args, emptySet()),
        )
        assertEquals(
            ApprovalDecision.Automatic,
            ApprovalPolicy.decide(AgentToolNames.Search, """{"query":"main"}""", emptySet()),
        )
        assertEquals(
            ApprovalDecision.Automatic,
            ApprovalPolicy.decide(AgentToolNames.ListDir, """{"path":"."}""", emptySet()),
        )
    }

    @Test
    fun `writing, editing and running a command ask first`() {
        assertEquals(
            ApprovalDecision.Ask,
            ApprovalPolicy.decide(AgentToolNames.WriteFile, """{"path":"a.txt","content":"x"}""", emptySet()),
        )
        assertEquals(
            ApprovalDecision.Ask,
            ApprovalPolicy.decide(AgentToolNames.RunCommand, """{"command":"ls -la"}""", emptySet()),
        )
    }

    @Test
    fun `an always allowed tool skips the question`() {
        assertEquals(
            ApprovalDecision.Automatic,
            ApprovalPolicy.decide(
                AgentToolNames.WriteFile,
                """{"path":"a.txt","content":"x"}""",
                setOf(AgentToolNames.WriteFile),
            ),
        )
    }

    @Test
    fun `the always ask list cannot be skipped, even when always allowed`() {
        val decision = ApprovalPolicy.decide(
            AgentToolNames.RunCommand,
            """{"command":"git push origin main"}""",
            setOf(AgentToolNames.RunCommand),
        )
        assertEquals(ApprovalDecision.AlwaysAsk, decision)
        assertTrue(ApprovalPolicy.isAlwaysAsk(AgentToolNames.RunCommand, """{"command":"git push"}"""))
    }

    @Test
    fun `a destructive command is always ask`() {
        assertTrue(ApprovalPolicy.isAlwaysAsk(AgentToolNames.RunCommand, """{"command":"rm -rf /tmp/x"}"""))
        assertTrue(ApprovalPolicy.isAlwaysAsk(AgentToolNames.RunCommand, """{"command":"cd /.. ; ls"}"""))
        assertTrue(ApprovalPolicy.isAlwaysAsk(AgentToolNames.RunCommand, """{"command":"sudo reboot"}"""))
        assertTrue(!ApprovalPolicy.isAlwaysAsk(AgentToolNames.RunCommand, """{"command":"ls src"}"""))
    }

    @Test
    fun `the summary shows the real path or command, not a description`() {
        assertEquals(
            "src/Main.kt",
            AgentTools.summary(AgentToolNames.ReadFile, """{"path":"src/Main.kt"}"""),
        )
        assertEquals(
            "ls -la",
            AgentTools.summary(AgentToolNames.RunCommand, """{"command":"ls -la"}"""),
        )
    }
}

class ToolExecutorTest {

    private lateinit var root: File
    private lateinit var backups: File
    private lateinit var executor: ToolExecutor

    @Before
    fun setUp() {
        root = Files.createTempDirectory("tools").toFile()
        backups = Files.createTempDirectory("backups").toFile()
        executor = ToolExecutor(ProjectSandbox(root), backups)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        backups.deleteRecursively()
    }

    @Test
    fun `write then read returns the content`() = runTest {
        val write = executor.run(AgentToolNames.WriteFile, """{"path":"a.txt","content":"hello"}""")
        assertTrue(write.ok)

        val read = executor.run(AgentToolNames.ReadFile, """{"path":"a.txt"}""")
        assertTrue(read.ok)
        assertEquals("hello", read.output)
    }

    @Test
    fun `an edit keeps a backup so it can be reverted`() = runTest {
        executor.run(AgentToolNames.WriteFile, """{"path":"a.txt","content":"one two"}""")
        val edit = executor.run(
            AgentToolNames.EditFile,
            """{"path":"a.txt","old":"two","new":"three"}""",
        )

        assertTrue(edit.ok)
        assertNotNull(edit.backupPath)
        assertTrue(edit.diff.contains("-one two"))
        assertTrue(edit.diff.contains("+one three"))
        assertEquals("one three", File(root, "a.txt").readText())
        assertEquals("one two", File(edit.backupPath!!).readText())
    }

    @Test
    fun `a path outside the project fails with a plain reason`() = runTest {
        val result = executor.run(AgentToolNames.ReadFile, """{"path":"../etc/passwd"}""")
        assertTrue(!result.ok)
        assertTrue(result.output.contains("outside the project"))
    }

    @Test
    fun `listing shows folders with a slash`() = runTest {
        executor.run(AgentToolNames.WriteFile, """{"path":"src/a.txt","content":"x"}""")
        val result = executor.run(AgentToolNames.ListDir, """{"path":"."}""")
        assertTrue(result.output.contains("src/"))
    }

    @Test
    fun `an unknown tool is reported, never crashed on`() = runTest {
        val result = executor.run("delete_everything", "{}")
        assertTrue(!result.ok)
        assertTrue(result.output.contains("Unknown tool"))
    }
}
