package com.bryce.discord.utils;

import com.bryce.discord.analytics.ActionType;

import java.awt.Color;

public class FormatUtils {
    public static String formatActionType(ActionType type) {
        return switch (type) {
            case WARN -> "Warning";
            case MUTE -> "Mute";
            case UNMUTE -> "Unmute";
            case TIMEOUT -> "Timeout";
            case UNTIMEOUT -> "Untimeout";
            case BAN -> "Ban";
            case UNBAN -> "Unban";
            case KICK -> "Kick";
            case PURGE -> "Purge";
            case MESSAGE_DELETE -> "Message Delete";
            default -> type.toString();
        };
    }

    public static Color getColorForActionType(ActionType type) {
        return switch (type) {
            case WARN -> Color.YELLOW;
            case MUTE -> new Color(128, 0, 128);
            case UNMUTE, UNTIMEOUT, UNBAN -> Color.GREEN;
            case TIMEOUT -> new Color(255, 165, 0);
            case BAN -> Color.RED;
            case KICK, MESSAGE_DELETE -> Color.ORANGE;
            case PURGE -> Color.BLUE;
            default -> Color.GRAY;
        };
    }

    public static long parseDurationToMinutes(String duration) {
        if (duration == null || duration.isEmpty()) {
            return -1;
        }

        String input = duration.toLowerCase().trim();
        long multiplier = 1;

        if (input.endsWith("w")) {
            multiplier = 10080;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("d")) {
            multiplier = 1440;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("h")) {
            multiplier = 60;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 1;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("s")) {
            return (long) Math.ceil(Long.parseLong(input.substring(0, input.length() - 1)) / 60.0);
        }

        try {
            long value = Long.parseLong(input);
            long totalMinutes = value * multiplier;
            
            if (totalMinutes > 40320) {
                return 40320;
            }
            if (totalMinutes < 1) {
                return -1;
            }
            return totalMinutes;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}


