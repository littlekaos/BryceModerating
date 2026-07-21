package com.bryce.discord.commands;

import com.bryce.discord.analytics.ActionType;
import com.bryce.discord.analytics.ModerationAnalytics;
import com.bryce.discord.models.ModAction;
import com.bryce.discord.models.WarnRecord;
import com.bryce.discord.services.ConfigService;
import com.bryce.discord.services.DataService;
import com.bryce.discord.services.LoggingService;
import com.bryce.discord.utils.FormatUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ModerationCommands {
    private final DataService dataService;
    private final ConfigService configService;
    private final LoggingService loggingService;
    private final ModerationAnalytics analytics;

    public ModerationCommands(DataService dataService, ConfigService configService, ModerationAnalytics analytics) {
        this.dataService = dataService;
        this.configService = configService;
        this.loggingService = new LoggingService(dataService);
        this.analytics = analytics;
    }

    public void handleWarn(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(event.getMember())) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        net.dv8tion.jda.api.entities.Message.Attachment evidence = event.getOption("evidence") != null ?
                event.getOption("evidence").getAsAttachment() : null;

        dataService.saveCommandLog(event.getUser().getId(), event.getUser().getName(), event.getName());

        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!configService.canModerate(event.getMember(), targetMember)) {
                event.getHook().editOriginal("❌ You cannot moderate other staff members.").queue();
                return;
            }

            WarnRecord warnRecord = new WarnRecord(
                    event.getGuild().getId(),
                    targetUser.getId(),
                    event.getUser().getId(),
                    reason,
                    System.currentTimeMillis()
            );
            dataService.addWarning(warnRecord);
            int currentWarnings = dataService.getWarningsForUser(event.getGuild().getId(), targetUser.getId()).size();

            analytics.recordAction(event.getGuild().getId(), ActionType.WARN, event.getUser(), targetUser, reason, 0, currentWarnings);

            EmbedBuilder warnEmbed = new EmbedBuilder()
                    .setTitle("⚠️ Warning Issued")
                    .setDescription(String.format("A warning has been issued to **%s** (ID: %s)", targetUser.getName(), targetUser.getId()))
                    .addField("Reason", reason, false)
                    .addField("Total Warnings", String.valueOf(currentWarnings), true)
                    .addField("Moderator", String.format("%s (ID: %s)", event.getUser().getName(), event.getUser().getId()), true)
                    .setColor(Color.YELLOW)
                    .setTimestamp(Instant.now());

            TextChannel logChannel = loggingService.getLogChannel(event.getGuild(), ConfigService.MODERATION_LOG_CHANNEL_NAME);

            targetUser.openPrivateChannel().queue(channel -> {
                EmbedBuilder userWarnEmbed = new EmbedBuilder()
                        .setTitle("⚠️ You've Received a Warning")
                        .setDescription(String.format("You have been warned in **%s**", event.getGuild().getName()))
                        .addField("Reason", reason, false)
                        .setColor(Color.YELLOW)
                        .setTimestamp(Instant.now());
                channel.sendMessageEmbeds(userWarnEmbed.build()).queue(
                        success -> {},
                        error -> System.out.println("Could not DM user " + targetUser.getName() + " about their warning")
                );
            });

            event.getHook().editOriginal("Warning issued successfully.").queue();

            if (logChannel != null) {
                if (evidence != null) {
                    evidence.getProxy().download().thenAccept(data -> {
                        FileUpload fileUpload = FileUpload.fromData(data, evidence.getFileName());
                        warnEmbed.setThumbnail(targetUser.getEffectiveAvatarUrl());
                        logChannel.sendMessageEmbeds(warnEmbed.build()).queue(message -> {
                            logChannel.sendFiles(fileUpload).queue();
                        });
                    });
                } else {
                    warnEmbed.setThumbnail(targetUser.getEffectiveAvatarUrl());
                    logChannel.sendMessageEmbeds(warnEmbed.build()).queue();
                }
            } else {
                if (evidence != null) {
                    evidence.getProxy().download().thenAccept(data -> {
                        FileUpload fileUpload = FileUpload.fromData(data, evidence.getFileName());
                        warnEmbed.setThumbnail(targetUser.getEffectiveAvatarUrl());
                        event.getChannel().sendMessageEmbeds(warnEmbed.build()).queue(message -> {
                            event.getChannel().sendFiles(fileUpload).queue();
                        });
                    });
                } else {
                    warnEmbed.setThumbnail(targetUser.getEffectiveAvatarUrl());
                    event.getChannel().sendMessageEmbeds(warnEmbed.build()).queue();
                }
            }
        }, error -> {
            event.getHook().editOriginal("❌ Error: Cannot find the user in this server.").queue();
        });
    }

    public void handleMute(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(event.getMember())) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        String durationStr = event.getOption("duration") != null ?
                event.getOption("duration").getAsString() : null;
        
        final Integer duration;
        if (durationStr != null) {
            long parsedDuration = FormatUtils.parseDurationToMinutes(durationStr);
            if (parsedDuration < 0) {
                event.getHook().sendMessage("❌ Invalid duration format. Use formats like: 1h, 7d, 2w, 30m (max: 28d)").queue();
                return;
            }
            duration = (int) parsedDuration;
        } else {
            duration = null;
        }

        dataService.saveCommandLog(event.getUser().getId(), event.getUser().getName(), event.getName());

        Guild guild = event.getGuild();
        String muteRoleId = dataService.getMuteRoleId(guild.getId());
        
        if (muteRoleId == null) {
            event.getHook().sendMessage("❌ No mute role for this server. Run `/adminsetup` to create or register one.").queue();
            return;
        }

        Role muteRole = guild.getRoleById(muteRoleId);

        if (muteRole == null) {
            event.getHook().sendMessage("❌ The configured mute role no longer exists. Run `/adminsetup` to create or register a new one.").queue();
            return;
        }

        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {

            if (!configService.canModerate(event.getMember(), targetMember)) {
                event.getHook().sendMessage("❌ You cannot moderate other staff members.").queue();
                return;
            }

            if (!guild.getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("Error: I cannot mute this user due to role hierarchy.").queue();
                return;
            }

            guild.addRoleToMember(targetMember, muteRole).queue(
                    success -> {
                        String durationText = duration != null ?
                                duration + " minutes" : "Permanent";

                        analytics.recordAction(event.getGuild().getId(), ActionType.MUTE, event.getUser(), targetUser, reason,
                                duration != null ? duration : 0, 0);

                        EmbedBuilder muteEmbed = new EmbedBuilder()
                                .setTitle("🔇 User Muted")
                                .setDescription(String.format("**%s** has been muted", targetUser.getName()))
                                .addField("Reason", reason, false)
                                .addField("Duration", durationText, true)
                                .addField("Moderator", event.getUser().getName(), true)
                                .setColor(new Color(128, 0, 128))
                                .setTimestamp(Instant.now())
                                .setThumbnail(targetUser.getEffectiveAvatarUrl());

                        TextChannel logChannel = loggingService.getLogChannel(guild, ConfigService.MODERATION_LOG_CHANNEL_NAME);
                        if (logChannel != null) {
                            logChannel.sendMessageEmbeds(muteEmbed.build()).queue();
                        } else {
                            event.getChannel().sendMessageEmbeds(muteEmbed.build()).queue();
                        }

                        event.getHook().sendMessage("User has been muted successfully.").queue();

                        if (duration != null) {
                            long unmuteTime = System.currentTimeMillis() + (duration * 60 * 1000L);
                            dataService.addMute(guild.getId(), targetUser.getId(), unmuteTime);
                        }
                    },
                    error -> {
                        event.getHook().sendMessage("Error: Could not mute the user. " + error.getMessage()).queue();
                    }
            );
        }, error -> {
            event.getHook().sendMessage("Error: Cannot find the user in this server. They may have left or been banned.").queue();
        });
    }

    public void handleUnmute(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(event.getMember())) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();

        dataService.saveCommandLog(event.getUser().getId(), event.getUser().getName(), event.getName());

        Guild guild = event.getGuild();
        String muteRoleId = dataService.getMuteRoleId(guild.getId());
        
        if (muteRoleId == null) {
            event.getHook().sendMessage("❌ No mute role for this server. Run `/adminsetup` to create or register one.").queue();
            return;
        }

        Role muteRole = guild.getRoleById(muteRoleId);

        if (muteRole == null) {
            event.getHook().sendMessage("❌ The configured mute role no longer exists. Run `/adminsetup` to create or register a new one.").queue();
            return;
        }

        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {

            if (!configService.canModerate(event.getMember(), targetMember)) {
                event.getHook().sendMessage("❌ You cannot moderate other staff members.").queue();
                return;
            }

            if (!guild.getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("Error: I cannot unmute this user due to role hierarchy.").queue();
                return;
            }

            if (!targetMember.getRoles().contains(muteRole)) {
                event.getHook().sendMessage("This user is not currently muted.").queue();
                return;
            }

            guild.removeRoleFromMember(targetMember, muteRole).queue(
                    success -> {
                        dataService.removeMute(guild.getId(), targetUser.getId());
                        analytics.recordAction(event.getGuild().getId(), ActionType.UNMUTE, event.getUser(), targetUser, reason, 0, 0);

                        EmbedBuilder unmuteEmbed = new EmbedBuilder()
                                .setTitle("🔊 User Unmuted")
                                .setDescription(String.format("**%s** has been unmuted", targetUser.getName()))
                                .addField("Reason", reason, false)
                                .addField("Moderator", event.getUser().getName(), true)
                                .setColor(Color.GREEN)
                                .setTimestamp(Instant.now())
                                .setThumbnail(targetUser.getEffectiveAvatarUrl());

                        TextChannel logChannel = loggingService.getLogChannel(guild, ConfigService.MODERATION_LOG_CHANNEL_NAME);
                        if (logChannel != null) {
                            logChannel.sendMessageEmbeds(unmuteEmbed.build()).queue();
                        } else {
                            event.getChannel().sendMessageEmbeds(unmuteEmbed.build()).queue();
                        }

                        event.getHook().sendMessage("User has been unmuted successfully.").queue();
                    },
                    error -> {
                        event.getHook().sendMessage("Error: Could not unmute the user. " + error.getMessage()).queue();
                    }
            );
        }, error -> {
            event.getHook().sendMessage("Error: Cannot find the user in this server. They may have left or been banned.").queue();
        });
    }

    public void handleTimeout(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(event.getMember())) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        String durationStr = event.getOption("duration") != null ?
                event.getOption("duration").getAsString() : null;
        
        final int duration;
        if (durationStr != null) {
            long parsedDuration = FormatUtils.parseDurationToMinutes(durationStr);
            if (parsedDuration < 0) {
                event.getHook().sendMessage("❌ Invalid duration format. Use formats like: 1h, 7d, 2w, 30m (max: 28d)").queue();
                return;
            }
            duration = (int) parsedDuration;
        } else {
            duration = 60;
        }

        dataService.saveCommandLog(event.getUser().getId(), event.getUser().getName(), event.getName());

        final int finalDuration = duration;

        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {

            if (!configService.canModerate(event.getMember(), targetMember)) {
                event.getHook().sendMessage("❌ You cannot moderate other staff members.").queue();
                return;
            }

            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("Error: I cannot timeout this user due to role hierarchy.").queue();
                return;
            }

            targetMember.timeoutFor(java.time.Duration.ofMinutes(finalDuration)).reason(reason).queue(
                    success -> {

                        analytics.recordAction(event.getGuild().getId(), ActionType.TIMEOUT, event.getUser(), targetUser, reason, finalDuration, 0);

                        EmbedBuilder timeoutEmbed = new EmbedBuilder()
                                .setTitle("⏰ User Timed Out")
                                .setDescription(String.format("**%s** has been timed out", targetUser.getName()))
                                .addField("Reason", reason, false)
                                .addField("Duration", finalDuration + " minutes", true)
                                .addField("Moderator", event.getUser().getName(), true)
                                .setColor(new Color(255, 165, 0))
                                .setTimestamp(Instant.now())
                                .setThumbnail(targetUser.getEffectiveAvatarUrl());

                        TextChannel logChannel = loggingService.getLogChannel(event.getGuild(), ConfigService.MODERATION_LOG_CHANNEL_NAME);
                        if (logChannel != null) {
                            logChannel.sendMessageEmbeds(timeoutEmbed.build()).queue();
                        } else {
                            event.getChannel().sendMessageEmbeds(timeoutEmbed.build()).queue();
                        }

                        event.getHook().sendMessage("User has been timed out successfully.").queue();
                    },
                    error -> {
                        event.getHook().sendMessage("Error: Could not timeout the user. " + error.getMessage()).queue();
                    }
            );
        }, error -> {
            event.getHook().sendMessage("Error: Cannot find the user in this server. They may have left or been banned.").queue();
        });
    }

    public void handleUntimeout(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(event.getMember())) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();

        dataService.saveCommandLog(event.getUser().getId(), event.getUser().getName(), event.getName());

        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {

            if (!configService.canModerate(event.getMember(), targetMember)) {
                event.getHook().sendMessage("❌ You cannot moderate other staff members.").queue();
                return;
            }

            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("Error: I cannot remove the timeout from this user due to role hierarchy.").queue();
                return;
            }

            if (targetMember.getTimeOutEnd() == null) {
                event.getHook().sendMessage("This user is not currently timed out.").queue();
                return;
            }

            targetMember.removeTimeout().reason(reason).queue(
                    success -> {

                        analytics.recordAction(event.getGuild().getId(), ActionType.UNTIMEOUT, event.getUser(), targetUser, reason, 0, 0);

                        EmbedBuilder untimeoutEmbed = new EmbedBuilder()
                                .setTitle("⏰ Timeout Removed")
                                .setDescription(String.format("Timeout has been removed from **%s**", targetUser.getName()))
                                .addField("Reason", reason, false)
                                .addField("Moderator", event.getUser().getName(), true)
                                .setColor(Color.GREEN)
                                .setTimestamp(Instant.now())
                                .setThumbnail(targetUser.getEffectiveAvatarUrl());

                        TextChannel logChannel = loggingService.getLogChannel(event.getGuild(), ConfigService.MODERATION_LOG_CHANNEL_NAME);
                        if (logChannel != null) {
                            logChannel.sendMessageEmbeds(untimeoutEmbed.build()).queue();
                        } else {
                            event.getChannel().sendMessageEmbeds(untimeoutEmbed.build()).queue();
                        }

                        event.getHook().sendMessage("User's timeout has been removed successfully.").queue();
                    },
                    error -> {
                        event.getHook().sendMessage("Error: Could not remove the timeout. " + error.getMessage()).queue();
                    }
            );
        }, error -> {
            event.getHook().sendMessage("Error: Cannot find the user in this server. They may have left or been banned.").queue();
        });
    }

    public void handleBan(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(event.getMember())) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        int deleteDays = event.getOption("delete_days") != null ?
                event.getOption("delete_days").getAsInt() : 1;

        if (deleteDays < 0 || deleteDays > 7) {
            event.getHook().sendMessage("Error: delete_days must be between 0 and 7").queue();
            return;
        }

        dataService.saveCommandLog(event.getUser().getId(), event.getUser().getName(), event.getName());

        String userName = targetUser.getName();
        String userAvatar = targetUser.getEffectiveAvatarUrl();

        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {

            if (!configService.canModerate(event.getMember(), targetMember)) {
                event.getHook().sendMessage("❌ You cannot moderate other staff members.").queue();
                return;
            }

            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("Error: I cannot ban this user due to role hierarchy.").queue();
                return;
            }

            proceedWithBan(event, targetUser, reason, deleteDays, userName, userAvatar);

        }, error -> {
            proceedWithBan(event, targetUser, reason, deleteDays, userName, userAvatar);
        });
    }

    private void proceedWithBan(SlashCommandInteractionEvent event, User targetUser, String reason,
                                int deleteDays, String userName, String userAvatar) {

        event.getGuild().ban(targetUser, deleteDays, TimeUnit.DAYS)
                .reason(reason)
                .queue(
                        success -> {

                            analytics.recordAction(event.getGuild().getId(), ActionType.BAN, event.getUser(), targetUser, reason, deleteDays, 0);

                            EmbedBuilder banEmbed = new EmbedBuilder()
                                    .setTitle("🔨 User Banned")
                                    .setDescription(String.format("<@%s> (%s) has been banned from the server", targetUser.getId(), targetUser.getId()))
                                    .addField("Reason", reason, false)
                                    .addField("Message History Deleted", deleteDays + " days", true)
                                    .addField("Moderator", String.format("<@%s> (ID: %s)", event.getUser().getId(), event.getUser().getId()), true)
                                    .setColor(Color.RED)
                                    .setTimestamp(Instant.now())
                                    .setThumbnail(userAvatar);

                            TextChannel logChannel = loggingService.getLogChannel(event.getGuild(), ConfigService.MODERATION_LOG_CHANNEL_NAME);
                            if (logChannel != null) {
                                logChannel.sendMessageEmbeds(banEmbed.build()).queue();
                            } else {
                                event.getChannel().sendMessageEmbeds(banEmbed.build()).queue();
                            }

                            event.getHook().sendMessage("User has been banned successfully.").queue();
                        },
                        error -> {
                            event.getHook().sendMessage("Error: Could not ban the user. " + error.getMessage()).queue();
                        }
                );
    }

    public void handleKick(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(event.getMember())) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();

        dataService.saveCommandLog(event.getUser().getId(), event.getUser().getName(), event.getName());

        Guild guild = event.getGuild();

        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {

            if (!configService.canModerate(event.getMember(), targetMember)) {
                event.getHook().sendMessage("❌ You cannot moderate other staff members.").queue();
                return;
            }

            if (!guild.getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("Error: I cannot kick this user due to role hierarchy.").queue();
                return;
            }

            String userName = targetUser.getName();
            String userAvatar = targetUser.getEffectiveAvatarUrl();

            targetUser.openPrivateChannel().queue(dmChannel -> {
                EmbedBuilder dmEmbed = new EmbedBuilder()
                        .setTitle("👢 You've Been Kicked")
                        .setDescription(String.format("You have been kicked from **%s**", guild.getName()))
                        .addField("Reason", reason, false)
                        .setColor(Color.ORANGE)
                        .setTimestamp(Instant.now());

                dmChannel.sendMessageEmbeds(dmEmbed.build()).queue(
                        null,
                        error -> System.out.println("Could not DM user " + userName + " about their kick")
                );
                proceedWithKick(event, targetMember, targetUser, reason, userName, userAvatar);
            }, error -> {
                System.out.println("Could not open private channel for user " + userName);
                proceedWithKick(event, targetMember, targetUser, reason, userName, userAvatar);
            });

        }, error -> {
            event.getHook().sendMessage("Error: Cannot find the user in this server. They may have already left.").queue();
        });
    }

    private void proceedWithKick(SlashCommandInteractionEvent event, net.dv8tion.jda.api.entities.Member targetMember,
                                 User targetUser, String reason, String userName, String userAvatar) {
        Guild guild = event.getGuild();

        guild.kick(targetMember).reason(reason).queue(
                success -> {
                    analytics.recordAction(event.getGuild().getId(), ActionType.KICK, event.getUser(), targetUser, reason, 0, 0);

                    EmbedBuilder kickEmbed = new EmbedBuilder()
                            .setTitle("👢 User Kicked")
                            .setDescription(String.format("**%s** has been kicked from the server", userName))
                            .addField("Reason", reason, false)
                            .addField("Moderator", event.getUser().getName(), true)
                            .setColor(Color.ORANGE)
                            .setTimestamp(Instant.now())
                            .setThumbnail(userAvatar);

                    TextChannel logChannel = loggingService.getLogChannel(guild, ConfigService.MODERATION_LOG_CHANNEL_NAME);
                    if (logChannel != null) {
                        logChannel.sendMessageEmbeds(kickEmbed.build()).queue();
                    } else {
                        event.getChannel().sendMessageEmbeds(kickEmbed.build()).queue();
                    }

                    event.getHook().sendMessage("✅ User **" + userName + "** has been kicked successfully.").queue();
                },
                error -> {
                    event.getHook().sendMessage("❌ Error: Could not kick the user. " + error.getMessage()).queue();
                }
        );
    }

    public void handleUnban(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(event.getMember())) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        String userId = event.getOption("user_id").getAsString();
        String reason = event.getOption("reason").getAsString();

        dataService.saveCommandLog(event.getUser().getId(), event.getUser().getName(), event.getName());

        if (!userId.matches("\\d{17,19}")) {
            event.getHook().sendMessage("❌ Invalid user ID format. Please provide a valid Discord user ID.").queue();
            return;
        }

        Guild guild = event.getGuild();

        guild.retrieveBanList().queue(banList -> {
            boolean isBanned = banList.stream().anyMatch(ban -> ban.getUser().getId().equals(userId));

            if (!isBanned) {
                event.getHook().sendMessage("❌ User with ID `" + userId + "` is not banned from this server.").queue();
                return;
            }

            Guild.Ban ban = banList.stream()
                    .filter(b -> b.getUser().getId().equals(userId))
                    .findFirst()
                    .orElse(null);

            if (ban == null) {
                event.getHook().sendMessage("❌ Could not find ban information for user ID `" + userId + "`.").queue();
                return;
            }

            User bannedUser = ban.getUser();
            String userName = bannedUser.getName();
            String userAvatar = bannedUser.getEffectiveAvatarUrl();

            configService.hasAdminPermissions(event.getMember());

            guild.unban(bannedUser).reason(reason).queue(
                    success -> {

                        analytics.recordAction(event.getGuild().getId(), ActionType.UNBAN, event.getUser(), bannedUser, reason, 0, 0);

                        EmbedBuilder unbanEmbed = new EmbedBuilder()
                                .setTitle("🔓 User Unbanned")
                                .setDescription(String.format("<@%s> (%s) has been unbanned from the server", userId, userId))
                                .addField("Reason", reason, false)
                                .addField("Moderator", String.format("<@%s> (ID: %s)", event.getUser().getId(), event.getUser().getId()), true)
                                .setColor(Color.GREEN)
                                .setTimestamp(Instant.now())
                                .setThumbnail(userAvatar);

                        TextChannel logChannel = loggingService.getLogChannel(guild, ConfigService.MODERATION_LOG_CHANNEL_NAME);
                        if (logChannel != null) {
                            logChannel.sendMessageEmbeds(unbanEmbed.build()).queue();
                        } else {
                            event.getChannel().sendMessageEmbeds(unbanEmbed.build()).queue();
                        }

                        event.getHook().sendMessage("✅ User **" + userName + "** has been unbanned successfully.").queue();
                    },
                    error -> {
                        event.getHook().sendMessage("❌ Error: Could not unban the user. " + error.getMessage()).queue();
                    }
            );
        }, error -> {
            event.getHook().sendMessage("❌ Error: Could not retrieve ban list. " + error.getMessage()).queue();
        });
    }

    public void handleReason(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(event.getMember())) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.getHook().sendMessage("This command can only be used in a server.").queue();
            return;
        }

        String userId = event.getOption("user").getAsString();
        
        dataService.saveCommandLog(event.getUser().getId(), event.getUser().getName(), event.getName());

        List<ModAction> history = dataService.getBanUnbanHistory(guild.getId(), userId);

        if (history.isEmpty()) {
            EmbedBuilder noHistoryEmbed = new EmbedBuilder()
                    .setTitle("📋 Moderation History")
                    .setDescription("<@" + userId + "> (`" + userId + "`)\n\nNo ban or unban records found.")
                    .setColor(Color.GRAY)
                    .setTimestamp(Instant.now());

            event.getHook().sendMessageEmbeds(noHistoryEmbed.build()).queue();
            return;
        }

        StringBuilder historyText = new StringBuilder();
        for (ModAction action : history) {
            String actionEmoji = action.actionType().name().equals("BAN") ? "🔨" : "🔓";
            historyText.append(actionEmoji).append(" **").append(action.actionType().name()).append("**\n");
            historyText.append("└─ **Moderator:** ").append(action.moderatorName())
                    .append(" (ID: ").append(action.moderatorId()).append(")\n");
            historyText.append("└─ **Reason:** ").append(action.reason()).append("\n");
            historyText.append("└─ **Date:** ").append(action.getFormattedDate()).append("\n\n");
        }

        EmbedBuilder historyEmbed = new EmbedBuilder()
                .setTitle("📋 Ban/Unban History")
                .setDescription("**User:** <@" + userId + "> (`" + userId + "`)\n\n" + historyText)
                .setColor(new Color(100, 150, 255))
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(historyEmbed.build()).queue();
    }

    public void handleRestrict(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasAdminPermissions(event.getMember())) {
            event.getHook().sendMessage("❌ You don't have permission to use this command.").queue();
            return;
        }

        var channel = event.getOption("channel").getAsChannel();
        String type = event.getOption("type").getAsString();

        dataService.saveChannelRestriction(channel.getId(), type);
        configService.addRestriction(channel.getId(), type);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 Channel Restriction Added")
                .setDescription(String.format("Restriction **%s** has been added to %s.", type, channel.getAsMention()))
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    public void handleUnrestrict(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!configService.hasAdminPermissions(event.getMember())) {
            event.getHook().sendMessage("❌ You don't have permission to use this command.").queue();
            return;
        }

        var channel = event.getOption("channel").getAsChannel();
        String type = event.getOption("type").getAsString();

        dataService.removeChannelRestriction(channel.getId(), type);
        configService.removeRestriction(channel.getId(), type);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔓 Channel Restriction Removed")
                .setDescription(String.format("Restriction **%s** has been removed from %s.", type, channel.getAsMention()))
                .setColor(Color.YELLOW)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}


