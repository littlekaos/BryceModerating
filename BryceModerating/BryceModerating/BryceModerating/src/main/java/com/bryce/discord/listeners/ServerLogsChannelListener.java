package com.bryce.discord.listeners;

import com.bryce.discord.BryceModeratingBot;
import com.bryce.discord.utils.LoggingUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Instant;

public class ServerLogsChannelListener extends ListenerAdapter {

    private final BryceModeratingBot bot;

    public ServerLogsChannelListener(BryceModeratingBot bot) {
        this.bot = bot;
    }

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.GREEN, "Channel Created")
                .setDescription("**Channel:** " + event.getChannel().getName())
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "Channel Deleted")
                .setDescription("**Channel:** " + event.getChannel().getName())
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onEmojiAdded(EmojiAddedEvent event) {
        TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.GREEN, "Emoji Added")
                .setDescription("**Emoji:** " + event.getEmoji().getName())
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "Emoji Removed")
                .setDescription("**Emoji:** " + event.getEmoji().getName())
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getMember().getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getMember().getUser().getId());

            if (event.getChannelJoined() != null) {
                EmbedBuilder embed = LoggingUtil.createEmbed(Color.GREEN, "Voice Channel Join")
                        .setDescription("**Username:** " + userInfo[0] + "\n" +
                                "**Discord ID:** " + userInfo[1] + "\n" +
                                "**Channel:** " + event.getChannelJoined().getName())
                        .setTimestamp(Instant.now());
                logChannel.sendMessageEmbeds(embed.build()).queue();
            } else if (event.getChannelLeft() != null) {
                EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "Voice Channel Leave")
                        .setDescription("**Username:** " + userInfo[0] + "\n" +
                                "**Discord ID:** " + userInfo[1] + "\n" +
                                "**Channel:** " + event.getChannelLeft().getName())
                        .setTimestamp(Instant.now());
                logChannel.sendMessageEmbeds(embed.build()).queue();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



