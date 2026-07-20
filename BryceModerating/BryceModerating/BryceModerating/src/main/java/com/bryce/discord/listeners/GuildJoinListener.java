package com.bryce.discord.listeners;

import com.bryce.discord.services.DataService;
import com.bryce.discord.services.StaffSetupService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildJoinListener extends ListenerAdapter {

    private final DataService dataService;
    private final StaffSetupService staffSetupService;

    public GuildJoinListener(DataService dataService, StaffSetupService staffSetupService) {
        this.dataService = dataService;
        this.staffSetupService = staffSetupService;
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        dataService.saveGuildInfo(guild);
        System.out.println("[GuildJoinListener] Saved guild info for: " + guild.getName());

        // Slight delay so the BOT_ADD audit entry is available
        new Thread(() -> {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            resolveInviterAndOfferSetup(guild);
        }, "staff-setup-" + guild.getId()).start();
    }

    private void resolveInviterAndOfferSetup(Guild guild) {
        if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            guild.retrieveAuditLogs()
                    .type(ActionType.BOT_ADD)
                    .limit(5)
                    .queue(
                            entries -> {
                                User inviter = null;
                                String botId = guild.getJDA().getSelfUser().getId();
                                for (AuditLogEntry entry : entries) {
                                    if (entry.getTargetId() != null && entry.getTargetId().equals(botId)) {
                                        inviter = entry.getUser();
                                        break;
                                    }
                                }
                                if (inviter == null) {
                                    inviter = guild.getOwner() != null ? guild.getOwner().getUser() : null;
                                }
                                staffSetupService.offerSetup(guild, inviter);
                            },
                            error -> {
                                System.err.println("[GuildJoinListener] Audit log failed: " + error.getMessage());
                                User owner = guild.getOwner() != null ? guild.getOwner().getUser() : null;
                                staffSetupService.offerSetup(guild, owner);
                            }
                    );
        } else {
            User owner = guild.getOwner() != null ? guild.getOwner().getUser() : null;
            staffSetupService.offerSetup(guild, owner);
        }
    }
}
