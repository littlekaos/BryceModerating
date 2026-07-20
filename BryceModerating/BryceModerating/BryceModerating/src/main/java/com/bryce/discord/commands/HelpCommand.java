package com.bryce.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

/**
 * Unified /help menu with category buttons (EndZone-style).
 * Merges former /help and /vchelp content into one ephemeral menu.
 */
public class HelpCommand extends ListenerAdapter {

    private static final Color HELP_COLOR = new Color(0, 120, 215);

    public static void sendHelpMenu(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = createBaseEmbed()
                .setDescription("Welcome to the Bryce Moderating help menu.\nSelect a category below to view commands.");

        event.replyEmbeds(embed.build())
                .addComponents(ActionRow.of(helpButtons()))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("help_")) {
            return;
        }

        EmbedBuilder embed = createBaseEmbed();

        switch (id) {
            case "help_mod" -> addModerationSection(embed);
            case "help_voice" -> addVoiceSection(embed);
            case "help_restrict" -> addRestrictionsSection(embed);
            case "help_admin" -> addAdminSection(embed);
            default -> embed.setDescription("Unknown help category. Use `/help` to open the menu again.");
        }

        addSupportSection(embed);

        event.editMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(helpButtons()))
                .queue();
    }

    private static EmbedBuilder createBaseEmbed() {
        return new EmbedBuilder()
                .setTitle("🛡️ Bryce Moderating — Help")
                .setColor(HELP_COLOR)
                .setTimestamp(Instant.now())
                .setFooter("Bryce Moderating | Use /help anytime");
    }

    private static List<Button> helpButtons() {
        return List.of(
                Button.primary("help_mod", "🔨 Moderation"),
                Button.primary("help_voice", "🎙️ Voice"),
                Button.primary("help_restrict", "🔒 Restrictions"),
                Button.primary("help_admin", "⚙️ Admin")
        );
    }

    public static void addModerationSection(EmbedBuilder embed) {
        embed.setDescription("Moderation commands for staff.");
        embed.addField(
                "🔨 Moderation",
                "**`/warn`** <user> <reason> [evidence]\n" +
                        "**`/mute`** <user> <reason> [duration] · **`/unmute`** <user>\n" +
                        "**`/timeout`** <user> <reason> [duration] · **`/untimeout`** <user>\n" +
                        "**`/ban`** <user> <reason> [delete_days] · **`/unban`** <id>\n" +
                        "**`/kick`** <user> <reason>\n" +
                        "**`/purge`** <amount> [user]\n" +
                        "**`/reason`** <id> — Check ban reasons\n" +
                        "**`/role add|remove`** — Manage roles",
                false
        );
    }

    public static void addVoiceSection(EmbedBuilder embed) {
        embed.setDescription("Voice channel manager commands.");
        embed.addField(
                "🎙️ Voice Channels",
                "**`/adminsetup`** — Voice lobbies: pick style + optional **Create** prefix\n" +
                        "**`/dbinfo`** — Voice DB stats (owner only)\n\n" +
                        "Join a lobby VC (`Create Solos VC` or `Solos VC`, etc.) to spawn a temp channel.\n" +
                        "Empty temp channels are deleted automatically.",
                false
        );
    }

    public static void addRestrictionsSection(EmbedBuilder embed) {
        embed.setDescription("Channel content restriction commands.");
        embed.addField(
                "🔒 Channel Restrictions",
                "**`/restrict`** <channel> <type> — Apply a restriction\n" +
                        "**`/unrestrict`** <channel> <type> — Remove a restriction\n" +
                        "**`/restrict-setup`** — Interactive multi-channel setup\n\n" +
                        "**Types:** Media With Text, Media Only, Screenshot Only,\n" +
                        "Text Only, No Media, No Content, No Message",
                false
        );
    }

    public static void addAdminSection(EmbedBuilder embed) {
        embed.setDescription("Administration and system commands.");
        embed.addField(
                "⚙️ Administration",
                "**`/adminsetup`** — Opt-in: log channels, Moderator/Admin/Muted roles, join-to-create voice\n" +
                        "**`/addmodroles`** / **`/removemodroles`** <role> — Register staff roles that lack Discord mod perms\n" +
                        "**`/addadminroles`** / **`/removeadminroles`** <role> — Register staff roles that lack Administrator\n" +
                        "**`/botstats`** — Owner overview (button for full server list)\n" +
                        "**`/savemoderationsystem`** — Force save (owner only)\n\n" +
                        "_Discord mod permissions or Administrator already grant bot access. Log channels are opt-in; if you delete them, they stay deleted._",
                false
        );
    }

    private static void addSupportSection(EmbedBuilder embed) {
        embed.addField(
                "📞 Support",
                "Contact <@689519709988585648> or <@529480987525251082>",
                false
        );
    }
}
