package com.bryce.discord.utils;

import com.bryce.discord.services.ConfigService;
import com.bryce.discord.services.DataService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.Color;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LoggingUtil {

    public static final String SERVER_LOGS_SETTING = "server_logs_channel_id";
    public static final String MODERATION_LOGS_SETTING = "moderation_logs_channel_id";

    /**
     * Resolve the server-logs channel without creating it.
     * Prefer stored ID; fall back to name lookup. Clears stale IDs if the channel was deleted.
     */
    public static TextChannel findServerLogsChannel(Guild guild, DataService dataService) {
        return resolveLogChannel(guild, dataService, SERVER_LOGS_SETTING, ConfigService.PURGE_LOG_CHANNEL_NAME);
    }

    /**
     * Resolve the moderation-logs channel without creating it.
     */
    public static TextChannel findModerationLogsChannel(Guild guild, DataService dataService) {
        return resolveLogChannel(guild, dataService, MODERATION_LOGS_SETTING, ConfigService.MODERATION_LOG_CHANNEL_NAME);
    }

    private static TextChannel resolveLogChannel(Guild guild, DataService dataService, String settingKey, String channelName) {
        if (guild == null || dataService == null) {
            return null;
        }

        String status = dataService.getGuildSetting(guild.getId(), "staff_setup_status");
        // Explicit decline: do not log via name fallback
        if ("declined".equals(status)) {
            return null;
        }

        String storedId = dataService.getGuildSetting(guild.getId(), settingKey);
        if (storedId != null && !storedId.isEmpty()) {
            TextChannel byId = guild.getTextChannelById(storedId);
            if (byId != null) {
                return byId;
            }
            // Channel was deleted — do not recreate
            dataService.clearGuildSetting(guild.getId(), settingKey);
            return null;
        }

        // Name lookup only when configured, or for legacy servers that never ran opt-in setup
        if (status == null || "configured".equals(status) || "pending".equals(status)) {
            return guild.getTextChannelsByName(channelName, true).stream().findFirst().orElse(null);
        }

        return null;
    }

    public static EmbedBuilder createEmbed(Color color, String title) {
        return new EmbedBuilder()
                .setColor(color)
                .setTitle(title);
    }

    public static String getFormattedTime(ZoneId zoneId) {
        return java.time.ZonedDateTime.now(zoneId)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    public static ZoneId getIndianapolisZone() {
        return ZoneId.of("America/Indianapolis");
    }

    public static DateTimeFormatter getStandardFormatter() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    }
}
