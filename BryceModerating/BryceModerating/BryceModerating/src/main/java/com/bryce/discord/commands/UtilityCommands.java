package com.bryce.discord.commands;

import com.bryce.discord.services.ConfigService;
import com.bryce.discord.services.DataService;
import com.bryce.discord.services.LoggingService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class UtilityCommands {
    private final DataService dataService;
    private final ConfigService configService;
    private final LoggingService loggingService;

    public DataService getDataService() {
        return dataService;
    }

    public ConfigService getConfigService() {
        return configService;
    }


    private static final String[] AUTHORIZED_USER_IDS = {"529480987525251082", "689519709988585648"};

    public UtilityCommands(DataService dataService, ConfigService configService) {
        this.dataService = dataService;
        this.configService = configService;
        this.loggingService = new LoggingService();
    }

    public void handlePurge(SlashCommandInteractionEvent event) {
        int amount = event.getOption("amount").getAsInt();
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : null;

        if (amount < 1 || amount > 1000) {
            event.reply("Please provide a number between 1 and 1000.").setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();

        event.deferReply(true).queue();

        if (targetUser != null) {
            searchAndDeleteUserMessages(channel, event, targetUser, amount);
        } else {
            channel.getHistory().retrievePast(Math.min(amount, 100)).queue(messages -> {
                if (messages.isEmpty()) {
                    event.getHook().sendMessage("No messages found to delete.").queue();
                    return;
                }

                bulkDeleteMessages(channel, messages, 0, () -> {
                    EmbedBuilder purgeEmbed = new EmbedBuilder()
                            .setTitle("🗑️ Messages Purged")
                            .setDescription(String.format("**%d** messages were purged in %s",
                                    messages.size(), channel.getAsMention()))
                            .addField("Moderator", event.getUser().getName(), true)
                            .setColor(Color.BLUE)
                            .setTimestamp(Instant.now());

                    event.getHook().sendMessage("Successfully deleted " + messages.size() + " messages.").queue();

                    TextChannel logChannel = loggingService.getLogChannel(event.getGuild(), ConfigService.MODERATION_LOG_CHANNEL_NAME);
                    if (logChannel != null) {
                        logChannel.sendMessageEmbeds(purgeEmbed.build()).queue();
                    } else {
                        event.getChannel().sendMessageEmbeds(purgeEmbed.build()).queue();
                    }
                });
            });
        }
    }

    private void searchAndDeleteUserMessages(TextChannel channel, SlashCommandInteractionEvent event, User targetUser, int targetAmount) {
        List<Message> userMessages = new ArrayList<>();
        int batchSize = 100;
        searchUserMessagesBatch(channel, event, targetUser, targetAmount, userMessages, batchSize, 0);
    }

    private void searchUserMessagesBatch(TextChannel channel, SlashCommandInteractionEvent event, User targetUser, int targetAmount,
                                          List<Message> userMessages, int batchSize, int searches) {
        if (userMessages.size() >= targetAmount || searches >= 20) {
            List<Message> toDelete = userMessages.stream().limit(targetAmount).collect(Collectors.toList());

            if (toDelete.isEmpty()) {
                event.getHook().sendMessage("No messages found to delete.").queue();
                return;
            }

            bulkDeleteMessages(channel, toDelete, 0, () -> {
                EmbedBuilder purgeEmbed = new EmbedBuilder()
                        .setTitle("🗑️ Messages Purged")
                        .setDescription(String.format("**%d** messages from **%s** were purged in %s",
                                toDelete.size(), targetUser.getName(), channel.getAsMention()))
                        .addField("Moderator", event.getUser().getName(), true)
                        .setColor(Color.BLUE)
                        .setTimestamp(Instant.now());

                event.getHook().sendMessage("Successfully deleted " + toDelete.size() + " messages.").queue();

                TextChannel logChannel = loggingService.getLogChannel(event.getGuild(), ConfigService.MODERATION_LOG_CHANNEL_NAME);
                if (logChannel != null) {
                    logChannel.sendMessageEmbeds(purgeEmbed.build()).queue();
                } else {
                    event.getChannel().sendMessageEmbeds(purgeEmbed.build()).queue();
                }
            });
            return;
        }

        int retrieveAmount = Math.min(100, 1000);
        
        channel.getHistory().retrievePast(retrieveAmount).queue(messages -> {
            List<Message> userBatch = messages.stream()
                    .filter(message -> message.getAuthor().getId().equals(targetUser.getId()))
                    .collect(Collectors.toList());
            
            List<String> existingIds = userMessages.stream()
                    .map(Message::getId)
                    .collect(Collectors.toList());
            
            for (Message msg : userBatch) {
                if (!existingIds.contains(msg.getId())) {
                    userMessages.add(msg);
                }
            }

            searchUserMessagesBatch(channel, event, targetUser, targetAmount, userMessages, batchSize, searches + 1);
        });
    }

    private void bulkDeleteMessages(TextChannel channel, List<Message> messages, int index, Runnable onComplete) {
        if (messages.isEmpty()) {
            onComplete.run();
            return;
        }

        List<CompletableFuture<Void>> deleteFutures = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i += 100) {
            int endIndex = Math.min(i + 100, messages.size());
            List<Message> batch = messages.subList(i, endIndex);
            
            CompletableFuture<Void> future = new CompletableFuture<>();
            deleteFutures.add(future);
            
            if (batch.size() == 1) {
                batch.get(0).delete().queue(
                    success -> future.complete(null),
                    error -> future.completeExceptionally(error)
                );
            } else {
                channel.deleteMessages(batch).queue(
                    success -> future.complete(null),
                    error -> future.completeExceptionally(error)
                );
            }
        }
        
        CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0]))
            .thenRun(onComplete);
    }

    public void handleSaveSystem(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        boolean isAuthorized = false;

        for (String id : AUTHORIZED_USER_IDS) {
            if (id.equals(userId)) {
                isAuthorized = true;
                break;
            }
        }
        if (!isAuthorized) {
            event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        System.out.println("Manual save triggered by " + event.getUser().getName() + " (ID: " + userId + ")");
        try {
            dataService.saveAllData();
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> {
                event.getHook().editOriginal("💾 All moderation data has been saved successfully.").queue(
                        success -> System.out.println("Save command response sent successfully"),
                        error -> System.err.println("Error sending save command response: " + error.getMessage())
                );
            });
        } catch (Exception e) {
            System.err.println("Error during save operation: " + e.getMessage());
            e.printStackTrace();

            event.getHook().editOriginal("❌ An error occurred while saving data: " + e.getMessage()).queue(
                    success -> System.out.println("Error response sent successfully"),
                    error -> System.err.println("Error sending error response: " + error.getMessage())
            );
        }
    }

    public void handleExportDb(SlashCommandInteractionEvent event) {
        List<String> allowedUserIds = List.of(
                "529480987525251082",
                "689519709988585648"
        );

        if (!allowedUserIds.contains(event.getUser().getId())) {
            event.reply("❌ You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        File dbFile = new File("modbot.db");

        if (!dbFile.exists()) {
            event.reply("❌ modbot.db not found!").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        event.getHook().sendMessage("📤 Exporting modbot.db...")
                .addFiles(FileUpload.fromData(dbFile, "modbot.db"))
                .queue();
    }

    public static boolean isUserAuthorized(String userId) {
        for (String id : AUTHORIZED_USER_IDS) {
            if (id.equals(userId)) {
                return true;
            }
        }
        return false;
    }

    public void handleSetModRoles(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("❌ You need **Administrator** permissions to use this command.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        var role = event.getOption("role").getAsRole();

        if (configService.getModeratorRoles().contains(role.getId())) {
            event.getHook().sendMessage("⚠️ " + role.getName() + " is already a moderator role.").queue();
            return;
        }

        configService.addModeratorRole(role.getId());

        dataService.saveBotRolesToDatabase(configService.getModeratorRoles(), configService.getAdminRoles());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛡️ Moderator Role Added")
                .setDescription("✅ " + role.getName() + " (" + role.getId() + ") is now a moderator role")
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
        System.out.println("Moderator role added by " + event.getUser().getName());
    }

    public void handleSetAdminRoles(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("❌ You need **Administrator** permissions to use this command.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        var role = event.getOption("role").getAsRole();

        if (configService.getAdminRoles().contains(role.getId())) {
            event.getHook().sendMessage("⚠️ " + role.getName() + " is already an admin role.").queue();
            return;
        }

        configService.addAdminRole(role.getId());

        dataService.saveBotRolesToDatabase(configService.getModeratorRoles(), configService.getAdminRoles());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👑 Admin Role Added")
                .setDescription("✅ " + role.getName() + " (" + role.getId() + ") is now an admin role")
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
        System.out.println("Admin role added by " + event.getUser().getName());
    }

    public void handleHelp(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        EmbedBuilder helpEmbed = new EmbedBuilder()
                .setTitle("🛡️ Bryce Moderating - Integrated Bot Help")
                .setColor(Color.BLUE)
                .setDescription("The moderation, voice management, and logging systems are now integrated into one bot.")
                
                .addField("⚠️ Moderation System", 
                        "**`/warn`** <user> <reason> [evidence]\n" +
                        "**`/mute`** <user> <reason> [duration] | **`/unmute`**\n" +
                        "**`/timeout`** <user> <reason> [duration] | **`/untimeout`**\n" +
                        "**`/ban`** <user> <reason> [delete_days] | **`/unban`** <id>\n" +
                        "**`/kick`** <user> <reason> | **`/purge`** <amount> [user]\n" +
                        "**`/reason`** <id> - Check ban reasons", false)

                .addField("🎙️ Voice Channel Management", 
                        "**`/createvoice`** <name> [limit]\n" +
                        "**`/deletevoice`** <channel>\n" +
                        "**`/mychannels`** - Your recent channels\n" +
                        "**`/activechannels`** - List active managed channels\n" +
                        "**`/vcstats`** <type> [user] - Global/Server/User stats\n" +
                        "**`/setup`** - Interactive voice manager configuration", false)

                .addField("🔒 Channel Restrictions",
                        "**`/restrict`** <channel> <type>\n" +
                        "**`/unrestrict`** <channel> <type>\n" +
                        "**`/restrict-setup`** - Interactive restriction setup", false)

                .addField("⚙️ System & Administration", 
                        "**`/setmodroles`** | **`/setadminroles`**\n" +
                        "**`/setmuterole`** <role>\n" +
                        "**`/reload-members`** - Force refresh member cache\n" +
                        "**`/cache-stats`** - View bot performance metrics\n" +
                        "**`/savemoderationsystem`** - Force manual data save", false)

                .addField("📝 Notes", "• Moderation actions are logged to the configured log channel.\n• Setup requires **Administrator** permissions.", false)
                .addField("📞 Support", "Contact <@689519709988585648> or <@529480987525251082>", false)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(helpEmbed.build()).queue();
    }

    public void handleRemoveModRoles(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("❌ You need **Administrator** permissions to use this command.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        var role = event.getOption("role").getAsRole();

        if (!configService.getModeratorRoles().contains(role.getId())) {
            event.getHook().sendMessage("⚠️ " + role.getName() + " is not a moderator role.").queue();
            return;
        }

        configService.removeModeratorRole(role.getId());

        dataService.saveBotRolesToDatabase(configService.getModeratorRoles(), configService.getAdminRoles());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛡️ Moderator Role Removed")
                .setDescription("✅ " + role.getName() + " (" + role.getId() + ") is no longer a moderator role")
                .setColor(Color.RED)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
        System.out.println("Moderator role removed by " + event.getUser().getName());
    }

    public void handleRemoveAdminRoles(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("❌ You need **Administrator** permissions to use this command.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        var role = event.getOption("role").getAsRole();

        if (!configService.getAdminRoles().contains(role.getId())) {
            event.getHook().sendMessage("⚠️ " + role.getName() + " is not an admin role.").queue();
            return;
        }

        configService.removeAdminRole(role.getId());

        dataService.saveBotRolesToDatabase(configService.getModeratorRoles(), configService.getAdminRoles());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👑 Admin Role Removed")
                .setDescription("✅ " + role.getName() + " (" + role.getId() + ") is no longer an admin role")
                .setColor(Color.RED)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
        System.out.println("Admin role removed by " + event.getUser().getName());
    }
}


