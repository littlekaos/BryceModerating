package com.bryce.discord.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.Color;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LoggingUtil {

    public static TextChannel ensureLogChannel(Guild guild) {
        TextChannel logChannel = guild.getTextChannelsByName("server-logs", true).stream().findFirst().orElse(null);
        if (logChannel == null) {
            logChannel = guild.createTextChannel("server-logs").complete();
            logChannel.getManager().setTopic("This channel will be used for server logs.").queue();
        }
        return logChannel;
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



