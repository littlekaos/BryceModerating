package com.bryce.discord.services;

import com.bryce.discord.models.VoiceChannelRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VoiceChannelDatabase {

    public VoiceChannelDatabase() {
        // Initialization is now handled by DatabaseInitializer in BryceModeratingBot
    }

    // Voice Channel Management Methods
    public void addVoiceChannel(String channelId, String channelName, String creatorId, String creatorName,
                                String guildId, String guildName, String categoryId, int userLimit, String channelType) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO voice_channels (channel_id, channel_name, creator_id, creator_name, guild_id, guild_name, category_id, user_limit, channel_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, channelId);
                    pstmt.setString(2, channelName);
                    pstmt.setString(3, creatorId);
                    pstmt.setString(4, creatorName);
                    pstmt.setString(5, guildId);
                    pstmt.setString(6, guildName);
                    pstmt.setString(7, categoryId);
                    pstmt.setInt(8, userLimit);
                    pstmt.setString(9, channelType);
                    pstmt.executeUpdate();

                    // Update user stats
                    updateUserChannelStats(conn, creatorId, creatorName, guildId);
                }
                return null;
            });
        } catch (SQLException e) {
            System.err.println("Error adding voice channel record: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void markChannelDeleted(String channelId) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "UPDATE voice_channels SET deleted_at = CURRENT_TIMESTAMP, is_active = false WHERE channel_id = ? AND is_active = true";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, channelId);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            System.err.println("Error marking channel as deleted: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Voice Activity Tracking Methods
    public void logVoiceJoin(String channelId, String userId, String userName, String guildId) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO voice_activity (channel_id, user_id, user_name, guild_id, action_type, joined_at) VALUES (?, ?, ?, ?, 'JOIN', CURRENT_TIMESTAMP)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, channelId);
                    pstmt.setString(2, userId);
                    pstmt.setString(3, userName);
                    pstmt.setString(4, guildId);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            System.err.println("Error logging voice join: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void logVoiceLeave(String channelId, String userId, String userName, String guildId) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                // First, get the most recent join record for this user in this channel
                String selectSql = "SELECT id, joined_at FROM voice_activity WHERE channel_id = ? AND user_id = ? AND action_type = 'JOIN' AND left_at IS NULL ORDER BY joined_at DESC LIMIT 1";
                String updateSql = "UPDATE voice_activity SET left_at = ?, duration_seconds = ? WHERE id = ?";

                try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                    selectStmt.setString(1, channelId);
                    selectStmt.setString(2, userId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (rs.next()) {
                            int recordId = rs.getInt("id");
                            Timestamp joinedAt = rs.getTimestamp("joined_at");
                            Timestamp leftAt = new Timestamp(System.currentTimeMillis());
                            long durationSeconds = (leftAt.getTime() - joinedAt.getTime()) / 1000;

                            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                                updateStmt.setTimestamp(1, leftAt);
                                updateStmt.setLong(2, durationSeconds);
                                updateStmt.setInt(3, recordId);
                                updateStmt.executeUpdate();
                            }

                            // Update user stats
                            updateUserVoiceTime(conn, userId, userName, guildId, durationSeconds);
                        }
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            System.err.println("Error logging voice leave: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // User Statistics Methods
    private void updateUserChannelStats(Connection conn, String userId, String userName, String guildId) throws SQLException {
        String sql = "INSERT INTO user_stats (user_id, user_name, guild_id, total_channels_created, last_channel_created) " +
                     "VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (user_id, guild_id) " +
                     "DO UPDATE SET " +
                     "total_channels_created = user_stats.total_channels_created + 1, " +
                     "last_channel_created = CURRENT_TIMESTAMP, " +
                     "updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, userName);
            pstmt.setString(3, guildId);
            pstmt.executeUpdate();
        }
    }

    private void updateUserVoiceTime(Connection conn, String userId, String userName, String guildId, long durationSeconds) throws SQLException {
        String sql = "INSERT INTO user_stats (user_id, user_name, guild_id, total_voice_time_seconds, last_voice_activity) " +
                     "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (user_id, guild_id) " +
                     "DO UPDATE SET " +
                     "total_voice_time_seconds = user_stats.total_voice_time_seconds + ?, " +
                     "last_voice_activity = CURRENT_TIMESTAMP, " +
                     "updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, userName);
            pstmt.setString(3, guildId);
            pstmt.setLong(4, durationSeconds);
            pstmt.setLong(5, durationSeconds);
            pstmt.executeUpdate();
        }
    }

    // Query Methods
    public List<VoiceChannelRecord> getActiveChannels(String guildId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                List<VoiceChannelRecord> channels = new ArrayList<>();
                String sql = "SELECT * FROM voice_channels WHERE guild_id = ? AND is_active = true ORDER BY created_at DESC";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            channels.add(new VoiceChannelRecord(
                                    rs.getInt("id"),
                                    rs.getString("channel_id"),
                                    rs.getString("channel_name"),
                                    rs.getString("creator_id"),
                                    rs.getString("creator_name"),
                                    rs.getString("guild_id"),
                                    rs.getString("guild_name"),
                                    rs.getString("category_id"),
                                    rs.getInt("user_limit"),
                                    rs.getString("channel_type"),
                                    rs.getTimestamp("created_at"),
                                    rs.getTimestamp("deleted_at"),
                                    rs.getBoolean("is_active")
                            ));
                        }
                    }
                }
                return channels;
            });
        } catch (SQLException e) {
            System.err.println("Error getting active channels: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<VoiceChannelRecord> getUserCreatedChannels(String userId, String guildId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                List<VoiceChannelRecord> channels = new ArrayList<>();
                String sql = "SELECT * FROM voice_channels WHERE creator_id = ? AND guild_id = ? ORDER BY created_at DESC LIMIT 10";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, userId);
                    pstmt.setString(2, guildId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            channels.add(new VoiceChannelRecord(
                                    rs.getInt("id"),
                                    rs.getString("channel_id"),
                                    rs.getString("channel_name"),
                                    rs.getString("creator_id"),
                                    rs.getString("creator_name"),
                                    rs.getString("guild_id"),
                                    rs.getString("guild_name"),
                                    rs.getString("category_id"),
                                    rs.getInt("user_limit"),
                                    rs.getString("channel_type"),
                                    rs.getTimestamp("created_at"),
                                    rs.getTimestamp("deleted_at"),
                                    rs.getBoolean("is_active")
                            ));
                        }
                    }
                }
                return channels;
            });
        } catch (SQLException e) {
            System.err.println("Error getting user created channels: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public int getTotalChannelsCreated() {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                String sql = "SELECT COUNT(*) as count FROM voice_channels";
                try (PreparedStatement pstmt = conn.prepareStatement(sql);
                     ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("count");
                    }
                }
                return 0;
            });
        } catch (SQLException e) {
            System.err.println("Error getting total channel count: " + e.getMessage());
            return 0;
        }
    }

    public int getActiveChannelCount(String guildId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                String sql = "SELECT COUNT(*) as count FROM voice_channels WHERE guild_id = ? AND is_active = true";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("count");
                        }
                    }
                }
                return 0;
            });
        } catch (SQLException e) {
            System.err.println("Error getting active channel count: " + e.getMessage());
            return 0;
        }
    }

    public void setMetadata(String key, String value) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO bot_metadata (key, value) VALUES (?, ?) " +
                             "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = CURRENT_TIMESTAMP";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, key);
                    pstmt.setString(2, value);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            System.err.println("Error setting metadata: " + e.getMessage());
        }
    }

    public String getMetadata(String key) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                String sql = "SELECT value FROM bot_metadata WHERE key = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, key);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getString("value");
                        }
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            System.err.println("Error getting metadata: " + e.getMessage());
            return null;
        }
    }

    public void saveServerSetup(String guildId, String categoryId, String managedVcIds) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO server_setup (guild_id, category_id, managed_vc_ids) VALUES (?, ?, ?) " +
                             "ON CONFLICT (guild_id) DO UPDATE SET category_id = EXCLUDED.category_id, " +
                             "managed_vc_ids = EXCLUDED.managed_vc_ids, updated_at = CURRENT_TIMESTAMP";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, categoryId);
                    pstmt.setString(3, managedVcIds);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            System.err.println("Error saving server setup: " + e.getMessage());
        }
    }

    public String getCategoryId(String guildId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                String sql = "SELECT category_id FROM server_setup WHERE guild_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getString("category_id");
                        }
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            System.err.println("Error getting category ID: " + e.getMessage());
            return null;
        }
    }

    public String getManagedVcIds(String guildId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                String sql = "SELECT managed_vc_ids FROM server_setup WHERE guild_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getString("managed_vc_ids");
                        }
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            System.err.println("Error getting managed VC IDs: " + e.getMessage());
            return null;
        }
    }

    public boolean hasServerSetup(String guildId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                String sql = "SELECT 1 FROM server_setup WHERE guild_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        return rs.next();
                    }
                }
            });
        } catch (SQLException e) {
            System.err.println("Error checking server setup: " + e.getMessage());
            return false;
        }
    }
}
