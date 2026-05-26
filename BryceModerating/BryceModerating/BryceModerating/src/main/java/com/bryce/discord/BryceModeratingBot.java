package com.bryce.discord;

import com.bryce.discord.analytics.ActionType;
import com.bryce.discord.analytics.ModerationAnalytics;
import com.bryce.discord.cache.MessageCache;
import com.bryce.discord.cache.UserCache;
import com.bryce.discord.commands.*;
import com.bryce.discord.listeners.*;
import com.bryce.discord.services.*;
import com.bryce.discord.utils.LoggingUtil;
import com.sun.net.httpserver.HttpServer;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class BryceModeratingBot {

    private static final int AUTOSAVE_MINUTES = 15;
    private final DataService dataService;
    private final ConfigService configService;
    private final ModerationAnalytics analytics;
    private final ModerationMessageListener moderationMessageListener;
    private final ModerationCommandManager moderationCommandManager;
    private final BackupService backupService;

    // Integrated components
    private final UserCache userCache;
    private final MessageCache messageCache;
    private final ServerLogsAdminCommands serverLogsAdminCommands;
    private final VoiceChannelManager voiceChannelManager;
    private final EventsSetupManager eventsSetupManager;

    private static final int SERVER_LOGS_AUTOSAVE_INTERVAL = 15;

    public BryceModeratingBot() {
        backupService = new BackupService();
        backupService.restoreFromBackup();

        DatabaseInitializer.initializeDatabase();

        dataService = new DataService();
        dataService.loadAllData();

        configService = new ConfigService();

        analytics = new ModerationAnalytics(dataService);

        moderationMessageListener = new ModerationMessageListener(dataService, configService, analytics);

        moderationCommandManager = new ModerationCommandManager(dataService, configService, analytics);

        dataService.loadRolesFromDatabase(configService);

        // Initialize integrated components
        userCache = new UserCache();
        messageCache = new MessageCache();
        serverLogsAdminCommands = new ServerLogsAdminCommands(this);
        voiceChannelManager = new VoiceChannelManager();
        eventsSetupManager = new EventsSetupManager();

        setupChannelRestrictions();

        setupAutoSave();

        backupService.startAutoBackup();

        System.out.println("🔄 Bot initialized with backup protection!");
    }

    private void setupChannelRestrictions() {
        configService.loadRestrictions(dataService.loadAllChannelRestrictions());
    }

    public UserCache getUserCache() {
        return this.userCache;
    }

    public MessageCache getMessageCache() {
        return this.messageCache;
    }

    public DataService getDataService() {
        return this.dataService;
    }

    private void setupAutoSave() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (dataService.isDataModified()) {
                    dataService.saveAllData();
                    backupService.createBackup();
                }
            }
        }, AUTOSAVE_MINUTES * 60 * 1000, AUTOSAVE_MINUTES * 60 * 1000);
    }

    private void setupServerLogsAutoSave() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("[INFO] Auto-saving user cache data...");
            }
        }, SERVER_LOGS_AUTOSAVE_INTERVAL * 60 * 1000, SERVER_LOGS_AUTOSAVE_INTERVAL * 60 * 1000);
    }

    public void startServerLogs(JDA jda) {
        System.out.println("[INFO] Server Logs started! Connected to " + jda.getGuilds().size() + " guilds.");

        int delaySeconds = 0;
        for (Guild guild : jda.getGuilds()) {
            final int delay = delaySeconds;
            new Thread(() -> {
                try {
                    System.out.println("[INFO] Waiting " + delay + " seconds before loading members for guild: " + guild.getName());
                    Thread.sleep(delay * 1000L);

                    LoggingUtil.ensureLogChannel(guild);
                    System.out.println("[INFO] Loading members for guild: " + guild.getName());

                    guild.loadMembers().onSuccess(members -> {
                        System.out.println("[SUCCESS] Cached " + members.size() + " members from " + guild.getName());
                        members.forEach(member -> getUserCache().cacheUser(member.getUser()));
                    }).onError(error -> {
                        System.err.println("[ERROR] Failed to load members for guild: " + guild.getName() + " - " + error.getMessage());
                    });
                } catch (Exception e) {
                    System.err.println("[ERROR] Error during delayed guild loading: " + e.getMessage());
                }
            }).start();

            delaySeconds += 5;
        }

        setupServerLogsAutoSave();
    }

    public void startUnmuteChecker(JDA jda) {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (Guild guild : jda.getGuilds()) {
                    java.util.Map<String, Long> expiredMutes = dataService.getExpiredMutes(guild.getId());
                    if (!expiredMutes.isEmpty()) {
                        String muteRoleId = dataService.getMuteRoleId(guild.getId());
                        if (muteRoleId == null) continue;
                        net.dv8tion.jda.api.entities.Role muteRole = guild.getRoleById(muteRoleId);
                        if (muteRole == null) continue;

                        for (String userId : expiredMutes.keySet()) {
                            guild.retrieveMemberById(userId).queue(member -> {
                                if (member.getRoles().contains(muteRole)) {
                                    guild.removeRoleFromMember(member, muteRole).queue(success -> {
                                        dataService.removeMute(guild.getId(), userId);
                                        analytics.recordAction(ActionType.UNMUTE, jda.getSelfUser(), member.getUser(),
                                                "Automatic unmute after persistence check", 0, 0);
                                        System.out.println("[INFO] Automatically unmuted " + member.getUser().getName() + " in " + guild.getName());
                                    });
                                } else {
                                    dataService.removeMute(guild.getId(), userId);
                                }
                            }, error -> {
                                // User might have left
                                dataService.removeMute(guild.getId(), userId);
                            });
                        }
                    }
                }
            }
        }, 60 * 1000, 60 * 1000); // Check every minute
    }

    private static void startHealthServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", exchange -> {
            String response = "Bot is running!";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();
        System.out.println("Health check server started on port 8080");
    }

    public static void main(String[] args) throws Exception {
        startHealthServer();
        loadEnvFile();

        BryceModeratingBot bot = new BryceModeratingBot();

        String token = getEnvValue("BOT_TOKEN");

        if (token == null || token.isEmpty()) {
            System.out.println("ERROR: No bot token found! Set the BOT_TOKEN environment variable or add it to .env file.");
            return;
        }

        String statusText = getEnvValue("BOT_STATUS");
        if (statusText == null || statusText.isEmpty()) {
            statusText = "Moderating your favorite servers!";
        }

        String onlineStatusStr = getEnvValue("BOT_ONLINE_STATUS");
        OnlineStatus onlineStatus = OnlineStatus.IDLE;
        if (onlineStatusStr != null && !onlineStatusStr.isEmpty()) {
            try {
                onlineStatus = OnlineStatus.valueOf(onlineStatusStr);
            } catch (Exception e) {
                System.out.println("Invalid online status, using IDLE");
            }
        }

        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(EnumSet.allOf(GatewayIntent.class))
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .enableCache(EnumSet.allOf(CacheFlag.class))
                .addEventListeners(bot.moderationMessageListener)
                .addEventListeners(bot.moderationCommandManager)
                .addEventListeners(new GuildJoinListener(bot.dataService))
                .addEventListeners(new RoleChangeListener(bot.configService))

                .addEventListeners(new ServerLogsMessageListener(bot))
                .addEventListeners(new ServerLogsMemberListener(bot))
                .addEventListeners(new ServerLogsServerListener(bot))
                .addEventListeners(new ServerLogsChannelListener(bot))
                .addEventListeners(new ServerLogsRoleListener(bot))
                .addEventListeners(new ServerLogsUserListener(bot))
                .addEventListeners(bot.serverLogsAdminCommands)

                .addEventListeners(new EventsCommandListener(bot.voiceChannelManager))
                .addEventListeners(new EventsVoiceListener(bot.voiceChannelManager))
                .addEventListeners(new EventsSetupCommandListener(bot.eventsSetupManager))
                .addEventListeners(new ChannelRestrictionSetupListener(bot.dataService, bot.configService))
                .setStatus(onlineStatus)
                .setActivity(Activity.playing(statusText))
                .build();

        try {
            jda.awaitReady();
            bot.serverLogsAdminCommands.setJDA(jda);
            bot.getUserCache().setJDA(jda);
            bot.startServerLogs(jda);
            bot.startUnmuteChecker(jda);

            // Unified Command Registration
            List<CommandData> allCommands = new ArrayList<>(bot.moderationCommandManager.getCommands());

            EventsCommandManager eventsCommandManager = new EventsCommandManager();
            allCommands.addAll(eventsCommandManager.getCommands());

            allCommands.addAll(bot.serverLogsAdminCommands.getCommands());

            jda.updateCommands().addCommands(allCommands).queue(success -> {
                System.out.println("✅ Successfully registered " + allCommands.size() + " unified global commands");
            }, error -> {
                System.err.println("❌ Failed to register unified commands: " + error.getMessage());
            });

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🔄 Bot shutting down - creating final backup and saving data...");

            try {
                bot.dataService.saveAllData();
                bot.backupService.onShutdown();
                if (jda != null) {
                    jda.shutdown();
                }
                System.out.println("✅ Shutdown complete!");
            } catch (Exception e) {
                System.err.println("❌ Error during shutdown: " + e.getMessage());
                e.printStackTrace();
            }
        }));
    }

    private static Dotenv dotenv;

    private static void loadEnvFile() {
        String[] locations = {
                ".env",
                "../.env",
                "../../.env",
                System.getProperty("user.home") + "/.env",
                System.getProperty("user.dir") + "/.env",
                System.getProperty("user.dir") + "/../.env"
        };

        for (String location : locations) {
            Path envPath = Paths.get(location);
            if (Files.exists(envPath)) {
                System.out.println("📋 Found .env file at: " + envPath.toAbsolutePath());
                try {
                    Path absolutePath = envPath.toAbsolutePath();
                    dotenv = Dotenv.configure()
                            .directory(absolutePath.getParent().toString())
                            .filename(absolutePath.getFileName().toString())
                            .load();
                    System.out.println("✅ Successfully loaded .env file");
                    return;
                } catch (Exception e) {
                    System.out.println("⚠️ Failed to load .env from " + envPath.toAbsolutePath() + ": " + e.getMessage());
                }
            }
        }

        System.out.println("⚠️ No .env file found in standard locations, using system environment variables only");
        dotenv = Dotenv.configure().ignoreIfMissing().load();
    }

    private static String getEnvValue(String key) {
        if (dotenv != null) {
            String value = dotenv.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return System.getenv(key);
    }
}