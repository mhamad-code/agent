package com.agent.ai.data.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The message tree arrived in version 2. The migration turns the flat list saved by
 * version 1 into a chain: every message's parent is the message before it, and each
 * conversation's active leaf is its last message.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ChatMigrationSql.statements.forEach(db::execSQL)
    }
}

/** Version 3 adds the table that records every tool call the agent made. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ChatMigrationSql.activityStatements.forEach(db::execSQL)
    }
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        DraftEntity::class,
        ModelEntity::class,
        MemoryEntity::class,
        SlashCommandEntity::class,
        MessageFtsEntity::class,
        ToolActivityEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: ChatDatabase? = null

        fun get(context: Context): ChatDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java,
                "agent_chat.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
