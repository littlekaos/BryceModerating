package com.bryce.discord.utils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

public class PermissionUtil {

    public PermissionUtil() {
    }

    public boolean hasAdminPermissions(Member member) {
        if (member == null) {
            System.out.println("DEBUG: Member is null");
            return false;
        }

        System.out.println("DEBUG: Checking permissions for user: " + member.getUser().getName() + " (ID: " + member.getUser().getId() + ")");

        boolean hasPermissions = member.hasPermission(Permission.ADMINISTRATOR) ||
                member.hasPermission(Permission.MANAGE_CHANNEL) ||
                member.hasPermission(Permission.MANAGE_SERVER) ||
                member.hasPermission(Permission.VOICE_MOVE_OTHERS);

        System.out.println("DEBUG: Has Discord permissions: " + hasPermissions);
        return hasPermissions;
    }
}



