package com.bryce.discord.models;

import com.bryce.discord.analytics.ActionType;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record ModAction(String guildId, ActionType actionType, String moderatorId, String moderatorName,
                        String targetId, String targetName, String reason, long timestamp, int duration,
                        int count) implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    public LocalDate getDate() {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    public LocalDateTime getDateTime() {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    public String getFormattedDate() {
        return getDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
