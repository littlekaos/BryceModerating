package com.bryce.discord.models;

import java.io.Serial;
import java.io.Serializable;

public record WarnRecord(String guildId, String userId, String moderatorId, String reason, long timestamp)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;
}
