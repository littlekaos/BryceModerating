package com.bryce.discord.listeners;

import com.bryce.discord.services.ConfigService;
import com.bryce.discord.services.DataService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class RoleChangeListener extends ListenerAdapter {
    private final ConfigService configService;
    private final DataService dataService;

    public RoleChangeListener(ConfigService configService, DataService dataService) {
        this.configService = configService;
        this.dataService = dataService;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        pruneOrphanedBotRoles(event.getJDA());
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        configService.clearPermissionCache(event.getMember().getId());
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        configService.clearPermissionCache(event.getMember().getId());
    }

    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        String roleId = event.getRole().getId();
        boolean removed = false;

        if (configService.getModeratorRoles().contains(roleId)) {
            configService.removeModeratorRole(roleId);
            removed = true;
        }
        if (configService.getAdminRoles().contains(roleId)) {
            configService.removeAdminRole(roleId);
            removed = true;
        }
        if (removed) {
            dataService.saveBotRolesToDatabase(configService.getModeratorRoles(), configService.getAdminRoles());
            System.out.println("Removed deleted role " + roleId + " from bot_roles");
        }

        String muteRoleId = dataService.getMuteRoleId(event.getGuild().getId());
        if (roleId.equals(muteRoleId)) {
            dataService.clearGuildSetting(event.getGuild().getId(), "muteRoleId");
            System.out.println("Cleared muteRoleId for guild " + event.getGuild().getId() + " (role deleted)");
        }
    }

    /** Drop mod/admin role IDs that no longer exist in any guild the bot is in. */
    private void pruneOrphanedBotRoles(JDA jda) {
        Set<String> existingRoleIds = new HashSet<>();
        for (Guild guild : jda.getGuilds()) {
            for (Role role : guild.getRoles()) {
                existingRoleIds.add(role.getId());
            }
        }

        boolean changed = false;
        for (String roleId : new HashSet<>(configService.getModeratorRoles())) {
            if (!existingRoleIds.contains(roleId)) {
                configService.removeModeratorRole(roleId);
                changed = true;
            }
        }
        for (String roleId : new HashSet<>(configService.getAdminRoles())) {
            if (!existingRoleIds.contains(roleId)) {
                configService.removeAdminRole(roleId);
                changed = true;
            }
        }

        if (changed) {
            dataService.saveBotRolesToDatabase(configService.getModeratorRoles(), configService.getAdminRoles());
            System.out.println("Pruned orphaned roles from bot_roles (roles no longer in any server)");
        }
    }
}
