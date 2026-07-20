package com.bryce.discord.listeners;

import com.bryce.discord.services.VoiceChannelManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;

public class EventsCommandListener extends ListenerAdapter {
    private final VoiceChannelManager channelManager;

    public EventsCommandListener(VoiceChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("dbinfo")) {
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        handleDbInfo(event);
    }

    private void handleDbInfo(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        String userId = event.getUser().getId();
        if (!userId.equals("689519709988585648") && !userId.equals("529480987525251082")) {
            event.getHook().editOriginal("❌ You don't have permission to view database information.").queue();
            return;
        }

        int totalChannels = channelManager.getVoiceService().getTotalChannelsCreated();
        int activeChannels = channelManager.getVoiceService().getActiveChannelCount(event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Database Information")
                .setColor(Color.CYAN)
                .addField("Total Channels Tracked", String.valueOf(totalChannels), true)
                .addField("Active Channels (This Server)", String.valueOf(activeChannels), true)
                .addField("Database Type", "PostgreSQL", true)
                .setFooter("Voice Channel Management System")
                .setTimestamp(java.time.Instant.now());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }
}
