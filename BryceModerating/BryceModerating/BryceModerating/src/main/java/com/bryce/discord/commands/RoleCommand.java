package com.bryce.discord.commands;

import com.bryce.discord.analytics.ActionType;
import com.bryce.discord.analytics.ModerationAnalytics;
import com.bryce.discord.services.ConfigService;
import com.bryce.discord.services.DataService;
import com.bryce.discord.services.LoggingService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.Color;
import java.time.Instant;

public class RoleCommand {
    private final DataService dataService;
    private final ConfigService configService;
    private final LoggingService loggingService;
    private final ModerationAnalytics analytics;

    public RoleCommand(DataService dataService, ConfigService configService, ModerationAnalytics analytics) {
        this.dataService = dataService;
        this.configService = configService;
        this.loggingService = new LoggingService(dataService);
        this.analytics = analytics;
    }

    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getSubcommandName();
        if (subcommandName == null) {
            event.reply("Invalid subcommand!").setEphemeral(true).queue();
            return;
        }

        switch (subcommandName) {
            case "add":
                handleRoleAdd(event);
                break;
            case "remove":
                handleRoleRemove(event);
                break;
            default:
                event.reply("Unknown subcommand: " + subcommandName).setEphemeral(true).queue();
        }
    }

    private void handleRoleAdd(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        Role targetRole = event.getOption("role").getAsRole();
        Member moderator = event.getMember();
        Guild guild = event.getGuild();

        if (guild == null) return;

        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(moderator)) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        // Prevent self-assignment of roles
        if (targetUser.getId().equals(moderator.getId())) {
            event.getHook().sendMessage("You cannot assign roles to yourself.").queue();
            return;
        }

        if (!canManageRole(moderator, targetRole, guild)) {
            event.getHook().sendMessage("You cannot add this role because it's equal to or higher than your highest role.").queue();
            return;
        }

        guild.retrieveMemberById(targetUser.getId()).queue(
                targetMember -> {
                    if (!configService.canModerate(moderator, targetMember)) {
                        event.getHook().sendMessage("❌ You cannot manage roles for this user because they are higher than or equal to you in hierarchy.").queue();
                        return;
                    }

                    if (targetMember.getRoles().contains(targetRole)) {
                        event.getHook().sendMessage(targetUser.getName() + " already has the role " + targetRole.getName() + ".").queue();
                        return;
                    }

                    guild.addRoleToMember(targetMember, targetRole).queue(
                            success -> {
                                logRoleAction(guild, "added", moderator.getUser(), targetUser, targetRole);
                                analytics.recordAction(ActionType.ROLE_ADD, moderator.getUser(), targetUser, "Added role " + targetRole.getName(), 0, 0);

                                EmbedBuilder embed = new EmbedBuilder()
                                        .setTitle("Role Added")
                                        .setColor(Color.GREEN)
                                        .setDescription("✅ Successfully added role " + targetRole.getName() + " to " + targetUser.getName())
                                        .setTimestamp(Instant.now());

                                event.getHook().sendMessageEmbeds(embed.build()).queue();
                            },
                            error -> {
                                event.getHook().sendMessage("Failed to add role: " + error.getMessage()).queue();
                            }
                    );
                },
                error -> {
                    event.getHook().sendMessage("Could not find that user in this server.").queue();
                }
        );
    }

    private void handleRoleRemove(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        Role targetRole = event.getOption("role").getAsRole();
        Member moderator = event.getMember();
        Guild guild = event.getGuild();

        if (guild == null) return;

        event.deferReply(true).queue();

        if (!configService.hasModeratorPermissions(moderator)) {
            event.getHook().sendMessage("You don't have permission to use this command.").queue();
            return;
        }

        if (!canManageRole(moderator, targetRole, guild)) {
            event.getHook().sendMessage("You cannot remove this role because it's equal to or higher than your highest role.").queue();
            return;
        }

        guild.retrieveMemberById(targetUser.getId()).queue(
                targetMember -> {
                    if (!configService.canModerate(moderator, targetMember)) {
                        event.getHook().sendMessage("❌ You cannot manage roles for this user because they are higher than or equal to you in hierarchy.").queue();
                        return;
                    }

                    if (!targetMember.getRoles().contains(targetRole)) {
                        event.getHook().sendMessage(targetUser.getName() + " doesn't have the role " + targetRole.getName() + ".").queue();
                        return;
                    }

                    guild.removeRoleFromMember(targetMember, targetRole).queue(
                            success -> {
                                logRoleAction(guild, "removed", moderator.getUser(), targetUser, targetRole);
                                analytics.recordAction(ActionType.ROLE_REMOVE, moderator.getUser(), targetUser, "Removed role " + targetRole.getName(), 0, 0);

                                EmbedBuilder embed = new EmbedBuilder()
                                        .setTitle("Role Removed")
                                        .setColor(Color.GREEN)
                                        .setDescription("✅ Successfully removed role " + targetRole.getName() + " from " + targetUser.getName())
                                        .setTimestamp(Instant.now());

                                event.getHook().sendMessageEmbeds(embed.build()).queue();
                            },
                            error -> {
                                event.getHook().sendMessage("Failed to remove role: " + error.getMessage()).queue();
                            }
                    );
                },
                error -> {
                    event.getHook().sendMessage("Could not find that user in this server.").queue();
                }
        );
    }

    private boolean canManageRole(Member moderator, Role targetRole, Guild guild) {
        if (configService.hasAdminPermissions(moderator)) {
            return true;
        }

        Role highestRole = moderator.getRoles().isEmpty() ? null : moderator.getRoles().get(0);

        if (highestRole == null) {
            return targetRole.equals(guild.getPublicRole());
        }

        return highestRole.getPosition() > targetRole.getPosition();
    }

    private void logRoleAction(Guild guild, String action, User moderator, User target, Role role) {
        String title = action.equals("added") ? "🔹 Role Added" : "🔸 Role Removed";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setColor(new Color(135, 206, 235))
                .setDescription("A role has been " + action + ".\n")
                .addField("Target User", target.getName() + "\n( `" + target.getId() + "` )", false)
                .addField("Role", role.getName() + "\n( `" + role.getId() + "` )", false)
                .addField("Moderator", moderator.getName() + " ( `" + moderator.getId() + "` )", false)
                .setThumbnail(target.getAvatarUrl())
                .setTimestamp(Instant.now())
                .setFooter("Role " + action.substring(0, 1).toUpperCase() + action.substring(1));

        loggingService.logModAction(guild, ConfigService.MODERATION_LOG_CHANNEL_NAME, embed.build());
    }
}
