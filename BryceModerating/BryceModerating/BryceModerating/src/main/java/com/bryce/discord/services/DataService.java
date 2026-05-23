package com.bryce.discord.services;

import com.bryce.discord.models.WarnRecord;
import com.bryce.discord.models.ModAction;
import com.bryce.discord.analytics.ActionType;
import net.dv8tion.jda.api.entities.Guild;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.ArrayList;
import java.util.List;

public class DataService {

    private static final boolean DEBUG_MODE = false;

    private boolean warningsModified = false;

    public void loadAllData() {
        warningsModified = false;
    }

    public void loadRolesFromDatabase(ConfigService configService) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "SELECT roleId, roleType FROM bot_roles";
                try (PreparedStatement pstmt = conn.prepareStatement(sql);
                     ResultSet rs = pstmt.executeQuery()) {

                    while (rs.next()) {
                        String roleId = rs.getString("roleId");
                        String roleType = rs.getString("roleType");

                        if ("moderator".equals(roleType)) {
                            configService.addModeratorRole(roleId);
                        } else if ("admin".equals(roleType)) {
                            configService.addAdminRole(roleId);
                        }
                    }
                }
                return null;
            });
            System.out.println("Loaded bot roles from database");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveBotRolesToDatabase(Set<String> moderatorRoles, Set<String> adminRoles) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String deleteSql = "DELETE FROM bot_roles";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                    pstmt.executeUpdate();
                }

                String insertSql = "INSERT INTO bot_roles (roleId, roleType) VALUES (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    for (String roleId : moderatorRoles) {
                        pstmt.setString(1, roleId);
                        pstmt.setString(2, "moderator");
                        pstmt.addBatch();
                    }
                    for (String roleId : adminRoles) {
                        pstmt.setString(1, roleId);
                        pstmt.setString(2, "admin");
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    System.out.println("Saved " + (moderatorRoles.size() + adminRoles.size()) + " bot roles to database");
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveAllData() {
        warningsModified = false;
    }

    public boolean isDataModified() {
        return warningsModified;
    }

    // Per-guild mute role methods
    public String getMuteRoleId(String guildId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                String sql = "SELECT value FROM guild_settings WHERE guildId = ? AND key = 'muteRoleId'";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getString("value");
                        }
                    }
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setMuteRoleId(String guildId, String muteRoleId) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO guild_settings (guildId, key, value) VALUES (?, 'muteRoleId', ?) " +
                           "ON CONFLICT (guildId, key) DO UPDATE SET value = EXCLUDED.value";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, muteRoleId);
                    pstmt.executeUpdate();
                    System.out.println("Saved muteRoleId for guild " + guildId + ": " + muteRoleId);
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void markWarningsModified() {
        warningsModified = true;
    }

    public void addWarning(WarnRecord warn) {
        saveWarnRecord(warn);
        markWarningsModified();
    }

    public List<WarnRecord> getAllWarnings() {
        return loadWarnRecords();
    }

    public List<WarnRecord> getWarningsForUser(String userId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                List<WarnRecord> records = new ArrayList<>();
                String sql = "SELECT userId, moderatorId, reason, timestamp FROM warnings WHERE userId = ?";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, userId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            WarnRecord record = new WarnRecord(
                                    rs.getString("userId"),
                                    rs.getString("moderatorId"),
                                    rs.getString("reason"),
                                    rs.getLong("timestamp")
                            );
                            records.add(record);
                        }
                    }
                }
                return records;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void deleteWarningsForUser(String userId) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "DELETE FROM warnings WHERE userId = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, userId);
                    int rows = pstmt.executeUpdate();
                    System.out.println("Deleted " + rows + " warnings for userId: " + userId);
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveWarnRecord(WarnRecord warn) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO warnings (userId, moderatorId, reason, timestamp) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, warn.getUserId());
                    pstmt.setString(2, warn.getModeratorId());
                    pstmt.setString(3, warn.getReason());
                    pstmt.setLong(4, warn.getTimestamp());

                    if (DEBUG_MODE) {
                        System.out.println("[DEBUG] Saving warning to DB for userId=" + warn.getUserId());
                    }
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<WarnRecord> loadWarnRecords() {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                List<WarnRecord> records = new ArrayList<>();
                String sql = "SELECT userId, moderatorId, reason, timestamp FROM warnings";

                try (PreparedStatement pstmt = conn.prepareStatement(sql);
                     ResultSet rs = pstmt.executeQuery()) {

                    while (rs.next()) {
                        WarnRecord record = new WarnRecord(
                                rs.getString("userId"),
                                rs.getString("moderatorId"),
                                rs.getString("reason"),
                                rs.getLong("timestamp")
                        );
                        records.add(record);
                    }
                }
                return records;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void saveModerationAnalytics(ModAction action) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO moderation_analytics (action, moderatorId, moderatorName, targetId, targetName, reason, timestamp, duration, count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, action.getActionType().name());
                    pstmt.setString(2, action.getModeratorId());
                    pstmt.setString(3, action.getModeratorName());
                    pstmt.setString(4, action.getTargetId());
                    pstmt.setString(5, action.getTargetName());
                    pstmt.setString(6, action.getReason());
                    pstmt.setLong(7, action.getTimestamp());
                    pstmt.setInt(8, action.getDuration());
                    pstmt.setInt(9, action.getCount());

                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<ModAction> loadModerationAnalytics() {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                List<ModAction> actions = new ArrayList<>();
                String sql = "SELECT action, moderatorId, moderatorName, targetId, targetName, reason, timestamp, duration, count FROM moderation_analytics";

                try (PreparedStatement pstmt = conn.prepareStatement(sql);
                     ResultSet rs = pstmt.executeQuery()) {

                    while (rs.next()) {
                        ModAction action = new ModAction(
                                ActionType.valueOf(rs.getString("action")),
                                rs.getString("moderatorId") != null ? rs.getString("moderatorId") : "unknown",
                                rs.getString("moderatorName") != null ? rs.getString("moderatorName") : "Unknown",
                                rs.getString("targetId") != null ? rs.getString("targetId") : "0",
                                rs.getString("targetName") != null ? rs.getString("targetName") : "Unknown",
                                rs.getString("reason") != null ? rs.getString("reason") : "",
                                rs.getLong("timestamp"),
                                rs.getInt("duration"),
                                rs.getInt("count")
                        );
                        actions.add(action);
                    }
                }
                return actions;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void saveGuildInfo(Guild guild) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO guilds (guildId, guildName, joinedTimestamp) VALUES (?, ?, ?) " +
                           "ON CONFLICT (guildId) DO UPDATE SET guildName = EXCLUDED.guildName, joinedTimestamp = EXCLUDED.joinedTimestamp";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guild.getId());
                    pstmt.setString(2, guild.getName());
                    pstmt.setLong(3, System.currentTimeMillis());

                    pstmt.executeUpdate();
                    System.out.println("Saved guild info: " + guild.getName());
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveCommandLog(String userId, String userName, String commandName) {
        try {
            long timestamp = System.currentTimeMillis();
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO command_logs (userId, userName, commandName, timestamp) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, userId);
                    pstmt.setString(2, userName);
                    pstmt.setString(3, commandName);
                    pstmt.setLong(4, timestamp);

                    pstmt.executeUpdate();
                    System.out.println("Logged command: " + commandName + " by " + userName);
                }
                return null;
            });
        } catch (Exception e) {
            System.err.println("Failed to log command: " + e.getMessage());
        }
    }

    public void addMute(String guildId, String userId, long unmuteTime) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO mutes (guildId, userId, unmuteTime) VALUES (?, ?, ?) " +
                           "ON CONFLICT (guildId, userId) DO UPDATE SET unmuteTime = EXCLUDED.unmuteTime";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, userId);
                    pstmt.setLong(3, unmuteTime);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeMute(String guildId, String userId) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "DELETE FROM mutes WHERE guildId = ? AND userId = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, userId);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, Long> getExpiredMutes(String guildId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                Map<String, Long> expiredMutes = new HashMap<>();
                String sql = "SELECT userId, unmuteTime FROM mutes WHERE guildId = ? AND unmuteTime <= ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    pstmt.setLong(2, System.currentTimeMillis());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            expiredMutes.put(rs.getString("userId"), rs.getLong("unmuteTime"));
                        }
                    }
                }
                return expiredMutes;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public void logMessage(String guildId, String channelId, String messageId, String userId, String content, String action) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO message_logs (guildId, channelId, messageId, userId, content, action, timestamp) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, channelId);
                    pstmt.setString(3, messageId);
                    pstmt.setString(4, userId);
                    pstmt.setString(5, content);
                    pstmt.setString(6, action);
                    pstmt.setLong(7, System.currentTimeMillis());
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            System.err.println("Failed to log message to DB: " + e.getMessage());
        }
    }

    public void logGeneral(String guildId, String userId, String eventType, String details) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO general_logs (guildId, userId, eventType, details, timestamp) " +
                           "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guildId);
                    pstmt.setString(2, userId);
                    pstmt.setString(3, eventType);
                    pstmt.setString(4, details);
                    pstmt.setLong(5, System.currentTimeMillis());
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            System.err.println("Failed to log general event to DB: " + e.getMessage());
        }
    }

    public List<ModAction> getBanUnbanHistory(String targetUserId) {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                List<ModAction> actions = new ArrayList<>();
                String sql = "SELECT action, moderatorId, moderatorName, targetId, targetName, reason, timestamp, duration, count FROM moderation_analytics " +
                           "WHERE targetId = ? AND (action = 'BAN' OR action = 'UNBAN') " +
                           "ORDER BY timestamp DESC";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, targetUserId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            ModAction action = new ModAction(
                                    ActionType.valueOf(rs.getString("action")),
                                    rs.getString("moderatorId") != null ? rs.getString("moderatorId") : "unknown",
                                    rs.getString("moderatorName") != null ? rs.getString("moderatorName") : "Unknown",
                                    rs.getString("targetId") != null ? rs.getString("targetId") : "0",
                                    rs.getString("targetName") != null ? rs.getString("targetName") : "Unknown",
                                    rs.getString("reason") != null ? rs.getString("reason") : "",
                                    rs.getLong("timestamp"),
                                    rs.getInt("duration"),
                                    rs.getInt("count")
                            );
                            actions.add(action);
                        }
                    }
                }
                return actions;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void saveChannelRestriction(String channelId, String restrictionType) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "INSERT INTO channel_restrictions (channelId, restrictionType) VALUES (?, ?) " +
                           "ON CONFLICT (channelId, restrictionType) DO NOTHING";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, channelId);
                    pstmt.setString(2, restrictionType);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeChannelRestriction(String channelId, String restrictionType) {
        try {
            DatabaseManager.executeWithRetry(conn -> {
                String sql = "DELETE FROM channel_restrictions WHERE channelId = ? AND restrictionType = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, channelId);
                    pstmt.setString(2, restrictionType);
                    pstmt.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, Set<String>> loadAllChannelRestrictions() {
        try {
            return DatabaseManager.executeWithRetry(conn -> {
                Map<String, Set<String>> restrictions = new HashMap<>();
                String sql = "SELECT channelId, restrictionType FROM channel_restrictions";
                try (PreparedStatement pstmt = conn.prepareStatement(sql);
                     ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String channelId = rs.getString("channelId");
                        String type = rs.getString("restrictionType");
                        restrictions.computeIfAbsent(type, k -> new HashSet<>()).add(channelId);
                    }
                }
                return restrictions;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }
}


