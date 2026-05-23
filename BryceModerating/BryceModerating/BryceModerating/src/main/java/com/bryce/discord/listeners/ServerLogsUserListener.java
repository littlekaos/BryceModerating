package com.bryce.discord.listeners;

import com.bryce.discord.BryceModeratingBot;
import com.bryce.discord.utils.LoggingUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Instant;

public class ServerLogsUserListener extends ListenerAdapter {

    private final BryceModeratingBot bot;

    public ServerLogsUserListener(BryceModeratingBot bot) {
        this.bot = bot;
    }

    @Override
    public void onUserUpdateName(UserUpdateNameEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getUser());

            if (event.getUser().getMutualGuilds().isEmpty()) return;

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getUser().getMutualGuilds().get(0));
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getUser().getId());

            for (net.dv8tion.jda.api.entities.Guild guild : event.getUser().getMutualGuilds()) {
                bot.getDataService().logGeneral(guild.getId(), event.getUser().getId(), "USER_NAME_UPDATE",
                        "Old: " + event.getOldName() + ", New: " + event.getNewName());
            }

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "User Name Updated")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1] + "\n" +
                            "**Old Name:** " + event.getOldName() + "\n" +
                            "**New Name:** " + event.getNewName())
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
    public void onUserUpdateAvatar(UserUpdateAvatarEvent event) {
        try {
            bot.getUserCache().cacheUser(event.getUser());

            if (event.getUser().getMutualGuilds().isEmpty()) return;

            TextChannel logChannel = LoggingUtil.ensureLogChannel(event.getUser().getMutualGuilds().get(0));
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getUser().getId());

            for (net.dv8tion.jda.api.entities.Guild guild : event.getUser().getMutualGuilds()) {
                bot.getDataService().logGeneral(guild.getId(), event.getUser().getId(), "USER_AVATAR_UPDATE",
                        "Avatar URL updated");
            }

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "User Avatar Updated")
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
}



