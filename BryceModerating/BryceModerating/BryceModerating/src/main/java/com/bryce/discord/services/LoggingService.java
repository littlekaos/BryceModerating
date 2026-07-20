package com.bryce.discord.services;

import com.bryce.discord.utils.LoggingUtil;
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
    private final DataService dataService;
    private final Map<String, TextChannel> logChannelCache = new ConcurrentHashMap<>();

    public LoggingService(DataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Resolve a log channel by configured name. Never creates channels.
     */
    public TextChannel getLogChannel(Guild guild, String channelName) {
        if (guild == null || channelName == null) {
            return null;
        }

        String cacheKey = guild.getId() + ":" + channelName;
        TextChannel cached = logChannelCache.get(cacheKey);
        if (cached != null) {
            // Verify still exists
            if (guild.getTextChannelById(cached.getId()) != null) {
                return cached;
            }
            logChannelCache.remove(cacheKey);
        }

        TextChannel logChannel;
        if (ConfigService.MODERATION_LOG_CHANNEL_NAME.equalsIgnoreCase(channelName)) {
            logChannel = LoggingUtil.findModerationLogsChannel(guild, dataService);
        } else if (ConfigService.PURGE_LOG_CHANNEL_NAME.equalsIgnoreCase(channelName)) {
            logChannel = LoggingUtil.findServerLogsChannel(guild, dataService);
        } else {
            logChannel = guild.getTextChannelsByName(channelName, true).stream().findFirst().orElse(null);
        }

        if (logChannel != null) {
            logChannelCache.put(cacheKey, logChannel);
        }
        return logChannel;
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

    public void invalidateCache(Guild guild) {
        if (guild == null) return;
        String prefix = guild.getId() + ":";
        logChannelCache.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
