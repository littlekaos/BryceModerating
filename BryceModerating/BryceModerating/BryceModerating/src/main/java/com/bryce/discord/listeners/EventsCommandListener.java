package com.bryce.discord.listeners;

import com.bryce.discord.models.VoiceChannelRecord;
import com.bryce.discord.utils.PermissionUtil;
import com.bryce.discord.services.VoiceChannelManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

import java.awt.Color;
import java.util.List;

public class EventsCommandListener extends ListenerAdapter {
    private final VoiceChannelManager channelManager;
    private final PermissionUtil permissionUtil;

    public EventsCommandListener(VoiceChannelManager channelManager) {
        this.channelManager = channelManager;
        this.permissionUtil = new PermissionUtil();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        switch (event.getName()) {
            case "vchelp":
                handleHelp(event);
                break;
            case "createvoice":
                handleCreateVoice(event);
                break;
            case "deletevoice":
                handleDeleteVoice(event);
                break;
            case "vcstats":
                handleVcStats(event);
                break;
            case "mychannels":
                handleMyChannels(event);
                break;
            case "activechannels":
                handleActiveChannels(event);
                break;
            case "dbinfo":
                handleDbInfo(event);
                break;
        }
    }

    private void handleCreateVoice(SlashCommandInteractionEvent event) {
        System.out.println("DEBUG: CreateVoice command used by: " + event.getUser().getName());

        boolean hasPermission = permissionUtil.hasAdminPermissions(event.getMember());
        System.out.println("DEBUG: Permission check result: " + hasPermission);

        if (!hasPermission) {
            System.out.println("DEBUG: Permission denied for user: " + event.getUser().getName());
            event.reply("You don't have permission to create voice channels. Only managers and overseers can use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        System.out.println("DEBUG: Permission granted, proceeding with channel creation");

        String name = event.getOption("name").getAsString();
        int limit = event.getOption("limit") != null ? event.getOption("limit").getAsInt() : 0;

        Guild guild = event.getGuild();
        User user = event.getUser();

        if (limit < 0) {
            event.getHook().sendMessage("The user limit cannot be negative. Please specify a number between 0-99 (0 for no limit).")
                    .queue();
            return;
        }

        if (limit > 99) {
            event.getHook().sendMessage("The maximum user limit allowed by Discord is 99. Setting limit to 99.")
                    .queue();
            limit = 99;
        }

        Category targetCategory = null;

        Member member = event.getMember();
        if (member != null && member.getVoiceState() != null && member.getVoiceState().getChannel() != null) {
            VoiceChannel currentVC = member.getVoiceState().getChannel().asVoiceChannel();
            targetCategory = currentVC.getParentCategory();
        }

        if (targetCategory == null) {
            for (Category category : guild.getCategories()) {
                String categoryName = category.getName().toLowerCase();
                if (categoryName.contains("voice") || categoryName.contains("vc") ||
                        categoryName.equals("general voice") || categoryName.equals("vcs")) {
                    targetCategory = category;
                    break;
                }
            }
        }

        if (targetCategory == null) {
            for (Category category : guild.getCategories()) {
                if (!category.getVoiceChannels().isEmpty()) {
                    targetCategory = category;
                    break;
                }
            }
        }

        if (targetCategory == null && !guild.getCategories().isEmpty()) {
            targetCategory = guild.getCategories().get(0);
        }

        final int finalLimit = limit;
        final Category finalCategory = targetCategory;

        try {
            if (finalCategory != null) {
                guild.createVoiceChannel(name)
                        .setParent(finalCategory)
                        .setUserlimit(finalLimit)
                        .queue(voiceChannel -> {
                            channelManager.addUserCreatedChannel(user.getId(), voiceChannel.getIdLong());
                            // Log to database
                            channelManager.getVoiceService().logCustomChannelCreation(voiceChannel, user);
                            event.getHook().sendMessage("Voice channel **" + name + "** created successfully in " + finalCategory.getName() + "!")
                                    .queue();
                        }, error -> {
                            event.getHook().sendMessage("Failed to create voice channel: " + error.getMessage())
                                    .queue();
                        });
            } else {
                guild.createVoiceChannel(name)
                        .setUserlimit(finalLimit)
                        .queue(voiceChannel -> {
                            channelManager.addUserCreatedChannel(user.getId(), voiceChannel.getIdLong());
                            // Log to database
                            channelManager.getVoiceService().logCustomChannelCreation(voiceChannel, user);
                            event.getHook().sendMessage("Voice channel **" + name + "** created successfully!")
                                    .queue();
                        }, error -> {
                            event.getHook().sendMessage("Failed to create voice channel: " + error.getMessage())
                                    .queue();
                        });
            }
        } catch (Exception e) {
            event.getHook().sendMessage("An unexpected error occurred: " + e.getMessage()).queue();
        }
    }

    private void handleDeleteVoice(SlashCommandInteractionEvent event) {
        if (event.getOption("channel") == null) {
            event.reply("You must specify a voice channel to delete.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        try {
            VoiceChannel channel = event.getOption("channel").getAsChannel().asVoiceChannel();
            User user = event.getUser();

            boolean isCreator = channelManager.isVoiceChannelCreator(user, channel);
            boolean isAdmin = permissionUtil.hasAdminPermissions(event.getMember());

            if (!isCreator && !isAdmin) {
                event.getHook().sendMessage("You can only delete voice channels that you created or if you have admin permissions.")
                        .queue();
                return;
            }

            channel.delete().queue(success -> {
                channelManager.deleteUserVoiceChannel(user, channel);
                event.getHook().sendMessage("Voice channel deleted successfully!").queue();
            }, error -> {
                event.getHook().sendMessage("Failed to delete voice channel: " + error.getMessage()).queue();
            });
        } catch (IllegalStateException e) {
            event.getHook().sendMessage("The specified channel is not a voice channel.").queue();
        } catch (Exception e) {
            event.getHook().sendMessage("An error occurred: " + e.getMessage()).queue();
        }
    }

    private void handleVcStats(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String type = event.getOption("type").getAsString().toLowerCase();
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : null;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.BLUE)
                .setTimestamp(java.time.Instant.now());

        switch (type) {
            case "global":
                embed.setTitle("🌍 Global Voice Channel Statistics");
                embed.setDescription(channelManager.getVoiceService().getServiceStats());
                break;
            case "server":
                embed.setTitle("🏠 Server Voice Channel Statistics");
                embed.setDescription(channelManager.getVoiceService().getGuildStats(event.getGuild().getId()));
                break;
            case "user":
                if (targetUser == null) {
                    targetUser = event.getUser();
                }
                embed.setTitle("👤 User Voice Channel Statistics");
                List<VoiceChannelRecord> userChannels = channelManager.getVoiceService()
                        .getUserCreatedChannels(targetUser.getId(), event.getGuild().getId());
                if (userChannels.isEmpty()) {
                    embed.setDescription("No voice channels found for " + targetUser.getAsMention());
                } else {
                    StringBuilder desc = new StringBuilder();
                    desc.append("**").append(targetUser.getAsMention()).append("** has created **")
                            .append(userChannels.size()).append("** voice channels:\n\n");
                    for (int i = 0; i < Math.min(userChannels.size(), 10); i++) {
                        VoiceChannelRecord record = userChannels.get(i);
                        desc.append("• **").append(record.getChannelName()).append("** (")
                                .append(record.getChannelType()).append(")\n");
                    }
                    embed.setDescription(desc.toString());
                }
                break;
            default:
                event.getHook().editOriginal("Invalid stats type. Use: global, server, or user").queue();
                return;
        }

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleMyChannels(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        List<VoiceChannelRecord> userChannels = channelManager.getVoiceService()
                .getUserCreatedChannels(event.getUser().getId(), event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Your Recent Voice Channels")
                .setColor(Color.GREEN)
                .setTimestamp(java.time.Instant.now());

        if (userChannels.isEmpty()) {
            embed.setDescription("You haven't created any voice channels yet!");
        } else {
            StringBuilder desc = new StringBuilder();
            for (int i = 0; i < Math.min(userChannels.size(), 10); i++) {
                VoiceChannelRecord record = userChannels.get(i);
                String status = record.isActive() ? "🟢 Active" : "🔴 Deleted";
                desc.append("**").append(record.getChannelName()).append("** - ").append(status).append("\n");
                desc.append("Type: ").append(record.getChannelType()).append(" | Created: ")
                        .append(record.getCreatedAt().toString(), 0, 16).append("\n\n");
            }
            embed.setDescription(desc.toString());
        }

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleActiveChannels(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        List<VoiceChannelRecord> activeChannels = channelManager.getVoiceService()
                .getActiveChannels(event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔊 Active Voice Channels")
                .setColor(Color.ORANGE)
                .setTimestamp(java.time.Instant.now());

        if (activeChannels.isEmpty()) {
            embed.setDescription("No active bot-managed voice channels found.");
        } else {
            StringBuilder desc = new StringBuilder();
            desc.append("**").append(activeChannels.size()).append("** active channels:\n\n");
            for (int i = 0; i < Math.min(activeChannels.size(), 15); i++) {
                VoiceChannelRecord record = activeChannels.get(i);
                desc.append("• **").append(record.getChannelName()).append("** by ")
                        .append(record.getCreatorName()).append(" (").append(record.getChannelType()).append(")\n");
            }
            if (activeChannels.size() > 15) {
                desc.append("\n*... and ").append(activeChannels.size() - 15).append(" more*");
            }
            embed.setDescription(desc.toString());
        }

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleDbInfo(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        String userId = event.getUser().getId();
        if (!userId.equals("689519709988585648") && !userId.equals("529480987525251082")) {
            event.getHook().editOriginal("❌ You don't have permission to view database information.").queue();
            return;
        }

        int totalChannels = channelManager.getVoiceService().getTotalChannelsCreated();
        int activeChannels = channelManager.getVoiceService().getActiveChannelCount(event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Database Information")
                .setColor(Color.CYAN)
                .addField("Total Channels Tracked", String.valueOf(totalChannels), true)
                .addField("Active Channels (This Server)", String.valueOf(activeChannels), true)
                .addField("Database Type", "PostgreSQL", true)
                .setFooter("Voice Channel Management System")
                .setTimestamp(java.time.Instant.now());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleHelp(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📚 Voice Channel Manager - Command Help")
                .setColor(Color.BLUE)
                .setThumbnail("https://cdn.discordapp.com/emojis/1000000000000000000.png");

        embed.addField("🎙️ Voice Channel Management", 
                "**`/createvoice`** - Create a new voice channel\n" +
                "  └─ `name` (required) - Channel name\n" +
                "  └─ `limit` (optional) - User limit (0 for unlimited, max 99)\n" +
                "  ℹ️ *Creates a channel in the Voice category or your current category*\n\n" +
                "**`/deletevoice`** - Delete a voice channel you created\n" +
                "  └─ `channel` (required) - The voice channel to delete\n" +
                "  ℹ️ *Only creators or admins can delete channels*\n\n" +
                "**`/mychannels`** - View your recently created voice channels\n" +
                "  └─ Shows up to 10 of your channels with creation dates\n" +
                "  ℹ️ *Private response - only you can see it*\n\n" +
                "**`/activechannels`** - View currently active bot-managed channels\n" +
                "  └─ Shows up to 15 active channels with creators\n" +
                "  ℹ️ *Helpful for seeing what's happening on the server*", 
                false);

        embed.addField("📊 Statistics & Information",
                "**`/vcstats`** - View voice channel statistics\n" +
                "  └─ `type` (required) - Choose: `global`, `server`, or `user`\n" +
                "  └─ `user` (optional) - For 'user' type, specify target user\n" +
                "  ℹ️ *View detailed statistics about voice channel usage*\n\n" +
                "**`/dbinfo`** - View database information\n" +
                "  └─ Shows total channels tracked and server-specific info\n" +
                "  ℹ️ *Owner Only command*",
                false);

        embed.addField("⚙️ Setup & Configuration",
                "**`/setup`** - Set up the Voice Channel Manager\n" +
                "  └─ Configure the bot for your server\n" +
                "  ℹ️ *Run this once to initialize the system*",
                false);

        embed.addField("❓ Quick Tips",
                "• **Permissions**: Only managers and overseers can create channels\n" +
                "• **Channel Deletion**: Channels auto-delete when all users leave\n" +
                "• **Private Responses**: Commands with 🔒 show only to you\n" +
                "• **User Limit**: Enter `0` for unlimited users in a channel\n" +
                "• **Need Help?**: Use `/help` anytime for this information",
                false);

        embed.addField("📞 Need help?",
                "Reach out to <@689519709988585648> or <@529480987525251082>",
                false);

        embed.setFooter("Voice Channel Management System | Use /help anytime for assistance")
                .setTimestamp(java.time.Instant.now());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }
}



