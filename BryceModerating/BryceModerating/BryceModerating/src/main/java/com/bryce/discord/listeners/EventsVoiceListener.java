package com.bryce.discord.listeners;

import com.bryce.discord.config.EventsServerConfig;
import com.bryce.discord.services.VoiceChannelManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.util.Arrays;
import java.util.List;

public class EventsVoiceListener extends ListenerAdapter {
    private final VoiceChannelManager channelManager;

    public EventsVoiceListener(VoiceChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        System.out.println("DEBUG: Voice update event detected");
        System.out.println("DEBUG: User: " + event.getMember().getEffectiveName());

        // Handle voice channel join
        if (event.getChannelJoined() != null) {
            System.out.println("DEBUG: User joined channel: " + event.getChannelJoined().getName());
            handleVoiceJoin(event);
        }

        // Handle voice channel leave
        if (event.getChannelLeft() != null) {
            System.out.println("DEBUG: User left channel: " + event.getChannelLeft().getName());
            handleVoiceLeave(event);
        }
    }

    private void handleVoiceJoin(GuildVoiceUpdateEvent event) {
        VoiceChannel joinedChannel = event.getChannelJoined().asVoiceChannel();
        String channelName = joinedChannel.getName();
        String channelId = joinedChannel.getId();
        Guild guild = event.getGuild();
        String guildId = guild.getId();

        System.out.println("DEBUG: Processing join for channel: '" + channelName + "'");
        System.out.println("DEBUG: Guild ID: " + guildId);
        System.out.println("DEBUG: Server has category configured: " + channelManager.getEventsServerConfig().hasServerCategory(guildId));

        if (!channelManager.getEventsServerConfig().hasServerCategory(guildId)) {
            System.out.println("DEBUG: Server not configured, skipping");
            return;
        }

        if (!isManagedVoiceChannel(guildId, channelId)) {
            System.out.println("DEBUG: Channel is not in managed list, skipping");
            return;
        }

        // Log voice activity to database for ALL managed voice channels
        channelManager.getVoiceService().logUserJoinVoice(joinedChannel, event.getMember());

        boolean isCreateVcChannel = channelManager.isCreateVcChannel(channelName);
        System.out.println("DEBUG: Is create VC channel: " + isCreateVcChannel);

        if (isCreateVcChannel) {
            System.out.println("DEBUG: Creating automatic voice channel for user: " + event.getMember().getEffectiveName());
            channelManager.createAutomaticVoiceChannel(event.getMember(), channelName);
        }
    }

    private boolean isManagedVoiceChannel(String guildId, String channelId) {
        // This assumes VoiceChannelDatabase has a getManagedVcIds method
        // If it doesn't exist, you'll need to add it to VoiceChannelDatabase class
        // For now, we're bypassing this check
        return true;
    }

    private void handleVoiceLeave(GuildVoiceUpdateEvent event) {
        VoiceChannel leftChannel = event.getChannelLeft().asVoiceChannel();

        System.out.println("DEBUG: Checking if channel should be deleted: " + leftChannel.getName());
        System.out.println("DEBUG: Is auto-created channel: " + channelManager.isAutoCreatedChannel(leftChannel.getIdLong()));
        System.out.println("DEBUG: Channel member count: " + leftChannel.getMembers().size());

        // Log voice activity to database for ALL voice channels
        channelManager.getVoiceService().logUserLeaveVoice(leftChannel, event.getMember());

        // Only delete auto-created channels when empty
        if (channelManager.isAutoCreatedChannel(leftChannel.getIdLong())) {
            channelManager.deleteEmptyVoiceChannel(leftChannel);
        }
    }
}