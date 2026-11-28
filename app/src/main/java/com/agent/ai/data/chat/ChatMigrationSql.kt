package com.agent.ai.data.chat

/**
 * The version 1 to version 2 migration, as plain SQL so it can be executed by Room on a
 * device and by a JVM SQLite test alike.
 */
object ChatMigrationSql {

    val statements: List<String> = listOf(
        "ALTER TABLE conversations ADD COLUMN activeLeafId TEXT",
        "ALTER TABLE conversations ADD COLUMN titleAuto INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE conversations ADD COLUMN systemPrompt TEXT",
        "ALTER TABLE conversations ADD COLUMN compaction TEXT",

        "ALTER TABLE messages ADD COLUMN parentId TEXT",
        "ALTER TABLE messages ADD COLUMN parts TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE messages ADD COLUMN errorDetail TEXT",
        "ALTER TABLE messages ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE messages ADD COLUMN summary INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE messages ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE messages ADD COLUMN truncated INTEGER NOT NULL DEFAULT 0",
        "CREATE INDEX IF NOT EXISTS `index_messages_parentId` ON `messages` (`parentId`)",

        "ALTER TABLE models ADD COLUMN vision INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE models ADD COLUMN reasoning INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE models ADD COLUMN toolCalling INTEGER NOT NULL DEFAULT 0",

        "CREATE TABLE IF NOT EXISTS `drafts` (`conversationId` TEXT NOT NULL, " +
            "`text` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`conversationId`))",
        "CREATE TABLE IF NOT EXISTS `memories` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, " +
            "`scope` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `slash_commands` (`id` TEXT NOT NULL, " +
            "`name` TEXT NOT NULL, `prompt` TEXT NOT NULL, `description` TEXT NOT NULL, " +
            "PRIMARY KEY(`id`))",
        "CREATE VIRTUAL TABLE IF NOT EXISTS `message_fts` USING FTS4(" +
            "`messageId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `text` TEXT NOT NULL)",

        // Every message points at the message before it in the same conversation.
        "UPDATE `messages` SET `parentId` = (" +
            "SELECT `m2`.`id` FROM `messages` `m2` " +
            "WHERE `m2`.`conversationId` = `messages`.`conversationId` " +
            "AND `m2`.`rowid` < `messages`.`rowid` " +
            "ORDER BY `m2`.`rowid` DESC LIMIT 1)",
        "UPDATE `conversations` SET `activeLeafId` = (" +
            "SELECT `m`.`id` FROM `messages` `m` " +
            "WHERE `m`.`conversationId` = `conversations`.`id` " +
            "ORDER BY `m`.`rowid` DESC LIMIT 1)",
                    "INSERT OR REPLACE INTO `message_fts` (`messageId`, `conversationId`, `text`) " +
            "SELECT `id`, `conversationId`, `text` FROM `messages`",
    )

    /**
     * Version 3 adds the agent activity log. Messages already carry their tool calls inside
     * the `parts` column, so no existing row is rewritten.
     */
    val activityStatements = listOf(
        "CREATE TABLE IF NOT EXISTS `tool_activity` (`id` TEXT NOT NULL, " +
            "`at` INTEGER NOT NULL, `tool` TEXT NOT NULL, `input` TEXT NOT NULL, " +
            "`result` TEXT NOT NULL, `approvedBy` TEXT NOT NULL, `ok` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_tool_activity_at` ON `tool_activity` (`at`)",
    )
}
