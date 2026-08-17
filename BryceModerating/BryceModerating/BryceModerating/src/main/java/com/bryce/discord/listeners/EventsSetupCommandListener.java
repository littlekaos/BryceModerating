package com.bryce.discord.listeners;
import com.bryce.discord.services.EventsSetupManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import java.awt.Color;
import java.util.List;
import java.util.regex.Pattern;

public class EventsSetupCommandListener extends ListenerAdapter {
    private final EventsSetupManager eventsSetupManager;
    private static final Pattern CREATE_VC_PATTERN = Pattern.compile(
            "Create (\\d+s?|Duo|Trio|Squad|Duos|Trios|Squads|6mans|Solo|Solos)( VC)?",
            Pattern.CASE_INSENSITIVE);

    public EventsSetupCommandListener(EventsSetupManager eventsSetupManager) {
        this.eventsSetupManager = eventsSetupManager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("setup")) {
            handleSetupCommand(event);
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (componentId.startsWith("category_select_")) {
            handleCategorySelection(event);
        } else if (componentId.startsWith("vc_select_")) {
            handleVoiceChannelSelection(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (componentId.startsWith("setup_confirm_")) {
            handleSetupConfirmation(event);
        } else if (componentId.startsWith("setup_cancel_")) {
            handleSetupCancellation(event);
        }
    }

    private void handleSetupCommand(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        String guildId = guild.getId();
        String userId = event.getUser().getId();

        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("You must be an administrator to run setup.").setEphemeral(true).queue();
            return;
        }

        eventsSetupManager.startSetupSession(guildId, userId);

        List<Category> categories = eventsSetupManager.getServerCategories(guild);
        if (categories.isEmpty()) {
            event.reply("No categories found in this server. Please create at least one category first.").setEphemeral(true).queue();
            eventsSetupManager.completeSetupSession(guildId);
            return;
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("category_select_" + guildId)
                .setPlaceholder("Select a category to manage VCs in");

        for (Category category : categories) {
            menuBuilder.addOption(category.getName(), category.getId());
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔧 Voice Channel Manager Setup")
                .setDescription("Select the category where you want the bot to manage voice channels.")
                .setColor(Color.BLUE);

        event.replyEmbeds(embed.build())
                .addComponents(ActionRow.of(menuBuilder.build()))
                .setEphemeral(true)
                .queue();
    }

    private void handleCategorySelection(StringSelectInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String categoryId = event.getValues().get(0);

        event.deferReply(true).queue();

        eventsSetupManager.setSelectedCategory(guildId, categoryId);

        Guild guild = event.getGuild();
        List<VoiceChannel> voiceChannels = eventsSetupManager.getVoiceChannelsInCategory(guild, categoryId);

        if (voiceChannels.isEmpty()) {
            event.getHook().sendMessage("No voice channels found in this category. Please add voice channels first.").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("vc_select_" + guildId)
                .setPlaceholder("Select voice channels to manage (hold Shift/Ctrl to select multiple)")
                .setMinValues(1)
                .setMaxValues(Math.min(voiceChannels.size(), 25));

        for (VoiceChannel vc : voiceChannels) {
            menuBuilder.addOption(vc.getName(), vc.getId());
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔧 Voice Channel Manager Setup")
                .setDescription("Select which voice channels you want the bot to manage.")
                .setColor(Color.BLUE);

        event.getHook().sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(menuBuilder.build()))
                .queue();
    }

    private void handleVoiceChannelSelection(StringSelectInteractionEvent event) {
        String guildId = event.getGuild().getId();
        List<String> selectedVcIds = event.getValues();

        event.deferReply(true).queue();

        eventsSetupManager.setManagedVoiceChannels(guildId, selectedVcIds);

        EventsSetupManager.SetupSession session = eventsSetupManager.getSession(guildId);
        Category selectedCategory = event.getGuild().getCategoryById(session.getSelectedCategory());

        StringBuilder vcNames = new StringBuilder();
        for (String vcId : selectedVcIds) {
            VoiceChannel vc = event.getGuild().getVoiceChannelById(vcId);
            if (vc != null) {
                vcNames.append("• ").append(vc.getName()).append("\n");
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔧 Voice Channel Manager Setup")
                .setDescription("Review your setup configuration:")
                .addField("Category", selectedCategory.getName(), false)
                .addField("Voice Channels to Manage", vcNames.toString(), false)
                .setColor(Color.BLUE);

        event.getHook().sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(
                        Button.success("setup_confirm_" + guildId, "✅ Confirm Setup"),
                        Button.danger("setup_cancel_" + guildId, "❌ Cancel")
                ))
                .queue();
    }

    private void handleSetupConfirmation(ButtonInteractionEvent event) {
        String guildId = event.getGuild().getId();

        event.deferReply(true).queue();

        EventsSetupManager.SetupSession session = eventsSetupManager.getSession(guildId);
        List<String> selectedVcIds = session.getManagedVoiceChannels();
        Guild guild = event.getGuild();

        for (String vcId : selectedVcIds) {
            VoiceChannel vc = guild.getVoiceChannelById(vcId);
            if (vc != null && CREATE_VC_PATTERN.matcher(vc.getName()).matches()) {
                vc.getManager().setUserLimit(1).queue(
                        success -> System.out.println("Set limit to 1 for: " + vc.getName()),
                        error -> System.err.println("Failed to set limit for " + vc.getName() + ": " + error.getMessage())
                );
            }
        }

        eventsSetupManager.saveSetup(guildId);
        eventsSetupManager.completeSetupSession(guildId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Setup Complete!")
                .setDescription("The Voice Channel Manager has been configured successfully.")
                .addField("Status", "The bot is now monitoring the selected voice channels.", false)
                .setColor(Color.GREEN);

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleSetupCancellation(ButtonInteractionEvent event) {
        String guildId = event.getGuild().getId();

        event.deferReply(true).queue();

        eventsSetupManager.completeSetupSession(guildId);

        event.getHook().sendMessage("❌ Setup cancelled. No changes were made.").queue();
    }
}



