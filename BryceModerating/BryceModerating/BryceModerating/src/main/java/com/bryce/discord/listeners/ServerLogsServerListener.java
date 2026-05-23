package com.bryce.discord.listeners;

import com.bryce.discord.BryceModeratingBot;
import com.bryce.discord.utils.LoggingUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateIconEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ServerLogsServerListener extends ListenerAdapter {

    private final BryceModeratingBot bot;

    public ServerLogsServerListener(BryceModeratingBot bot) {
        this.bot = bot;
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

        bot.getDataService().logGeneral(event.getGuild().getId(), null, "BOT_GUILD_JOIN",
                "Name: " + event.getGuild().getName());

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.BLUE, "Bot Joined Server")
                .setDescription("Hello! I'm now part of this server. Let's have some fun!")
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();

        event.getGuild().loadMembers().onSuccess(members -> {
            members.forEach(member -> bot.getUserCache().cacheUser(member.getUser()));
            logChannel.sendMessage("✅ Successfully cached " + members.size() + " members!").queue();
        });
    }

    @Override
    public void onGuildUpdateName(GuildUpdateNameEvent event) {
        TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

        bot.getDataService().logGeneral(event.getGuild().getId(), null, "GUILD_NAME_UPDATE",
                "Old: " + event.getOldName() + ", New: " + event.getNewName());

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "Server Name Updated")
                .setDescription("**Old Name:** " + event.getOldName() +
                        "\n**New Name:** " + event.getNewName())
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onGuildUpdateIcon(GuildUpdateIconEvent event) {
        TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "Server Icon Updated")
                .setDescription("**Old Icon:** " + (event.getOldIconUrl() != null ?
                        event.getOldIconUrl() : "None") +
                        "\n**New Icon:** " + (event.getNewIconUrl() != null ?
                        event.getNewIconUrl() : "None"))
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onGuildInviteCreate(GuildInviteCreateEvent event) {
        try {
            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

            if (event.getInvite().getInviter() != null) {
                bot.getUserCache().cacheUser(event.getInvite().getInviter());
            }

            String[] userInfo = new String[]{"Unknown", "Unknown"};

            if (event.getInvite().getInviter() != null) {
                userInfo = bot.getUserCache().getUserDisplayInfo(event.getInvite().getInviter().getId());
            }

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.GREEN, "Invite Created")
                    .setDescription("**Channel:** <#" + event.getChannel().getId() + ">\n" +
                            "**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1])
                    .setTimestamp(Instant.now());

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

            bot.getDataService().logGeneral(event.getGuild().getId(), event.getUser().getId(), "GUILD_BAN",
                    "Username: " + event.getUser().getName());

            ZonedDateTime estTime = Instant.now().atZone(ZoneId.of("America/Indianapolis"));
            String formattedTime = estTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getUser().getId());

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "User Banned")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1])
                    .addField("Time (EST):", formattedTime, false)
                    .setTimestamp(Instant.now());

            if (event.getUser().getAvatarUrl() != null) {
                embed.setThumbnail(event.getUser().getAvatarUrl());
            }

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());

            bot.getDataService().logGeneral(event.getGuild().getId(), event.getUser().getId(), "GUILD_UNBAN",
                    "Username: " + event.getUser().getName());

            ZonedDateTime estTime = Instant.now().atZone(ZoneId.of("America/Indianapolis"));
            String formattedTime = estTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getUser().getId());

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.GREEN, "User Unbanned")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1])
                    .addField("Time (EST):", formattedTime, false)
                    .setTimestamp(Instant.now());

            if (event.getUser().getAvatarUrl() != null) {
                embed.setThumbnail(event.getUser().getAvatarUrl());
            }

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



