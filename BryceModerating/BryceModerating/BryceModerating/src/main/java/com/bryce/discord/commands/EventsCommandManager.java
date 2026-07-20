package com.bryce.discord.commands;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.List;

public class EventsCommandManager {

    public EventsCommandManager() {
    }

    public List<CommandData> getCommands() {
        List<CommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("dbinfo", "View database information (Admin only)"));

        return commands;
    }
}
