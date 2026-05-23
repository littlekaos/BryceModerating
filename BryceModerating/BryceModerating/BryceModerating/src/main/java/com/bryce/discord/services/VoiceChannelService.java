package com.bryce.discord.services;

import com.bryce.discord.services.VoiceChannelDatabase;
import com.bryce.discord.models.VoiceChannelRecord;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.List;

public class VoiceChannelService {
    private final VoiceChannelDatabase database = new VoiceChannelDatabase();

    public VoiceChannelService() {
        initializeServiceIfNeeded();
    }

    private void initializeServiceIfNeeded() {
        String isInitialized = database.getMetadata("voice_service_initialized");
        if (!"true".equals(isInitialized)) {
            System.out.println("🔄 Initializing Voice Channel Service for the first time...");
            database.setMetadata("voice_service_initialized", "true");
            database.setMetadata("service_start_time", String.valueOf(System.currentTimeMillis()));
            System.out.println("✅ Voice Channel Service initialized successfully!");
        } else {
            System.out.println("✅ Voice Channel Service already initialized");
            System.out.println("📊 Total channels tracked: " + database.getTotalChannelsCreated());
        }
    }

    // Voice Channel Creation Methods
    public void logChannelCreation(VoiceChannel channel, User creator, String channelType) {
        Guild guild = channel.getGuild();
        String categoryId = channel.getParentCategory() != null ? channel.getParentCategory().getId() : null;

        database.addVoiceChannel(
                channel.getId(),
                channel.getName(),
                creator.getId(),
                creator.getName(),
                guild.getId(),
                guild.getName(),
                categoryId,
                channel.getUserLimit(),
                channelType
        );

        System.out.println("📝 Logged channel creation: " + channel.getName() + " by " + creator.getName());
    }

    public void logAutomaticChannelCreation(VoiceChannel channel, Member creator) {
        logChannelCreation(channel, creator.getUser(), "AUTOMATIC");
    }

    public void logCustomChannelCreation(VoiceChannel channel, User creator) {
        logChannelCreation(channel, creator, "CUSTOM");
    }

    // Voice Channel Deletion Methods
    public void logChannelDeletion(String channelId) {
        database.markChannelDeleted(channelId);
        System.out.println("🗑️ Logged channel deletion: " + channelId);
    }

    // Voice Activity Tracking Methods
    public void logUserJoinVoice(VoiceChannel channel, Member member) {
        database.logVoiceJoin(
                channel.getId(),
                member.getUser().getId(),
                member.getEffectiveName(),
                channel.getGuild().getId()
        );
        System.out.println("📥 Logged voice join: " + member.getEffectiveName() + " -> " + channel.getName());
    }

    public void logUserLeaveVoice(VoiceChannel channel, Member member) {
        database.logVoiceLeave(
                channel.getId(),
                member.getUser().getId(),
                member.getEffectiveName(),
                channel.getGuild().getId()
        );
        System.out.println("📤 Logged voice leave: " + member.getEffectiveName() + " <- " + channel.getName());
    }

    // Query Methods
    public List<VoiceChannelRecord> getActiveChannels(String guildId) {
        return database.getActiveChannels(guildId);
    }

    public List<VoiceChannelRecord> getUserCreatedChannels(String userId, String guildId) {
        return database.getUserCreatedChannels(userId, guildId);
    }

    public int getTotalChannelsCreated() {
        return database.getTotalChannelsCreated();
    }

    public int getActiveChannelCount(String guildId) {
        return database.getActiveChannelCount(guildId);
    }

    // Statistics Methods
    public String getServiceStats() {
        int totalChannels = database.getTotalChannelsCreated();
        String startTime = database.getMetadata("service_start_time");

        StringBuilder stats = new StringBuilder();
        stats.append("**Voice Channel Service Statistics**\n");
        stats.append("📊 Total Channels Created: **").append(totalChannels).append("**\n");

        if (startTime != null) {
            long startTimeMs = Long.parseLong(startTime);
            long uptime = (System.currentTimeMillis() - startTimeMs) / 1000;
            stats.append("⏱️ Service Uptime: **").append(formatDuration(uptime)).append("**\n");
        }

        return stats.toString();
    }

    public String getGuildStats(String guildId) {
        int activeChannels = database.getActiveChannelCount(guildId);

        StringBuilder stats = new StringBuilder();
        stats.append("**Server Voice Channel Statistics**\n");
        stats.append("🔊 Active Channels: **").append(activeChannels).append("**\n");

        return stats.toString();
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    // Utility Methods
    public void resetServiceData() {
        System.out.println("⚠️ This would reset all voice channel data - implement if needed");
        // Implement if you need to reset the database
    }

    public boolean isChannelTracked(String channelId) {
        // You could implement this to check if a channel is in the database
        return true; // For now, assume all channels should be tracked
    }
}



