package com.bryce.discord.services;

import java.sql.Connection;

public class DatabaseInitializer {
    public static void initializeDatabase() {
        try (Connection conn = DatabaseManager.getConnection()) {

            String warningsTable = "CREATE TABLE IF NOT EXISTS warnings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "guildId TEXT," +
                    "userId TEXT NOT NULL," +
                    "moderatorId TEXT," +
                    "reason TEXT," +
                    "timestamp BIGINT" +
                    ")";

            String analyticsTable = "CREATE TABLE IF NOT EXISTS moderation_analytics (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "guildId TEXT," +
                    "action TEXT NOT NULL," +
                    "moderatorId TEXT," +
                    "moderatorName TEXT," +
                    "targetId TEXT," +
                    "targetName TEXT," +
                    "reason TEXT," +
                    "timestamp BIGINT," +
                    "duration BIGINT," +
                    "count INTEGER" +
                    ")";

            String settingsTable = "CREATE TABLE IF NOT EXISTS bot_settings (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT" +
                    ")";

            String guildsTable = "CREATE TABLE IF NOT EXISTS guilds (" +
                    "guildId TEXT PRIMARY KEY," +
                    "guildName TEXT," +
                    "joinedTimestamp BIGINT" +
                    ")";

            String commandLogsTable = "CREATE TABLE IF NOT EXISTS command_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "userId TEXT," +
                    "userName TEXT," +
                    "commandName TEXT," +
                    "timestamp BIGINT" +
                    ")";

            String guildSettingsTable = "CREATE TABLE IF NOT EXISTS guild_settings (" +
                    "guildId TEXT NOT NULL," +
                    "key TEXT NOT NULL," +
                    "value TEXT," +
                    "PRIMARY KEY (guildId, key)" +
                    ")";

            String botRolesTable = "CREATE TABLE IF NOT EXISTS bot_roles (" +
                    "roleId TEXT PRIMARY KEY," +
                    "roleType TEXT NOT NULL" +
                    ")";

            String mutesTable = "CREATE TABLE IF NOT EXISTS mutes (" +
                    "guildId TEXT NOT NULL," +
                    "userId TEXT NOT NULL," +
                    "unmuteTime BIGINT NOT NULL," +
                    "PRIMARY KEY (guildId, userId)" +
                    ")";

            String messageLogsTable = "CREATE TABLE IF NOT EXISTS message_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "guildId TEXT," +
                    "channelId TEXT," +
                    "messageId TEXT," +
                    "userId TEXT," +
                    "content TEXT," +
                    "action TEXT," +
                    "timestamp BIGINT" +
                    ")";

            String generalLogsTable = "CREATE TABLE IF NOT EXISTS general_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "guildId TEXT," +
                    "userId TEXT," +
                    "eventType TEXT," +
                    "details TEXT," +
                    "timestamp BIGINT" +
                    ")";

            String voiceChannelsTable = "CREATE TABLE IF NOT EXISTS voice_channels (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "channel_id TEXT NOT NULL," +
                    "channel_name TEXT NOT NULL," +
                    "creator_id TEXT NOT NULL," +
                    "creator_name TEXT NOT NULL," +
                    "guild_id TEXT NOT NULL," +
                    "guild_name TEXT," +
                    "category_id TEXT," +
                    "user_limit INTEGER DEFAULT 0," +
                    "channel_type TEXT DEFAULT 'CUSTOM'," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "deleted_at TIMESTAMP," +
                    "is_active BOOLEAN DEFAULT true" +
                    ")";

            String voiceActivityTable = "CREATE TABLE IF NOT EXISTS voice_activity (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "channel_id TEXT NOT NULL," +
                    "user_id TEXT NOT NULL," +
                    "user_name TEXT NOT NULL," +
                    "guild_id TEXT NOT NULL," +
                    "action_type TEXT NOT NULL," +
                    "joined_at TIMESTAMP," +
                    "left_at TIMESTAMP," +
                    "duration_seconds INTEGER," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";

            String botMetadataTable = "CREATE TABLE IF NOT EXISTS bot_metadata (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";

            String userStatsTable = "CREATE TABLE IF NOT EXISTS user_stats (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id TEXT NOT NULL," +
                    "user_name TEXT NOT NULL," +
                    "guild_id TEXT NOT NULL," +
                    "total_channels_created INTEGER DEFAULT 0," +
                    "total_voice_time_seconds BIGINT DEFAULT 0," +
                    "last_channel_created TIMESTAMP," +
                    "last_voice_activity TIMESTAMP," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(user_id, guild_id)" +
                    ")";

            String serverSetupTable = "CREATE TABLE IF NOT EXISTS server_setup (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "guild_id TEXT NOT NULL UNIQUE," +
                    "category_id TEXT NOT NULL," +
                    "managed_vc_ids TEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";

            String channelRestrictionsTable = "CREATE TABLE IF NOT EXISTS channel_restrictions (" +
                    "channelId TEXT NOT NULL," +
                    "restrictionType TEXT NOT NULL," +
                    "PRIMARY KEY (channelId, restrictionType)" +
                    ")";

            conn.createStatement().execute(warningsTable);
            conn.createStatement().execute(analyticsTable);
            conn.createStatement().execute(settingsTable);
            conn.createStatement().execute(guildsTable);
            conn.createStatement().execute(commandLogsTable);
            conn.createStatement().execute(guildSettingsTable);
            conn.createStatement().execute(botRolesTable);
            conn.createStatement().execute(mutesTable);
            conn.createStatement().execute(messageLogsTable);
            conn.createStatement().execute(generalLogsTable);
            conn.createStatement().execute(voiceChannelsTable);
            conn.createStatement().execute(voiceActivityTable);
            conn.createStatement().execute(botMetadataTable);
            conn.createStatement().execute(userStatsTable);
            conn.createStatement().execute(serverSetupTable);
            conn.createStatement().execute(channelRestrictionsTable);

            // Migrate existing DBs that predate guild scoping
            ensureColumn(conn, "warnings", "guildId", "TEXT");
            ensureColumn(conn, "moderation_analytics", "guildId", "TEXT");

            System.out.println("[DatabaseInitializer] Database tables initialized.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void ensureColumn(Connection conn, String table, String column, String type) {
        try {
            conn.createStatement().execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            System.out.println("[DatabaseInitializer] Added column " + table + "." + column);
        } catch (Exception ignored) {
            // Column already exists
        }
    }
}


