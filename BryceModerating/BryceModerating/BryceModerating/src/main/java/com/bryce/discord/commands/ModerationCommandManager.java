package com.bryce.discord.commands;

import com.bryce.discord.analytics.ModerationAnalytics;
import com.bryce.discord.services.ConfigService;
import com.bryce.discord.services.DataService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.entities.channel.ChannelType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModerationCommandManager extends ListenerAdapter {
    private final DataService dataService;
    private final ConfigService configService;
    private final ModerationAnalytics analytics;

    private final ModerationCommands moderationCommands;
    private final UtilityCommands utilityCommands;
    private final RoleCommand roleCommand;

    public ModerationCommandManager(DataService dataService, ConfigService configService, ModerationAnalytics analytics) {
        this.dataService = dataService;
        this.configService = configService;
        this.analytics = analytics;

        this.moderationCommands = new ModerationCommands(dataService, configService, analytics);
        this.utilityCommands = new UtilityCommands(dataService, configService);
        this.roleCommand = new RoleCommand(dataService, configService, analytics);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("savemoderationsystem")) {
            String userId = event.getUser().getId();
            if (UtilityCommands.isUserAuthorized(userId)) {
                utilityCommands.handleSaveSystem(event);
            } else {
                event.reply("You don't have permission to use this command.").setEphemeral(true).queue();
            }
            return;
        }
        switch (event.getName()) {
            case "warn":
                moderationCommands.handleWarn(event);
                break;
            case "setmuterole":
                moderationCommands.handleSetMuteRole(event);
                break;
            case "setmodroles":
                utilityCommands.handleSetModRoles(event);
                break;
            case "setadminroles":
                utilityCommands.handleSetAdminRoles(event);
                break;
            case "removemodroles":
                utilityCommands.handleRemoveModRoles(event);
                break;
            case "removeadminroles":
                utilityCommands.handleRemoveAdminRoles(event);
                break;
            case "mute":
                moderationCommands.handleMute(event);
                break;
            case "unmute":
                moderationCommands.handleUnmute(event);
                break;
            case "timeout":
                moderationCommands.handleTimeout(event);
                break;
            case "untimeout":
                moderationCommands.handleUntimeout(event);
                break;
            case "ban":
                moderationCommands.handleBan(event);
                break;
            case "unban":
                moderationCommands.handleUnban(event);
                break;
            case "kick":
                moderationCommands.handleKick(event);
                break;
            case "purge":
                utilityCommands.handlePurge(event);
                break;
            case "restrict":
                moderationCommands.handleRestrict(event);
                break;
            case "unrestrict":
                moderationCommands.handleUnrestrict(event);
                break;
            case "exportdb":
                utilityCommands.handleExportDb(event);
                break;
            case "reason":
                moderationCommands.handleReason(event);
                break;
            case "help":
                utilityCommands.handleHelp(event);
                break;
            case "role":
                roleCommand.execute(event);
                break;
        }
    }

    public List<CommandData> getCommands() {
        return createGlobalCommands();
    }

    private List<CommandData> createGlobalCommands() {
        List<CommandData> globalCommands = new ArrayList<>();

        globalCommands.add(Commands.slash("warn", "Give a warning to a user")
                .addOption(OptionType.USER, "user", "The user to warn", true)
                .addOption(OptionType.STRING, "reason", "Reason for the warning", true)
                .addOption(OptionType.ATTACHMENT, "evidence", "Evidence image", false)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("mute", "Mute a user in all channels")
                .addOption(OptionType.USER, "user", "The user to mute", true)
                .addOption(OptionType.STRING, "reason", "Reason for the mute", true)
                .addOption(OptionType.STRING, "duration", "Duration (default: permanent). Examples: 1h, 7d, 4w, 30m (max: 4w/28d)", false)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("unmute", "Unmute a previously muted user")
                .addOption(OptionType.USER, "user", "The user to unmute", true)
                .addOption(OptionType.STRING, "reason", "Reason for unmuting", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("timeout", "Timeout a user for a specified duration")
                .addOption(OptionType.USER, "user", "The user to timeout", true)
                .addOption(OptionType.STRING, "reason", "Reason for the timeout", true)
                .addOption(OptionType.STRING, "duration", "Duration (default: 60m). Examples: 1h, 7d, 4w, 30m (max: 4w/28d)", false)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("untimeout", "Remove a timeout from a user")
                .addOption(OptionType.USER, "user", "The user to remove timeout from", true)
                .addOption(OptionType.STRING, "reason", "Reason for removing the timeout", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("ban", "Ban a user from the server")
                .addOption(OptionType.USER, "user", "The user to ban", true)
                .addOption(OptionType.STRING, "reason", "Reason for the ban", true)
                .addOption(OptionType.INTEGER, "delete_days", "Number of days of messages to delete (0-7)", false)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("unban", "Unban a user from the server")
                .addOption(OptionType.STRING, "user_id", "The Discord user ID of the banned user", true)
                .addOption(OptionType.STRING, "reason", "Reason for the unban", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("kick", "Kick a user from the server")
                .addOption(OptionType.USER, "user", "The user to kick", true)
                .addOption(OptionType.STRING, "reason", "Reason for the kick", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("purge", "Delete multiple messages from the channel")
                .addOption(OptionType.INTEGER, "amount", "Number of messages to delete (1-1000)", true)
                .addOption(OptionType.USER, "user", "Optional: Delete only messages from this user", false)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("restrict", "Add a restriction to a channel")
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "The channel to restrict", true)
                        .setChannelTypes(ChannelType.TEXT))
                .addOptions(new OptionData(OptionType.STRING, "type", "The type of restriction", true)
                        .addChoice("Media With Text", "MEDIA_WITH_TEXT")
                        .addChoice("Media Only", "MEDIA_ONLY")
                        .addChoice("Screenshot Only", "SCREENSHOT_ONLY")
                        .addChoice("Text Only", "TEXT_ONLY")
                        .addChoice("No Media", "NO_MEDIA")
                        .addChoice("No Content", "NO_CONTENT")
                        .addChoice("No Message", "NO_MESSAGE"))
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("unrestrict", "Remove a restriction from a channel")
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "The channel to unrestrict", true)
                        .setChannelTypes(ChannelType.TEXT))
                .addOptions(new OptionData(OptionType.STRING, "type", "The type of restriction to remove", true)
                        .addChoice("Media With Text", "MEDIA_WITH_TEXT")
                        .addChoice("Media Only", "MEDIA_ONLY")
                        .addChoice("Screenshot Only", "SCREENSHOT_ONLY")
                        .addChoice("Text Only", "TEXT_ONLY")
                        .addChoice("No Media", "NO_MEDIA")
                        .addChoice("No Content", "NO_CONTENT")
                        .addChoice("No Message", "NO_MESSAGE"))
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("restrict-setup", "Interactive setup for channel restrictions")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("setmuterole", "Set the role to use for muting users")
                .addOption(OptionType.ROLE, "role", "The role to use for muting", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("savemoderationsystem", "Force save all moderation data")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("exportdb", "Export modbot.db database (owner only)")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("reason", "Check the ban reason for a user")
                .addOption(OptionType.STRING, "user", "The user ID to check ban reason for", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("setmodroles", "Set which role has moderator permissions")
                .addOption(OptionType.ROLE, "role", "The moderator role", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("setadminroles", "Set which role has admin permissions")
                .addOption(OptionType.ROLE, "role", "The admin role", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("removemodroles", "Remove a moderator role")
                .addOption(OptionType.ROLE, "role", "The moderator role to remove", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("removeadminroles", "Remove an admin role")
                .addOption(OptionType.ROLE, "role", "The admin role to remove", true)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("help", "View help and guidance for the moderation bot")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        globalCommands.add(Commands.slash("role", "Role management")
                .addSubcommands(
                        new SubcommandData("add", "Add a role to a user")
                                .addOption(OptionType.USER, "user", "The user to add the role to", true)
                                .addOption(OptionType.ROLE, "role", "The role to add", true),
                        new SubcommandData("remove", "Remove a role from a user")
                                .addOption(OptionType.USER, "user", "The user to remove the role from", true)
                                .addOption(OptionType.ROLE, "role", "The role to remove", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));

        return globalCommands;
    }
}


