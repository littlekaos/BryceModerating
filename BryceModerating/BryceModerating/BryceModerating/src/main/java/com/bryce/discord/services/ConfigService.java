package com.bryce.discord.services;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.Permission;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigService {
    private final Set<String> noMessageChannels = new HashSet<>();
    private final Set<String> noMediaChannels = new HashSet<>();
    private final Set<String> noContentChannels = new HashSet<>();
    private final Set<String> screenshotOnlyChannels = new HashSet<>();
    private final Set<String> mediaOnlyChannels = new HashSet<>();
    private final Set<String> mediaWithTextChannels = new HashSet<>();
    private final Set<String> textOnlyChannels = new HashSet<>();
    private final Set<String> moderatorRoles = new HashSet<>();
    private final Set<String> adminRoles = new HashSet<>();

    private static class CachedValue {
        final boolean value;
        final long timestamp;

        CachedValue(boolean value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }

    private final Map<String, CachedValue> permissionCache = new ConcurrentHashMap<>();
    private static final long PERMISSION_CACHE_TTL_MILLIS = 5000;

    public static final String MODERATION_LOG_CHANNEL_NAME = "moderation-logs";
    public static final String PURGE_LOG_CHANNEL_NAME = "server-logs";

    public boolean hasModeratorPermissions(Member member) {
        if (member == null) {
            return false;
        }

        String cacheKey = "mod:" + member.getId();
        CachedValue cached = permissionCache.get(cacheKey);
        if (cached != null && !cached.isExpired(PERMISSION_CACHE_TTL_MILLIS)) {
            return cached.value;
        }

        boolean hasPermissions = hasAdminPermissions(member) ||
                member.getRoles().stream()
                        .anyMatch(role -> moderatorRoles.contains(role.getId()));

        permissionCache.put(cacheKey, new CachedValue(hasPermissions));
        return hasPermissions;
    }

    public boolean hasAdminPermissions(Member member) {
        if (member == null) {
            return false;
        }

        String cacheKey = "admin:" + member.getId();
        CachedValue cached = permissionCache.get(cacheKey);
        if (cached != null && !cached.isExpired(PERMISSION_CACHE_TTL_MILLIS)) {
            return cached.value;
        }

        boolean hasPermissions = member.getRoles().stream()
                .anyMatch(role -> adminRoles.contains(role.getId()));

        permissionCache.put(cacheKey, new CachedValue(hasPermissions));
        return hasPermissions;
    }

    public void clearPermissionCache(String memberId) {
        permissionCache.remove("mod:" + memberId);
        permissionCache.remove("admin:" + memberId);
    }

    public void addNoMessageChannel(String channelId) {
        noMessageChannels.add(channelId);
    }

    public void addNoMediaChannel(String channelId) {
        noMediaChannels.add(channelId);
    }

    public void addNoContentChannel(String channelId) {
        noContentChannels.add(channelId);
    }

    public void addScreenshotOnlyChannel(String channelId) {
        screenshotOnlyChannels.add(channelId);
    }

    public void addMediaOnlyChannel(String channelId) {
        mediaOnlyChannels.add(channelId);
    }

    public void addMediaWithTextChannel(String channelId) {
        mediaWithTextChannels.add(channelId);
    }

    public void addTextOnlyChannel(String channelId) {
        textOnlyChannels.add(channelId);
    }

    public void addRestriction(String channelId, String type) {
        switch (type) {
            case "NO_MESSAGE": noMessageChannels.add(channelId); break;
            case "NO_MEDIA": noMediaChannels.add(channelId); break;
            case "NO_CONTENT": noContentChannels.add(channelId); break;
            case "SCREENSHOT_ONLY": screenshotOnlyChannels.add(channelId); break;
            case "MEDIA_ONLY": mediaOnlyChannels.add(channelId); break;
            case "MEDIA_WITH_TEXT": mediaWithTextChannels.add(channelId); break;
            case "TEXT_ONLY": textOnlyChannels.add(channelId); break;
        }
    }

    public void removeRestriction(String channelId, String type) {
        switch (type) {
            case "NO_MESSAGE": noMessageChannels.remove(channelId); break;
            case "NO_MEDIA": noMediaChannels.remove(channelId); break;
            case "NO_CONTENT": noContentChannels.remove(channelId); break;
            case "SCREENSHOT_ONLY": screenshotOnlyChannels.remove(channelId); break;
            case "MEDIA_ONLY": mediaOnlyChannels.remove(channelId); break;
            case "MEDIA_WITH_TEXT": mediaWithTextChannels.remove(channelId); break;
            case "TEXT_ONLY": textOnlyChannels.remove(channelId); break;
        }
    }

    public void loadRestrictions(Map<String, Set<String>> restrictions) {
        if (restrictions == null) return;
        
        if (restrictions.containsKey("NO_MESSAGE")) noMessageChannels.addAll(restrictions.get("NO_MESSAGE"));
        if (restrictions.containsKey("NO_MEDIA")) noMediaChannels.addAll(restrictions.get("NO_MEDIA"));
        if (restrictions.containsKey("NO_CONTENT")) noContentChannels.addAll(restrictions.get("NO_CONTENT"));
        if (restrictions.containsKey("SCREENSHOT_ONLY")) screenshotOnlyChannels.addAll(restrictions.get("SCREENSHOT_ONLY"));
        if (restrictions.containsKey("MEDIA_ONLY")) mediaOnlyChannels.addAll(restrictions.get("MEDIA_ONLY"));
        if (restrictions.containsKey("MEDIA_WITH_TEXT")) mediaWithTextChannels.addAll(restrictions.get("MEDIA_WITH_TEXT"));
        if (restrictions.containsKey("TEXT_ONLY")) textOnlyChannels.addAll(restrictions.get("TEXT_ONLY"));
    }

    public void addModeratorRole(String roleId) {
        moderatorRoles.add(roleId);
    }

    public void addAdminRole(String roleId) {
        adminRoles.add(roleId);
    }

    public void removeModeratorRole(String roleId) {
        moderatorRoles.remove(roleId);
    }

    public void removeAdminRole(String roleId) {
        adminRoles.remove(roleId);
    }

    public Set<String> getNoMessageChannels() {
        return noMessageChannels;
    }

    public Set<String> getNoMediaChannels() {
        return noMediaChannels;
    }

    public Set<String> getNoContentChannels() {
        return noContentChannels;
    }

    public Set<String> getScreenshotOnlyChannels() {
        return screenshotOnlyChannels;
    }

    public Set<String> getMediaOnlyChannels() {
        return mediaOnlyChannels;
    }

    public Set<String> getMediaWithTextChannels() {
        return mediaWithTextChannels;
    }

    public Set<String> getTextOnlyChannels() {
        return textOnlyChannels;
    }

    public boolean canModerate(Member moderator, Member target) {
        if (moderator == null || target == null) {
            return false;
        }

        // Standard hierarchy check (must be higher in role list)
        // This ensures nobody can moderate people higher than or equal to them
        if (!moderator.canInteract(target)) {
            return false;
        }
        
        // Admins can moderate anyone below them in hierarchy
        if (hasAdminPermissions(moderator)) {
            return true;
        }

        // Moderators can moderate anyone below them in hierarchy
        return hasModeratorPermissions(moderator);
    }

    public void clearModeratorRoles() {
        moderatorRoles.clear();
    }

    public void clearAdminRoles() {
        adminRoles.clear();
    }

    public Set<String> getModeratorRoles() {
        return moderatorRoles;
    }

    public Set<String> getAdminRoles() {
        return adminRoles;
    }
}


