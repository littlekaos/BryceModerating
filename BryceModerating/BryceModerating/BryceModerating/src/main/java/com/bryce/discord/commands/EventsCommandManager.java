package com.bryce.discord.commands;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EventsCommandManager {

    public EventsCommandManager() {
    }

    public List<CommandData> getCommands() {
        List<CommandData> commands = new ArrayList<>();

        // Help command
        commands.add(Commands.slash("vchelp", "View detailed help information about all commands"));

        // Setup command
        commands.add(Commands.slash("setup", "Set up the Voice Channel Manager for this server"));

        // Existing voice channel commands
        commands.add(Commands.slash("createvoice", "Create a new voice channel")
                .addOption(OptionType.STRING, "name", "The name of the voice channel", true)
                .addOption(OptionType.INTEGER, "limit", "User limit (0 for no limit)", false));

        commands.add(Commands.slash("deletevoice", "Delete a voice channel you created")
                .addOption(OptionType.CHANNEL, "channel", "The voice channel to delete", true));

        commands.add(Commands.slash("vcstats", "View voice channel statistics")
                .addOption(OptionType.STRING, "type", "Type of stats (server/global/user)", true)
                .addOption(OptionType.USER, "user", "User to view stats for (optional)", false));

        commands.add(Commands.slash("mychannels", "View your recently created voice channels"));

        commands.add(Commands.slash("activechannels", "View currently active voice channels"));

        commands.add(Commands.slash("dbinfo", "View database information (Admin only)"));

        return commands;
    }
}



