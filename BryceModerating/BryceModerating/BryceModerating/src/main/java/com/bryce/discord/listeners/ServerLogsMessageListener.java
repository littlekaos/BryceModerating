package com.bryce.discord.listeners;

import com.bryce.discord.BryceModeratingBot;
import com.bryce.discord.utils.LoggingUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Instant;

public class ServerLogsMessageListener extends ListenerAdapter {

    private final BryceModeratingBot bot;

    public ServerLogsMessageListener(BryceModeratingBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        Message message = event.getMessage();
        bot.getUserCache().cacheUser(event.getAuthor());
        bot.getMessageCache().cacheMessage(message, event.getAuthor().getId());

        bot.getDataService().logMessage(
                event.getGuild().getId(),
                event.getChannel().getId(),
                message.getId(),
                event.getAuthor().getId(),
                message.getContentRaw(),
                "RECEIVED"
        );
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        try {
            // Bot messages are never cached (see onMessageReceived), so uncached
            // deletes are almost always bot/system messages — skip logging them.
            String userId = bot.getMessageCache().getMessageAuthorId(event.getMessageId());
            if (userId == null) {
                return;
            }

            // Extra guard: never log our own message deletions
            if (userId.equals(event.getJDA().getSelfUser().getId())) {
                bot.getMessageCache().removeMessage(event.getMessageId());
                return;
            }

            TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
            if (logChannel == null) {
                return;
            }

            String content = bot.getMessageCache().getMessageContent(event.getMessageId());
            String userInfo = bot.getUserCache().getPlainUserInfo(userId);

            bot.getDataService().logMessage(
                    event.getGuild().getId(),
                    event.getChannel().getId(),
                    event.getMessageId(),
                    userId,
                    content,
                    "DELETED"
            );

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "Message Deleted")
                    .setDescription("**Username:** " + userInfo + "\n" +
                            "**Discord ID:** " + userId + "\n" +
                            "**Channel:** <#" + event.getChannel().getId() + ">\n" +
                            "**Content:** " + content)
                    .setTimestamp(Instant.now());

            logChannel.sendMessageEmbeds(embed.build()).queue();

            bot.getMessageCache().removeMessage(event.getMessageId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        try {
            if (event.getAuthor().isBot()) {
                return;
            }

            bot.getUserCache().cacheUser(event.getAuthor());

            TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
            if (logChannel == null) {
                return;
            }
            Message message = event.getMessage();
            String oldContent = bot.getMessageCache().getMessageContent(message.getId());
            String plainUserInfo = bot.getUserCache().getPlainUserInfo(event.getAuthor().getId());

            bot.getMessageCache().cacheMessage(message, event.getAuthor().getId());
            String newContent = bot.getMessageCache().getMessageContent(message.getId());

            bot.getDataService().logMessage(
                    event.getGuild().getId(),
                    event.getChannel().getId(),
                    message.getId(),
                    event.getAuthor().getId(),
                    newContent,
                    "EDITED (Old: " + oldContent + ")"
            );

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.YELLOW, "Message Edited")
                    .setDescription("**Username:** " + plainUserInfo + "\n" +
                            "**Discord ID:** " + event.getAuthor().getId() + "\n" +
                            "**Channel:** <#" + event.getChannel().getId() + ">\n" +
                            "**Old Content:** " + oldContent + "\n" +
                            "**New Content:** " + newContent)
                    .setTimestamp(message.getTimeCreated());

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
        if (logChannel == null) {
            return;
        }

        for (String messageId : event.getMessageIds()) {
            String content = bot.getMessageCache().getMessageContent(messageId);
            String userId = bot.getMessageCache().getMessageAuthorId(messageId);
            bot.getDataService().logMessage(
                    event.getGuild().getId(),
                    event.getChannel().getId(),
                    messageId,
                    userId,
                    content,
                    "BULK_DELETED"
            );
        }

        EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "Bulk Message Delete")
                .setDescription("**Channel:** " + event.getChannel().getName() +
                        "\n**Messages Deleted:** " + event.getMessageIds().size())
                .setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();

        bot.getMessageCache().removeMessages(event.getMessageIds());
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        try {
            if (event.getUser() != null) {
                bot.getUserCache().cacheUser(event.getUser());
            }

            TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
            if (logChannel == null) {
                return;
            }
            String emojiDisplay = event.getReaction().getEmoji().getAsReactionCode();
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getUserId());

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.GREEN, "Reaction Added")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1] + "\n" +
                            "**Channel:** <#" + event.getChannel().getId() + ">\n" +
                            "**Message ID:** " + event.getMessageId() + "\n" +
                            "**Reaction:** " + emojiDisplay)
                    .setTimestamp(Instant.now());

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        try {
            if (event.getUser() != null) {
                bot.getUserCache().cacheUser(event.getUser());
            }

            TextChannel logChannel = LoggingUtil.findServerLogsChannel(event.getGuild(), bot.getDataService());
            if (logChannel == null) {
                return;
            }
            String emojiDisplay = event.getReaction().getEmoji().getAsReactionCode();
            String[] userInfo = bot.getUserCache().getUserDisplayInfo(event.getUserId());

            EmbedBuilder embed = LoggingUtil.createEmbed(Color.RED, "Reaction Removed")
                    .setDescription("**Username:** " + userInfo[0] + "\n" +
                            "**Discord ID:** " + userInfo[1] + "\n" +
                            "**Channel:** <#" + event.getChannel().getId() + ">\n" +
                            "**Message ID:** " + event.getMessageId() + "\n" +
                            "**Reaction:** " + emojiDisplay)
                    .setTimestamp(Instant.now());

            logChannel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



