package com.agent.ai.data.chat

import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the version 1 to 2 migration on a real SQLite database.
 *
 * The version 1 tables are created with the schema Room generated for them, then the same
 * statements Room executes on the device are applied. The test proves that the flat list
 * becomes a chain and that every new table and the full-text index are in place.
 *
 * The on-device `MigrationTestHelper` check would also need the version 1 exported schema
 * file, which predates schema export; this test covers the SQL itself.
 */
class MigrationSqlTest {

    private lateinit var db: Connection

    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`modelId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, " +
            "`role` TEXT NOT NULL, `text` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`modelId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `errorKind` TEXT, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)",
        "CREATE TABLE IF NOT EXISTS `models` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`free` INTEGER NOT NULL, `contextLength` INTEGER NOT NULL, `description` TEXT NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )

    @Before
    fun setUp() {
        Class.forName("org.sqlite.JDBC")
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { statement ->
            v1Schema.forEach { statement.execute(it) }
            statement.execute(
                "INSERT INTO conversations VALUES ('c1', 'Old chat', 'kilo-auto/free', 1, 9)"
            )
            statement.execute(
                "INSERT INTO messages VALUES ('m1', 'c1', 'user', 'hello there', 'done', 'm', 1, NULL)"
            )
            statement.execute(
                "INSERT INTO messages VALUES ('m2', 'c1', 'assistant', 'hi', 'done', 'm', 2, NULL)"
            )
            statement.execute(
                "INSERT INTO messages VALUES ('m3', 'c1', 'user', 'again', 'done', 'm', 3, NULL)"
            )
            statement.execute(
                "INSERT INTO models VALUES ('m', 'Model', 1, 8192, '', 0)"
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun migrate() {
        db.createStatement().use { statement ->
            ChatMigrationSql.statements.forEach { statement.execute(it) }
        }
    }

    @Test
    fun `every message points at the one before it`() {
        migrate()
        val parents = mutableMapOf<String, String?>()
        db.createStatement().use { statement ->
            statement.executeQuery("SELECT id, parentId FROM messages ORDER BY rowid").use { rows ->
                while (rows.next()) parents[rows.getString(1)] = rows.getString(2)
            }
        }
        assertEquals(null, parents["m1"])
        assertEquals("m1", parents["m2"])
        assertEquals("m2", parents["m3"])
    }

    @Test
    fun `the active leaf becomes the last message`() {
        migrate()
        db.createStatement().use { statement ->
            statement.executeQuery("SELECT activeLeafId, titleAuto, pinned FROM conversations").use {
                it.next()
                assertEquals("m3", it.getString(1))
                assertEquals(1, it.getInt(2))
                assertEquals(0, it.getInt(3))
            }
        }
    }

    @Test
    fun `the new columns and tables exist with safe defaults`() {
        migrate()
        db.createStatement().use { statement ->
            statement.executeQuery("SELECT parts, archived, summary, deleted, truncated FROM messages")
                .use {
                    it.next()
                    assertEquals("", it.getString(1))
                    assertEquals(0, it.getInt(2))
                    assertEquals(0, it.getInt(3))
                    assertEquals(0, it.getInt(4))
                    assertEquals(0, it.getInt(5))
                }
            listOf("drafts", "memories", "slash_commands", "message_fts").forEach { table ->
                statement.executeQuery("SELECT COUNT(*) FROM $table").use { rows ->
                    assertTrue(rows.next())
                }
            }
            statement.executeQuery("SELECT vision, reasoning, toolCalling FROM models").use {
                it.next()
                assertEquals(0, it.getInt(1))
                assertEquals(0, it.getInt(2))
                assertEquals(0, it.getInt(3))
            }
        }
    }

    @Test
    fun `the full text index is filled, so old chats are searchable`() {
        migrate()
        db.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM message_fts").use {
                it.next()
                assertEquals(3, it.getInt(1))
            }
            statement.executeQuery(
                "SELECT messageId FROM message_fts WHERE message_fts MATCH 'hello'"
            ).use { rows ->
                assertTrue(rows.next())
                assertEquals("m1", rows.getString(1))
            }
        }
    }
}
