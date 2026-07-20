package com.bryce.discord.listeners;

import com.bryce.discord.BryceModeratingBot;
import com.bryce.discord.utils.LoggingUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Instant;

public class ServerLogsRoleListener extends ListenerAdapter {

    private final BryceModeratingBot bot;

    public ServerLogsRoleListener(BryceModeratingBot bot) {
        this.bot = bot;
    }

    @Override
    public void onRoleCreate(RoleCreateEvent event) {
        TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
        if (logChannel == null) {
            return;
        }

        bot.getDataService().logGeneral(event.getGuild().getId(), null, "ROLE_CREATE",
                "Role: " + event.getRole().getName() + " (" + event.getRole().getId() + ")");

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.GREEN, "Role Created")
                .setDescription("**Role:** " + event.getRole().getName() + "\n" +
                        "**Role ID:** " + event.getRole().getId())
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
        if (logChannel == null) {
            return;
        }

        bot.getDataService().logGeneral(event.getGuild().getId(), null, "ROLE_DELETE",
                "Role: " + event.getRole().getName() + " (" + event.getRole().getId() + ")");

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "Role Deleted")
                .setDescription("**Role:** " + event.getRole().getName() + "\n" +
                        "**Role ID:** " + event.getRole().getId())
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onRoleUpdateName(RoleUpdateNameEvent event) {
        TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
        if (logChannel == null) {
            return;
        }

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "Role Name Updated")
                .setDescription("**Role ID:** " + event.getRole().getId() + "\n" +
                        "**Old Name:** " + event.getOldName() + "\n" +
                        "**New Name:** " + event.getNewName())
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onRoleUpdateColor(RoleUpdateColorEvent event) {
        TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
        if (logChannel == null) {
            return;
        }

        String oldColorHex = event.getOldColor() != null ?
                String.format("#%06X", (0xFFFFFF & event.getOldColor().getRGB())) : "None";
        String newColorHex = event.getNewColor() != null ?
                String.format("#%06X", (0xFFFFFF & event.getNewColor().getRGB())) : "None";

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "Role Color Updated")
                .setDescription("**Role:** " + event.getRole().getName() + "\n" +
                        "**Role ID:** " + event.getRole().getId() + "\n" +
                        "**Old Color:** " + oldColorHex + "\n" +
                        "**New Color:** " + newColorHex)
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent event) {
        TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
        if (logChannel == null) {
            return;
        }

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.ORANGE, "Role Permissions Updated")
                .setDescription("**Role:** " + event.getRole().getName() + "\n" +
                        "**Role ID:** " + event.getRole().getId() + "\n" +
                        "**Permission Changes:** Role permissions were modified")
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }
}



