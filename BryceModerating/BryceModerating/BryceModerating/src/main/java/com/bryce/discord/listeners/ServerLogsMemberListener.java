package com.bryce.discord.listeners;

import com.bryce.discord.BryceModeratingBot;
import com.bryce.discord.utils.LoggingUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class ServerLogsMemberListener extends ListenerAdapter {

    private final BryceModeratingBot bot;

    public ServerLogsMemberListener(BryceModeratingBot bot) {
        this.bot = bot;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());
            String accountCreationTime = event.getUser().getTimeCreated()
                    .atZoneSameInstant(ZoneId.of("America/Indianapolis"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));

            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getUser().getId());

            bot.getDataService().logGeneral(event.getGuild().getId(), event.getUser().getId(), "MEMBER_JOIN",
                    "Username: " + userInfo[0] + ", Account Created: " + accountCreationTime);

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.GREEN, "Member Joined")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1])
                    .addField("Account Created", accountCreationTime, false)
                    .addField("User ID", event.getUser().getId(), false)
                    .setTimestamp(event.getMember().getTimeJoined())
                    .setThumbnail(event.getUser().getAvatarUrl());

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());
            String leaveTime = LoggingUtil.getFormattedTime(LoggingUtil.getIndianapolisZone());

            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getUser().getId());

            bot.getDataService().logGeneral(event.getGuild().getId(), event.getUser().getId(), "MEMBER_LEAVE",
                    "Username: " + userInfo[0]);

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "Member Left")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1])
                    .addField("User ID", event.getUser().getId(), false)
                    .addField("Time Left", leaveTime, false)
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
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getUser().getId());

            bot.getDataService().logGeneral(event.getGuild().getId(), event.getUser().getId(), "NICKNAME_UPDATE",
                    "Old: " + event.getOldNickname() + ", New: " + event.getNewNickname());

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "Nickname Updated")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1] + "\n" +
                            "**Old Nickname:** " + (event.getOldNickname() != null ? event.getOldNickname() : "None") + "\n" +
                            "**New Nickname:** " + (event.getNewNickname() != null ? event.getNewNickname() : "None"))
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
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getMember().getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getMember().getUser().getId());

            bot.getDataService().logGeneral(event.getGuild().getId(), event.getMember().getUser().getId(), "TIMEOUT_UPDATE",
                    "New Timeout End: " + (event.getNewTimeOutEnd() != null ? event.getNewTimeOutEnd().toString() : "None"));

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "Member Timeout Updated")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1] + "\n" +
                            "**New Timeout:** " + (event.getNewTimeOutEnd() != null ?
                            event.getNewTimeOutEnd().toString() : "None"))
                    .setTimestamp(Instant.now());

            if (event.getMember().getUser().getAvatarUrl() != null) {
                embed.setThumbnail(event.getMember().getUser().getAvatarUrl());
            }

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildMemberUpdateAvatar(GuildMemberUpdateAvatarEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getMember().getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getMember().getUser().getId());

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "Member Avatar Updated")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1] + "\n" +
                            "**New Avatar:** " + (event.getNewAvatarUrl() != null ?
                            event.getNewAvatarUrl() : "None"))
                    .setTimestamp(Instant.now());

            if (event.getNewAvatarUrl() != null) {
                embed.setThumbnail(event.getNewAvatarUrl());
            }

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getMember().getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());
            String addedRoles = event.getRoles().stream()
                    .map(role -> role.getName() + " (`" + role.getId() + "`)")
                    .collect(Collectors.joining(", "));

            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getMember().getUser().getId());

            bot.getDataService().logGeneral(event.getGuild().getId(), event.getMember().getUser().getId(), "ROLE_ADD",
                    "Roles: " + addedRoles);

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.GREEN, "Roles Added")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1] + "\n" +
                            "**Added Roles:** " + addedRoles)
                    .setTimestamp(Instant.now());

            if (event.getMember().getUser().getAvatarUrl() != null) {
                embed.setThumbnail(event.getMember().getUser().getAvatarUrl());
            }

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getMember().getUser());

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getGuild());
            String removedRoles = event.getRoles().stream()
                    .map(role -> role.getName() + " (`" + role.getId() + "`)")
                    .collect(Collectors.joining(", "));

            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getMember().getUser().getId());

            bot.getDataService().logGeneral(event.getGuild().getId(), event.getMember().getUser().getId(), "ROLE_REMOVE",
                    "Roles: " + removedRoles);

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "Roles Removed")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1] + "\n" +
                            "**Removed Roles:** " + removedRoles)
                    .setTimestamp(Instant.now());

            if (event.getMember().getUser().getAvatarUrl() != null) {
                embed.setThumbnail(event.getMember().getUser().getAvatarUrl());
            }

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



