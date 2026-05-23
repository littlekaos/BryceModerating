package com.bryce.discord.config;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.OnlineStatus;

public class ServerLogsConfig {
    private final Dotenv dotenv;

    public ServerLogsConfig(Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    public String getToken() {
        return dotenv.get("BOT_TOKEN");
    }

    public String getStatusText() {
        String status = dotenv.get("BOT_STATUS");
        return status != null ? status : "Moderating Server Logs!";
    }

    public OnlineStatus getOnlineStatus() {
        String status = dotenv.get("BOT_ONLINE_STATUS");
        if (status != null) {
            try {
                return OnlineStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return OnlineStatus.ONLINE;
            }
        }
        return OnlineStatus.ONLINE;
    }
}
