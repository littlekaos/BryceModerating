package com.bryce.discord.commands;

import com.bryce.discord.BryceModeratingBot;
import com.bryce.discord.cache.UserCache.UserDetails;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.List;

public class ServerLogsAdminCommands extends ListenerAdapter {

    private final BryceModeratingBot bot;
    private JDA jda;

    public ServerLogsAdminCommands(BryceModeratingBot bot) {
        this.bot = bot;
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    public List<CommandData> getCommands() {
        List<CommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("reload-members", "Reload member cache for the current server"));
        commands.add(Commands.slash("cache-stats", "Show statistics about the bot's caches"));
        commands.add(Commands.slash("test-user", "Test user retrieval functionality")
                .addOption(OptionType.STRING, "user_id", "Discord user ID to test retrieval for", true));
        return commands;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (this.jda == null) {
            this.jda = event.getJDA();
        }

        String commandName = event.getName();

        // FIX: Only handle commands that belong to this listener
        if (!commandName.equals("reload-members") &&
                !commandName.equals("cache-stats") &&
                !commandName.equals("test-user")) {
            return; // Not our command, ignore it
        }

        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("❌ **Error:** You don't have permission to use this command!").setEphemeral(true).queue();
            return;
        }

        switch (commandName) {
            case "reload-members":
                handleReloadMembersCommand(event);
                break;
            case "cache-stats":
                handleCacheStatsCommand(event);
                break;
            case "test-user":
                handleTestUserCommand(event);
                break;
        }
    }

    private void handleReloadMembersCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        reloadGuildMembers(event.getGuild());

        event.getHook().editOriginal("✅ **Member cache reload initiated!** This may take some time depending on server size.").queue();
    }

    private void handleCacheStatsCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        String stats = "📊 **Cache Statistics**\n" +
                "Message cache: " + bot.getMessageCache().getMessageCacheSize() + " entries\n" +
                "User reference cache: " + bot.getMessageCache().getUserCacheSize() + " entries\n" +
                "User details cache: " + bot.getUserCache().getCacheSize() + " users";

        event.getHook().sendMessage(stats).queue();
    }

    private void handleTestUserCommand(SlashCommandInteractionEvent event) {
        String userId = event.getOption("user_id").getAsString();

        if (userId.matches("\\d+")) {
            event.deferReply().queue();

            UserDetails details = bot.getUserCache().getUserDetails(userId);
            if (details != null) {
                event.getHook().sendMessage("✅ **Found in cache:** " + details.tag).queue();
            } else {
                event.getHook().sendMessage("❌ **Not found in cache**").queue();
            }

            User user = bot.getUserCache().retrieveUser(userId);
            if (user != null) {
                event.getHook().sendMessage("✅ **Retrieved user:** " + user.getName()).queue();
            } else {
                event.getHook().sendMessage("❌ **Could not retrieve user**").queue();
            }

            String displayInfo = bot.getUserCache().getPlainUserInfo(userId);
            event.getHook().sendMessage("📝 **Would display in logs as:** " + displayInfo).queue();

            String mention = bot.getUserCache().getMention(userId);
            event.getHook().sendMessage("🏷️ **Username would display as:** " + mention).queue();
        } else {
            event.reply("❌ **Invalid user ID format.** Please provide a numeric Discord user ID.").setEphemeral(true).queue();
        }
    }

    public void reloadGuildMembers(net.dv8tion.jda.api.entities.Guild guild) {
        try {
            System.out.println("[INFO] Forcing reload of members for guild: " + guild.getName());

            guild.loadMembers().onSuccess(members -> {
                System.out.println("[SUCCESS] Cached " + members.size() + " members from " + guild.getName());
                members.forEach(member -> bot.getUserCache().cacheUser(member.getUser()));
            }).onError(error -> {
                System.err.println("[ERROR] Failed to load members for guild: " + guild.getName() + " - " + error.getMessage());
            });
        } catch (Exception e) {
            System.err.println("[ERROR] Error reloading guild members: " + e.getMessage());
            e.printStackTrace();
        }
    }
}