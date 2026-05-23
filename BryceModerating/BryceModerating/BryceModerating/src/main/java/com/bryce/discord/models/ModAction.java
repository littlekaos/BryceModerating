package com.bryce.discord.models;

import com.bryce.discord.analytics.ActionType;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ModAction implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ActionType actionType;
    private final String moderatorId;
    private final String moderatorName;
    private final String targetId;
    private final String targetName;
    private final String reason;
    private final long timestamp;
    private final int duration;
    private final int count;

    public ModAction(ActionType actionType, String moderatorId, String moderatorName,
                     String targetId, String targetName, String reason,
                     long timestamp, int duration, int count) {
        this.actionType = actionType;
        this.moderatorId = moderatorId;
        this.moderatorName = moderatorName;
        this.targetId = targetId;
        this.targetName = targetName;
        this.reason = reason;
        this.timestamp = timestamp;
        this.duration = duration;
        this.count = count;
    }

    public ActionType getActionType() { return actionType; }
    public String getModeratorId() { return moderatorId; }
    public String getModeratorName() { return moderatorName; }
    public String getTargetId() { return targetId; }
    public String getTargetName() { return targetName; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }
    public int getDuration() { return duration; }
    public int getCount() { return count; }

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


