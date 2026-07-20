package com.bryce.discord.listeners;

import com.bryce.discord.services.StaffSetupService;
import com.bryce.discord.services.StaffSetupService.VoiceLobbyStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class StaffSetupListener extends ListenerAdapter {

    private final StaffSetupService staffSetupService;

    public StaffSetupListener(StaffSetupService staffSetupService) {
        this.staffSetupService = staffSetupService;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(StaffSetupService.BTN_YES_PREFIX) && !id.startsWith(StaffSetupService.BTN_NO_PREFIX)) {
            return;
        }

        boolean accept = id.startsWith(StaffSetupService.BTN_YES_PREFIX);
        String guildId = id.substring(id.indexOf(':') + 1);
        Guild guild = event.getJDA().getGuildById(guildId);

        if (guild == null) {
            event.reply("❌ I can't find that server anymore.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        if (!accept) {
            staffSetupService.declineSetup(guild);
            event.getHook().editOriginal("⏭️ Admin setup skipped for **" + guild.getName()
                            + "**. Run `/adminsetup` anytime to try again.")
                    .setEmbeds()
                    .setComponents()
                    .queue();
            return;
        }

        event.getHook().editOriginalEmbeds(staffSetupService.buildVoiceStyleEmbed(guild).build())
                .setComponents(staffSetupService.voiceStyleSelect(guild.getId()))
                .queue();
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();

        if (id.startsWith(StaffSetupService.SELECT_VOICE_STYLE_PREFIX)) {
            handleVoiceStyleSelect(event, id);
            return;
        }

        if (id.startsWith(StaffSetupService.SELECT_VOICE_PREFIX_PREFIX)) {
            handleVoicePrefixSelect(event, id);
        }
    }

    private void handleVoiceStyleSelect(StringSelectInteractionEvent event, String id) {
        String guildId = id.substring(StaffSetupService.SELECT_VOICE_STYLE_PREFIX.length());
        Guild guild = event.getJDA().getGuildById(guildId);
        if (guild == null) {
            event.reply("❌ I can't find that server anymore.").setEphemeral(true).queue();
            return;
        }

        if (event.getValues().isEmpty()) {
            event.reply("❌ Pick a voice lobby style.").setEphemeral(true).queue();
            return;
        }

        VoiceLobbyStyle style = VoiceLobbyStyle.fromSelectValue(event.getValues().get(0));
        if (style == null) {
            event.reply("❌ Unknown voice lobby style.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        event.getHook().editOriginalEmbeds(staffSetupService.buildVoicePrefixEmbed(guild, style).build())
                .setComponents(staffSetupService.voicePrefixSelect(guild.getId(), style))
                .queue();
    }

    private void handleVoicePrefixSelect(StringSelectInteractionEvent event, String id) {
        // staff_setup_voice_prefix:{guildId}:{styleKey}
        String payload = id.substring(StaffSetupService.SELECT_VOICE_PREFIX_PREFIX.length());
        int colon = payload.indexOf(':');
        if (colon < 0) {
            event.reply("❌ Invalid setup state. Run `/adminsetup` again.").setEphemeral(true).queue();
            return;
        }

        String guildId = payload.substring(0, colon);
        String styleKey = payload.substring(colon + 1);

        Guild guild = event.getJDA().getGuildById(guildId);
        if (guild == null) {
            event.reply("❌ I can't find that server anymore.").setEphemeral(true).queue();
            return;
        }

        if (event.getValues().isEmpty()) {
            event.reply("❌ Choose whether lobby names start with Create.").setEphemeral(true).queue();
            return;
        }

        VoiceLobbyStyle style = VoiceLobbyStyle.fromSelectValue(styleKey);
        if (style == null) {
            event.reply("❌ Unknown voice lobby style. Run `/adminsetup` again.").setEphemeral(true).queue();
            return;
        }

        boolean withCreatePrefix = "create".equals(event.getValues().get(0));
        if (!withCreatePrefix && !"plain".equals(event.getValues().get(0))) {
            event.reply("❌ Unknown prefix option.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        staffSetupService.applySetup(guild, style, withCreatePrefix).whenComplete((summary, error) -> {
            if (error != null) {
                event.getHook().editOriginal("❌ Setup failed: " + error.getMessage())
                        .setEmbeds()
                        .setComponents()
                        .queue();
                return;
            }
            event.getHook().editOriginal("✅ " + summary)
                    .setEmbeds()
                    .setComponents()
                    .queue();
        });
    }
}
