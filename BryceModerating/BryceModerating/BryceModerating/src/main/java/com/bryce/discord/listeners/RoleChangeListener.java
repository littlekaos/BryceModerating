package com.bryce.discord.listeners;

import com.bryce.discord.services.ConfigService;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class RoleChangeListener extends ListenerAdapter {
    private final ConfigService configService;

    public RoleChangeListener(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        configService.clearPermissionCache(event.getMember().getId());
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        configService.clearPermissionCache(event.getMember().getId());
    }
}


