package com.bryce.discord.utils;

import com.bryce.discord.analytics.ActionType;

import java.awt.Color;

public class FormatUtils {
    public static String formatActionType(ActionType type) {
        switch (type) {
            case WARN:
                return "Warning";
            case MUTE:
                return "Mute";
            case UNMUTE:
                return "Unmute";
            case TIMEOUT:
                return "Timeout";
            case UNTIMEOUT:
                return "Untimeout";
            case BAN:
                return "Ban";
            case UNBAN:
                return "Unban";
            case KICK:
                return "Kick";
            case PURGE:
                return "Purge";
            case MESSAGE_DELETE:
                return "Message Delete";
            default:
                return type.toString();
        }
    }

    public static Color getColorForActionType(ActionType type) {
        switch (type) {
            case WARN:
                return Color.YELLOW;
            case MUTE:
                return new Color(128, 0, 128);
            case UNMUTE:
                return Color.GREEN;
            case TIMEOUT:
                return new Color(255, 165, 0);
            case UNTIMEOUT:
                return Color.GREEN;
            case BAN:
                return Color.RED;
            case UNBAN:
                return Color.GREEN;
            case KICK:
                return Color.ORANGE;
            case PURGE:
                return Color.BLUE;
            case MESSAGE_DELETE:
                return Color.ORANGE;
            default:
                return Color.GRAY;
        }
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


