package com.bryce.discord.config;

import com.bryce.discord.services.VoiceChannelDatabase;

public class EventsServerConfig {
    private final VoiceChannelDatabase database;

    public EventsServerConfig() {
        this.database = new VoiceChannelDatabase();
    }

    public boolean hasServerCategory(String guildId) {
        return database.hasServerSetup(guildId);
    }

    public String getCategoryId(String guildId) {
        return database.getCategoryId(guildId);
    }
    public String getManagedVcIds(String guildId) {
        return database.getManagedVcIds(guildId);
    }
}
