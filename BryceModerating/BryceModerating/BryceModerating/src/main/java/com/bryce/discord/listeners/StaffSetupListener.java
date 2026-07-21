package com.bryce.discord.listeners;

import com.bryce.discord.commands.UtilityCommands;
import com.bryce.discord.services.StaffSetupService;
import com.bryce.discord.services.StaffSetupService.VoiceLobbyStyle;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

import java.util.function.Consumer;

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
        String payload = id.substring(id.indexOf(':') + 1);
        String[] parts = payload.split(":", 2);
        if (parts.length < 2) {
            event.reply("❌ Invalid setup button. Run `/adminsetup` again.").setEphemeral(true).queue();
            return;
        }

        String guildId = parts[0];
        String boundUserId = parts[1];
        Guild guild = event.getJDA().getGuildById(guildId);

        if (guild == null) {
            event.reply("❌ I can't find that server anymore.").setEphemeral(true).queue();
            return;
        }

        authorizeSetup(event, guild, boundUserId, member -> {
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
                    .setComponents(staffSetupService.voiceStyleSelect(guild.getId(), boundUserId))
                    .queue();
        });
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
        // staff_setup_voice:{guildId}:{userId}
        String payload = id.substring(StaffSetupService.SELECT_VOICE_STYLE_PREFIX.length());
        String[] parts = payload.split(":", 2);
        if (parts.length < 2) {
            event.reply("❌ Invalid setup state. Run `/adminsetup` again.").setEphemeral(true).queue();
            return;
        }

        String guildId = parts[0];
        String boundUserId = parts[1];
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

        authorizeSetup(event, guild, boundUserId, member -> {
            event.deferEdit().queue();
            event.getHook().editOriginalEmbeds(staffSetupService.buildVoicePrefixEmbed(guild, style).build())
                    .setComponents(staffSetupService.voicePrefixSelect(guild.getId(), boundUserId, style))
                    .queue();
        });
    }

    private void handleVoicePrefixSelect(StringSelectInteractionEvent event, String id) {
        // staff_setup_voice_prefix:{guildId}:{userId}:{styleKey}
        String payload = id.substring(StaffSetupService.SELECT_VOICE_PREFIX_PREFIX.length());
        String[] parts = payload.split(":", 3);
        if (parts.length < 3) {
            event.reply("❌ Invalid setup state. Run `/adminsetup` again.").setEphemeral(true).queue();
            return;
        }

        String guildId = parts[0];
        String boundUserId = parts[1];
        String styleKey = parts[2];

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

        authorizeSetup(event, guild, boundUserId, member -> {
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
        });
    }

    /**
     * Setup components must be used by the bound user (or bot owner) who is a Discord Administrator
     * in the target guild.
     */
    private void authorizeSetup(IReplyCallback event, Guild guild, String boundUserId, Consumer<Member> onAuthorized) {
        User user = event.getUser();
        boolean ownerBypass = UtilityCommands.isUserAuthorized(user.getId());
        if (!ownerBypass && !user.getId().equals(boundUserId)) {
            event.reply("❌ Only the person who started admin setup can use these controls.")
                    .setEphemeral(true).queue();
            return;
        }

        Member cached = guild.getMember(user);
        if (cached != null) {
            if (!ownerBypass && !cached.hasPermission(Permission.ADMINISTRATOR)) {
                event.reply("❌ Administrator only.").setEphemeral(true).queue();
                return;
            }
            onAuthorized.accept(cached);
            return;
        }

        guild.retrieveMember(user).queue(
                member -> {
                    if (!ownerBypass && !member.hasPermission(Permission.ADMINISTRATOR)) {
                        event.reply("❌ Administrator only.").setEphemeral(true).queue();
                        return;
                    }
                    onAuthorized.accept(member);
                },
                error -> event.reply("❌ You must be a member of that server to run setup.")
                        .setEphemeral(true).queue()
        );
    }
}
