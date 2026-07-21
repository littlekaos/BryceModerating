package com.bryce.discord.listeners;

import com.bryce.discord.services.ConfigService;
import com.bryce.discord.services.DataService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelRestrictionSetupListener extends ListenerAdapter {
    private static final String TYPE_SELECT_PREFIX = "restriction_type_select:";
    private static final String CHANNEL_SELECT_PREFIX = "restriction_channel_select:";

    private final DataService dataService;
    private final ConfigService configService;
    private final Map<String, String> userRestrictionChoice = new ConcurrentHashMap<>();

    public ChannelRestrictionSetupListener(DataService dataService, ConfigService configService) {
        this.dataService = dataService;
        this.configService = configService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("restrict-setup")) return;

        if (event.getGuild() == null || event.getMember() == null
                || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need **Administrator** permissions to use this setup.").setEphemeral(true).queue();
            return;
        }

        String userId = event.getUser().getId();
        String guildId = event.getGuild().getId();

        StringSelectMenu menu = StringSelectMenu.create(TYPE_SELECT_PREFIX + guildId + ":" + userId)
                .setPlaceholder("Select a restriction type")
                .addOption("Media With Text", "MEDIA_WITH_TEXT", "Require media/links, text allowed")
                .addOption("Media Only", "MEDIA_ONLY", "Only media/links allowed")
                .addOption("Screenshot Only", "SCREENSHOT_ONLY", "Only images allowed, no text")
                .addOption("Text Only", "TEXT_ONLY", "Only text allowed, no media/links")
                .addOption("No Media", "NO_MEDIA", "No media/links allowed")
                .addOption("No Content", "NO_CONTENT", "No messages allowed at all")
                .addOption("No Message", "NO_MESSAGE", "No messages allowed")
                .build();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛡️ Channel Restriction Setup")
                .setDescription("Select the type of restriction you want to apply to one or more channels.")
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build())
                .addComponents(ActionRow.of(menu))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith(TYPE_SELECT_PREFIX)) return;

        if (!authorizeAdmin(event.getMember(), event.getComponentId(), event.getUser().getId(), event.getGuild() != null ? event.getGuild().getId() : null)) {
            event.reply("❌ You need **Administrator** permissions to continue.").setEphemeral(true).queue();
            return;
        }

        String type = event.getValues().get(0);
        String sessionKey = event.getGuild().getId() + ":" + event.getUser().getId();
        userRestrictionChoice.put(sessionKey, type);

        EntitySelectMenu channelMenu = EntitySelectMenu.create(
                        CHANNEL_SELECT_PREFIX + event.getGuild().getId() + ":" + event.getUser().getId(),
                        EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("Select channel(s) to apply this restriction to")
                .setChannelTypes(ChannelType.TEXT)
                .setMinValues(1)
                .setMaxValues(25)
                .build();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛡️ Channel Restriction Setup")
                .setDescription("Selected Type: **" + type + "**\n\nNow, select the channels you want to apply this restriction to.")
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now());

        event.editMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(channelMenu))
                .queue();
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!event.getComponentId().startsWith(CHANNEL_SELECT_PREFIX)) return;

        if (!authorizeAdmin(event.getMember(), event.getComponentId(), event.getUser().getId(), event.getGuild() != null ? event.getGuild().getId() : null)) {
            event.reply("❌ You need **Administrator** permissions to continue.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        String sessionKey = event.getGuild().getId() + ":" + event.getUser().getId();
        String type = userRestrictionChoice.remove(sessionKey);
        if (type == null) {
            event.getHook().sendMessage("❌ Session expired. Please start over with `/restrict-setup`.").setEphemeral(true).queue();
            return;
        }

        List<GuildChannel> channels = event.getMentions().getChannels();
        StringBuilder sb = new StringBuilder();

        for (GuildChannel channel : channels) {
            dataService.saveChannelRestriction(channel.getId(), type);
            configService.addRestriction(channel.getId(), type);
            sb.append("• ").append(channel.getAsMention()).append("\n");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Setup Complete!")
                .setDescription(String.format("Restriction **%s** has been applied to the following channels:\n\n%s", type, sb))
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(embed.build())
                .setComponents(List.of())
                .queue();
    }

    private boolean authorizeAdmin(Member member, String componentId, String userId, String guildId) {
        if (member == null || guildId == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            return false;
        }
        // componentId = prefix + guildId + ":" + userId
        String expectedSuffix = guildId + ":" + userId;
        return componentId.endsWith(expectedSuffix);
    }
}
