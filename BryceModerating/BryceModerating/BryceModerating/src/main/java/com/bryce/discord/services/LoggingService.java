package com.bryce.discord.services;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LoggingService {
    private final Map<String, TextChannel> logChannelCache = new ConcurrentHashMap<>();

    public TextChannel getLogChannel(Guild guild, String channelName) {
        String cacheKey = guild.getId() + ":" + channelName;
        if (logChannelCache.containsKey(cacheKey)) {
            return logChannelCache.get(cacheKey);
        }

        TextChannel logChannel = guild.getTextChannelsByName(channelName, true).stream().findFirst().orElse(null);
        if (logChannel != null) {
            logChannelCache.put(cacheKey, logChannel);
            return logChannel;
        }

        guild.createTextChannel(channelName).queue(newChannel -> {
            String topic;
            switch (channelName) {
                case "staff-strikes":
                    topic = "This channel logs all staff strike actions.";
                    break;
                case "moderation-logs":
                    topic = "This channel logs all moderation actions (warn, mute, timeout, etc).";
                    break;
                default:
                    topic = "Server logging channel.";
            }

            newChannel.getManager().setTopic(topic).queue(success -> {
                logChannelCache.put(cacheKey, newChannel);
            });
        });

        return null;
    }

    public void logModAction(Guild guild, String channelName, MessageEmbed embed) {
        TextChannel logChannel = getLogChannel(guild, channelName);
        if (logChannel != null) {
            logChannel.sendMessageEmbeds(embed).queue();
        }
    }

    public void logModActionWithFile(Guild guild, String channelName, MessageEmbed embed,
                                     InputStream fileData, String fileName) {
        TextChannel logChannel = getLogChannel(guild, channelName);
        if (logChannel != null) {
            FileUpload fileUpload = FileUpload.fromData(fileData, fileName);
            logChannel.sendMessageEmbeds(embed).queue(message -> {
                logChannel.sendFiles(fileUpload).queue();
            });
        }
    }

    public void sendWarningEmbed(TextChannel channel, String reason, Consumer<Void> callback) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚠️ Warning")
                .setDescription(reason)
                .setColor(Color.ORANGE)
                .setFooter("This message will self-destruct in 5 seconds", null)
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build())
                .queue(message -> message.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS,
                        success -> {
                            if (callback != null) {
                                callback.accept(null);
                            }
                        },
                        error -> {
                            if (callback != null) {
                                callback.accept(null);
                            }
                        }));
    }
}


