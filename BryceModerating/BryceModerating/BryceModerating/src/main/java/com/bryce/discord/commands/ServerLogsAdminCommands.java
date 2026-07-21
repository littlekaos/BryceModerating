package com.bryce.discord.commands;

import com.bryce.discord.BryceModeratingBot;
import com.bryce.discord.services.VoiceChannelDatabase;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ServerLogsAdminCommands extends ListenerAdapter {

    private static final String BTN_PREFIX = "botstats:";
    private static final int SERVERS_PER_PAGE = 20;

    private final BryceModeratingBot bot;
    private final Instant startedAt = Instant.now();
    private final VoiceChannelDatabase voiceChannelDatabase = new VoiceChannelDatabase();
    private JDA jda;

    public ServerLogsAdminCommands(BryceModeratingBot bot) {
        this.bot = bot;
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    public List<CommandData> getCommands() {
        List<CommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("botstats", "Owner-only overview of bot health and reach"));
        return commands;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (this.jda == null) {
            this.jda = event.getJDA();
        }

        if (!event.getName().equals("botstats")) {
            return;
        }

        if (!UtilityCommands.isUserAuthorized(event.getUser().getId())) {
            event.reply("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        Long currentGuildId = event.getGuild() != null ? event.getGuild().getIdLong() : null;
        event.getHook().editOriginalEmbeds(buildOverviewEmbed(event.getJDA(), currentGuildId).build())
                .setComponents(overviewButtons(event.getUser().getId()))
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(BTN_PREFIX)) {
            return;
        }

        // botstats:overview:{userId}
        // botstats:servers:{userId}:{page}
        String[] parts = id.split(":");
        if (parts.length < 3) {
            return;
        }

        String view = parts[1];
        String ownerId = parts[2];

        if (!event.getUser().getId().equals(ownerId)) {
            event.reply("❌ Only the person who ran `/botstats` can use these buttons.").setEphemeral(true).queue();
            return;
        }
        if (!UtilityCommands.isUserAuthorized(event.getUser().getId())) {
            event.reply("❌ You don't have permission to use this.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        Long currentGuildId = event.getGuild() != null ? event.getGuild().getIdLong() : null;
        JDA api = event.getJDA();

        if ("overview".equals(view)) {
            event.getHook().editOriginalEmbeds(buildOverviewEmbed(api, currentGuildId).build())
                    .setComponents(overviewButtons(ownerId))
                    .queue();
            return;
        }

        if ("servers".equals(view)) {
            int page = 0;
            if (parts.length >= 4) {
                try {
                    page = Integer.parseInt(parts[3]);
                } catch (NumberFormatException ignored) {
                    page = 0;
                }
            }
            List<Guild> guilds = sortedGuilds(api);
            int totalPages = Math.max(1, (int) Math.ceil(guilds.size() / (double) SERVERS_PER_PAGE));
            page = Math.clamp(page, 0, totalPages - 1);

            event.getHook().editOriginalEmbeds(buildServersEmbed(guilds, page, currentGuildId).build())
                    .setComponents(serversButtons(ownerId, page, totalPages))
                    .queue();
        }
    }

    private EmbedBuilder buildOverviewEmbed(JDA api, Long currentGuildId) {
        List<Guild> guilds = sortedGuilds(api);

        long totalMembers = 0;
        int voiceConfigured = 0;
        for (Guild guild : guilds) {
            totalMembers += guild.getMemberCount();
            if (voiceChannelDatabase.hasServerSetup(guild.getId())) {
                voiceConfigured++;
            }
        }

        int serverCount = guilds.size();
        int avgMembers = serverCount > 0 ? (int) Math.round(totalMembers / (double) serverCount) : 0;
        int largest = serverCount > 0 ? guilds.get(0).getMemberCount() : 0;
        String largestName = serverCount > 0 ? guilds.get(0).getName() : "—";

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);

        String thisServer = "N/A (DM)";
        if (currentGuildId != null) {
            Guild here = api.getGuildById(currentGuildId);
            if (here != null) {
                thisServer = here.getName() + " — " + String.format("%,d", here.getMemberCount()) + " members";
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Bot Stats")
                .setColor(new Color(0, 120, 215))
                .setDescription("Owner overview — process health and Discord reach.\nUse **All servers** to browse every guild.")
                .addField("Reach",
                        "**Servers:** " + String.format("%,d", serverCount) + "\n" +
                                "**Members:** " + String.format("%,d", totalMembers) + "\n" +
                                "**Average / server:** " + String.format("%,d", avgMembers) + "\n" +
                                "**Largest:** " + largestName + " (" + String.format("%,d", largest) + ")",
                        true)
                .addField("Process",
                        "**Uptime:** " + formatUptime(Duration.between(startedAt, Instant.now())) + "\n" +
                                "**Ping:** " + api.getGatewayPing() + " ms\n" +
                                "**Status:** " + api.getStatus() + "\n" +
                                "**Memory:** " + usedMb + " / " + maxMb + " MB",
                        true)
                .addField("Runtime",
                        "**Java:** " + System.getProperty("java.version") + "\n" +
                                "**JDA:** " + net.dv8tion.jda.api.JDAInfo.VERSION + "\n" +
                                "**JVM uptime:** " + formatUptime(Duration.ofMillis(
                                        ManagementFactory.getRuntimeMXBean().getUptime())),
                        true)
                .addField("Caches",
                        "Messages: **" + bot.getMessageCache().getMessageCacheSize() + "**\n" +
                                "Users: **" + bot.getUserCache().getCacheSize() + "**",
                        true)
                .addField("Voice setups",
                        voiceConfigured + " / " + serverCount + " servers configured",
                        true)
                .addField("This server", thisServer, true)
                .setFooter("Member counts are Discord's reported guild sizes")
                .setTimestamp(Instant.now());

        if (api.getSelfUser().getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(api.getSelfUser().getEffectiveAvatarUrl());
        }
        return embed;
    }

    private EmbedBuilder buildServersEmbed(List<Guild> guilds, int page, Long currentGuildId) {
        int totalPages = Math.max(1, (int) Math.ceil(guilds.size() / (double) SERVERS_PER_PAGE));
        int from = page * SERVERS_PER_PAGE;
        int to = Math.min(from + SERVERS_PER_PAGE, guilds.size());

        StringBuilder list = new StringBuilder();
        if (guilds.isEmpty()) {
            list.append("No servers yet.");
        } else {
            for (int i = from; i < to; i++) {
                Guild guild = guilds.get(i);
                boolean here = currentGuildId != null && currentGuildId == guild.getIdLong();
                list.append("`").append(i + 1).append(".` ")
                        .append(here ? "**›** " : "")
                        .append(guild.getName())
                        .append(" — **")
                        .append(String.format("%,d", guild.getMemberCount()))
                        .append("** (`")
                        .append(guild.getId())
                        .append("`)\n");
            }
        }

        // Discord description max 4096; keep a safety margin
        String description = list.toString();
        if (description.length() > 3900) {
            description = description.substring(0, 3900) + "\n*…truncated*";
        }

        return new EmbedBuilder()
                .setTitle("All servers")
                .setColor(new Color(0, 120, 215))
                .setDescription(description)
                .setFooter("Page " + (page + 1) + " / " + totalPages + " · " + guilds.size() + " servers · sorted by members")
                .setTimestamp(Instant.now());
    }

    private static ActionRow overviewButtons(String userId) {
        return ActionRow.of(
                Button.primary(BTN_PREFIX + "servers:" + userId + ":0", "All servers")
        );
    }

    private static ActionRow serversButtons(String userId, int page, int totalPages) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.secondary(BTN_PREFIX + "overview:" + userId, "Back to stats"));
        buttons.add(Button.primary(BTN_PREFIX + "servers:" + userId + ":" + (page - 1), "◀ Prev")
                .withDisabled(page <= 0));
        buttons.add(Button.primary(BTN_PREFIX + "servers:" + userId + ":" + (page + 1), "Next ▶")
                .withDisabled(page >= totalPages - 1));
        return ActionRow.of(buttons);
    }

    private static List<Guild> sortedGuilds(JDA api) {
        List<Guild> guilds = new ArrayList<>(api.getGuilds());
        guilds.sort(Comparator.comparingInt(Guild::getMemberCount).reversed());
        return guilds;
    }

    private static String formatUptime(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + duration.toSecondsPart() + "s";
    }

    /** Kept for internal use (e.g. guild join / startup). */
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
