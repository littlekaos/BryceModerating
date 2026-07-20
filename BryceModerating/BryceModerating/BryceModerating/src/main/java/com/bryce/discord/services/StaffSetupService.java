package com.bryce.discord.services;

import com.bryce.discord.utils.LoggingUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StaffSetupService {

    public static final String STATUS_CONFIGURED = "configured";
    public static final String STATUS_DECLINED = "declined";
    public static final String STATUS_PENDING = "pending";
    public static final String SETUP_STATUS_KEY = "staff_setup_status";

    public static final String BTN_YES_PREFIX = "staff_setup_yes:";
    public static final String BTN_NO_PREFIX = "staff_setup_no:";
    public static final String SELECT_VOICE_STYLE_PREFIX = "staff_setup_voice:";
    public static final String SELECT_VOICE_PREFIX_PREFIX = "staff_setup_voice_prefix:";

    private static final String VOICE_CATEGORY_NAME = "Voice Channels";

    public enum VoiceLobbyStyle {
        NAMED(List.of("Solos", "Duos", "Trios", "Squads"), List.of(1, 2, 3, 4)),
        NUMBERED(List.of("1s", "2s", "3s", "4s"), List.of(1, 2, 3, 4)),
        NUMBERED_WITH_6MANS(List.of("1s", "2s", "3s", "4s", "6mans"), List.of(1, 2, 3, 4, 6));

        private final List<String> baseNames;
        private final List<Integer> limits;

        VoiceLobbyStyle(List<String> baseNames, List<Integer> limits) {
            this.baseNames = baseNames;
            this.limits = limits;
        }

        public List<DefaultLobby> lobbies(boolean withCreatePrefix) {
            List<DefaultLobby> result = new ArrayList<>();
            for (int i = 0; i < baseNames.size(); i++) {
                String name = withCreatePrefix
                        ? "Create " + baseNames.get(i) + " VC"
                        : baseNames.get(i) + " VC";
                result.add(new DefaultLobby(name, limits.get(i)));
            }
            return result;
        }

        public static VoiceLobbyStyle fromSelectValue(String value) {
            return switch (value) {
                case "named" -> NAMED;
                case "numbered" -> NUMBERED;
                case "numbered6" -> NUMBERED_WITH_6MANS;
                default -> null;
            };
        }

        public String selectValue() {
            return switch (this) {
                case NAMED -> "named";
                case NUMBERED -> "numbered";
                case NUMBERED_WITH_6MANS -> "numbered6";
            };
        }

        public String label() {
            return switch (this) {
                case NAMED -> "Solos, Duos, Trios, Squads";
                case NUMBERED -> "1s, 2s, 3s, 4s";
                case NUMBERED_WITH_6MANS -> "1s, 2s, 3s, 4s + 6mans";
            };
        }

        public String exampleNames(boolean withCreatePrefix) {
            return lobbies(withCreatePrefix).stream()
                    .map(lobby -> "`" + lobby.name() + "`")
                    .collect(Collectors.joining(", "));
        }
    }

    private static final Set<String> MOD_ROLE_NAMES = Set.of(
            "moderator", "moderators", "mod", "mods", "staff"
    );
    private static final Set<String> MUTE_ROLE_NAMES = Set.of(
            "muted", "mute", "silenced"
    );

    private final DataService dataService;
    private final ConfigService configService;
    private final VoiceChannelDatabase voiceChannelDatabase;

    public StaffSetupService(DataService dataService, ConfigService configService) {
        this.dataService = dataService;
        this.configService = configService;
        this.voiceChannelDatabase = new VoiceChannelDatabase();
    }

    public ScanResult scan(Guild guild) {
        TextChannel serverLogs = guild.getTextChannelsByName(ConfigService.PURGE_LOG_CHANNEL_NAME, true)
                .stream().findFirst().orElse(null);
        TextChannel modLogs = guild.getTextChannelsByName(ConfigService.MODERATION_LOG_CHANNEL_NAME, true)
                .stream().findFirst().orElse(null);

        List<Role> modRoles = new ArrayList<>();
        List<Role> adminRoles = new ArrayList<>();
        Role muteRole = null;
        java.util.EnumSet<Permission> modPermHints = ConfigService.getModeratorDiscordPermissions();

        String savedMuteId = dataService.getMuteRoleId(guild.getId());
        if (savedMuteId != null) {
            muteRole = guild.getRoleById(savedMuteId);
        }

        for (Role role : guild.getRoles()) {
            if (role.isPublicRole() || role.isManaged()) {
                continue;
            }

            // Admin: only if Administrator is checked (covers renamed Admin/Administrator roles)
            if (role.hasPermission(Permission.ADMINISTRATOR)) {
                adminRoles.add(role);
                continue;
            }

            String name = role.getName().toLowerCase(Locale.ROOT);

            if (muteRole == null && MUTE_ROLE_NAMES.contains(name)) {
                muteRole = role;
                continue;
            }

            boolean nameLooksLikeMod = MOD_ROLE_NAMES.contains(name);
            boolean hasModPermission = modPermHints.stream().anyMatch(role::hasPermission);

            // Mod: name match OR has any moderator permission (covers renamed Mod/Moderator roles)
            if (nameLooksLikeMod || hasModPermission) {
                modRoles.add(role);
            }
        }

        VoiceScan voice = scanVoice(guild);
        return new ScanResult(serverLogs, modLogs, modRoles, adminRoles, muteRole,
                voice.category(), voice.createLobbies(), voice.setupSaved());
    }

    private VoiceScan scanVoice(Guild guild) {
        List<VoiceChannel> createLobbies = new ArrayList<>();
        for (VoiceChannel channel : guild.getVoiceChannels()) {
            if (VoiceChannelManager.matchesCreateVcName(channel.getName())) {
                createLobbies.add(channel);
            }
        }

        Category category = null;
        String savedCategoryId = voiceChannelDatabase.getCategoryId(guild.getId());
        if (savedCategoryId != null) {
            category = guild.getCategoryById(savedCategoryId);
        }
        if (category == null && !createLobbies.isEmpty()) {
            category = createLobbies.get(0).getParentCategory();
        }
        if (category == null) {
            List<Category> named = guild.getCategoriesByName(VOICE_CATEGORY_NAME, true);
            if (!named.isEmpty()) {
                category = named.get(0);
            }
        }

        boolean setupSaved = voiceChannelDatabase.hasServerSetup(guild.getId()) && category != null;
        return new VoiceScan(category, createLobbies, setupSaved);
    }

    public EmbedBuilder buildSetupEmbed(Guild guild, ScanResult scan) {
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        if (scan.serverLogs() != null) {
            found.add("`#" + scan.serverLogs().getName() + "`");
        } else {
            missing.add("`#server-logs`");
        }
        if (scan.modLogs() != null) {
            found.add("`#" + scan.modLogs().getName() + "`");
        } else {
            missing.add("`#moderation-logs`");
        }
        if (!scan.modRoles().isEmpty()) {
            found.add("Moderator: " + roleList(scan.modRoles()));
        } else {
            missing.add("Moderator role");
        }
        if (!scan.adminRoles().isEmpty()) {
            found.add("Admin: " + roleList(scan.adminRoles()));
        } else {
            missing.add("Admin role");
        }
        if (scan.muteRole() != null) {
            found.add("Muted: " + scan.muteRole().getAsMention());
        } else {
            missing.add("Muted role");
        }

        if (scan.voiceCategory() != null) {
            found.add("Voice category: `" + scan.voiceCategory().getName() + "`");
        } else {
            missing.add("Voice category (`" + VOICE_CATEGORY_NAME + "`)");
        }

        if (!scan.createLobbies().isEmpty()) {
            found.add("Create VCs: " + scan.createLobbies().stream()
                    .map(vc -> "`" + vc.getName() + "`")
                    .collect(Collectors.joining(", ")));
        } else {
            missing.add("Create VC lobbies (you'll pick naming style next)");
        }

        return new EmbedBuilder()
                .setTitle("🛡️ Admin setup for " + guild.getName())
                .setColor(new Color(0, 120, 215))
                .setDescription(
                        "I can set up **server logs**, **moderation logs**, **Moderator/Admin/Muted** roles, " +
                                "and **join-to-create voice** (category + Create … VC lobbies).\n\n" +
                                "Click **Yes** to continue — you'll pick lobby names and whether they start with **Create**.\n\n" +
                                "Click **No** to skip — logging and voice stay off until you run `/adminsetup` later.\n\n" +
                                "If you delete a log channel later, I **won't** recreate it."
                )
                .addField("✅ Found", found.isEmpty() ? "_Nothing yet_" : String.join("\n", found), false)
                .addField("❌ Missing", missing.isEmpty() ? "_All set_" : String.join("\n", missing), false)
                .setFooter("Guild ID: " + guild.getId())
                .setTimestamp(Instant.now());
    }

    public ActionRow setupButtons(String guildId) {
        return ActionRow.of(
                Button.success(BTN_YES_PREFIX + guildId, "Yes — set it up"),
                Button.danger(BTN_NO_PREFIX + guildId, "No thanks")
        );
    }

    public EmbedBuilder buildVoiceStyleEmbed(Guild guild) {
        return new EmbedBuilder()
                .setTitle("🎙️ Voice lobby style — " + guild.getName())
                .setColor(new Color(0, 120, 215))
                .setDescription(
                        "Pick how lobby channels should be named.\n\n" +
                                "On the next step you can choose **Create** at the start or not " +
                                "(e.g. `Create Solos VC` vs `Solos VC`)."
                )
                .addField("Solos / Duos / Trios / Squads",
                        VoiceLobbyStyle.NAMED.exampleNames(true), false)
                .addField("1s / 2s / 3s / 4s",
                        VoiceLobbyStyle.NUMBERED.exampleNames(true), false)
                .addField("1s / 2s / 3s / 4s + 6mans",
                        VoiceLobbyStyle.NUMBERED_WITH_6MANS.exampleNames(true), false)
                .setTimestamp(Instant.now());
    }

    public ActionRow voiceStyleSelect(String guildId) {
        return ActionRow.of(
                net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
                        .create(SELECT_VOICE_STYLE_PREFIX + guildId)
                        .setPlaceholder("Choose lobby naming style")
                        .addOption("Solos, Duos, Trios, Squads", "named", "Named lobbies (1–4 players)")
                        .addOption("1s, 2s, 3s, 4s", "numbered", "Numbered lobbies (no 6mans)")
                        .addOption("1s, 2s, 3s, 4s + 6mans", "numbered6", "Numbered lobbies including 6mans")
                        .build()
        );
    }

    public EmbedBuilder buildVoicePrefixEmbed(Guild guild, VoiceLobbyStyle style) {
        return new EmbedBuilder()
                .setTitle("🎙️ Lobby name prefix — " + guild.getName())
                .setColor(new Color(0, 120, 215))
                .setDescription("Style: **" + style.label() + "**\n\nShould lobby channels start with **Create**?")
                .addField("With Create",
                        style.exampleNames(true), false)
                .addField("Without Create",
                        style.exampleNames(false), false)
                .setTimestamp(Instant.now());
    }

    public ActionRow voicePrefixSelect(String guildId, VoiceLobbyStyle style) {
        return ActionRow.of(
                net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
                        .create(SELECT_VOICE_PREFIX_PREFIX + guildId + ":" + style.selectValue())
                        .setPlaceholder("Create at the start of lobby names?")
                        .addOption("Yes — Create … VC", "create", "e.g. Create Solos VC")
                        .addOption("No — … VC only", "plain", "e.g. Solos VC")
                        .build()
        );
    }

    /** Ephemeral setup UI for `/adminsetup`. */
    public void offerSetupEphemeral(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        dataService.setGuildSetting(guild.getId(), SETUP_STATUS_KEY, STATUS_PENDING);
        ScanResult scan = scan(guild);
        event.replyEmbeds(buildSetupEmbed(guild, scan).build())
                .addComponents(setupButtons(guild.getId()))
                .setEphemeral(true)
                .queue();
    }

    /**
     * DM the inviter; if that fails, ping them in a server channel.
     */
    public void offerSetup(Guild guild, User inviter) {
        dataService.setGuildSetting(guild.getId(), SETUP_STATUS_KEY, STATUS_PENDING);
        ScanResult scan = scan(guild);
        EmbedBuilder embed = buildSetupEmbed(guild, scan);
        ActionRow buttons = setupButtons(guild.getId());

        if (inviter == null) {
            postServerPing(guild, null, embed, buttons);
            return;
        }

        inviter.openPrivateChannel().queue(
                channel -> channel.sendMessageEmbeds(embed.build()).setComponents(buttons).queue(
                        success -> System.out.println("[StaffSetup] DM sent to " + inviter.getName() + " for " + guild.getName()),
                        error -> postServerPing(guild, inviter, embed, buttons)
                ),
                error -> postServerPing(guild, inviter, embed, buttons)
        );
    }

    private void postServerPing(Guild guild, User inviter, EmbedBuilder embed, ActionRow buttons) {
        GuildMessageChannel target = findAnnounceChannel(guild);
        if (target == null) {
            System.err.println("[StaffSetup] No channel to announce setup in " + guild.getName());
            return;
        }

        String mention = inviter != null ? inviter.getAsMention() + " " : "";
        target.sendMessage(mention + "I couldn't DM you — use the buttons below to finish admin setup.")
                .addEmbeds(embed.build())
                .setComponents(buttons)
                .queue(
                        success -> System.out.println("[StaffSetup] Ping posted in #" + target.getName()),
                        error -> System.err.println("[StaffSetup] Failed to post ping: " + error.getMessage())
                );
    }

    private GuildMessageChannel findAnnounceChannel(Guild guild) {
        if (guild.getSystemChannel() != null && guild.getSelfMember().hasPermission(guild.getSystemChannel(), Permission.MESSAGE_SEND)) {
            return guild.getSystemChannel();
        }
        for (TextChannel channel : guild.getTextChannels()) {
            if (guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL)) {
                return channel;
            }
        }
        return null;
    }

    public CompletableFuture<String> applySetup(Guild guild, VoiceLobbyStyle voiceStyle, boolean withCreatePrefix) {
        ScanResult scan = scan(guild);
        CompletableFuture<TextChannel> serverLogsFuture = ensureChannel(guild, scan.serverLogs(),
                ConfigService.PURGE_LOG_CHANNEL_NAME, "This channel will be used for server logs.");
        CompletableFuture<TextChannel> modLogsFuture = ensureChannel(guild, scan.modLogs(),
                ConfigService.MODERATION_LOG_CHANNEL_NAME, "This channel logs all moderation actions (warn, mute, timeout, etc).");

        CompletableFuture<StaffRoles> rolesFuture = ensureStaffRoles(guild, scan);
        CompletableFuture<VoiceSetupResult> voiceFuture = ensureVoiceSetup(guild, scan, voiceStyle, withCreatePrefix);

        return CompletableFuture.allOf(serverLogsFuture, modLogsFuture, rolesFuture, voiceFuture)
                .thenApply(v -> {
                    TextChannel serverLogs = serverLogsFuture.join();
                    TextChannel modLogs = modLogsFuture.join();
                    StaffRoles staffRoles = rolesFuture.join();
                    VoiceSetupResult voice = voiceFuture.join();

                    if (serverLogs != null) {
                        dataService.setGuildSetting(guild.getId(), LoggingUtil.SERVER_LOGS_SETTING, serverLogs.getId());
                    }
                    if (modLogs != null) {
                        dataService.setGuildSetting(guild.getId(), LoggingUtil.MODERATION_LOGS_SETTING, modLogs.getId());
                    }

                    for (Role role : staffRoles.modRoles()) {
                        configService.addModeratorRole(role.getId());
                    }
                    for (Role role : staffRoles.adminRoles()) {
                        configService.addAdminRole(role.getId());
                    }
                    dataService.saveBotRolesToDatabase(configService.getModeratorRoles(), configService.getAdminRoles());

                    if (staffRoles.muteRole() != null) {
                        dataService.setMuteRoleId(guild.getId(), staffRoles.muteRole().getId());
                        applyMuteChannelOverrides(guild, staffRoles.muteRole());
                    }

                    dataService.setGuildSetting(guild.getId(), SETUP_STATUS_KEY, STATUS_CONFIGURED);

                    String prefixLabel = withCreatePrefix ? "Create …" : "no Create prefix";
                    String voiceLine;
                    if (voice.category() != null && !voice.createLobbies().isEmpty()) {
                        voiceLine = "• Voice (" + voiceStyle.label() + ", " + prefixLabel + "): `" +
                                voice.category().getName() + "` — " +
                                voice.createLobbies().stream()
                                        .map(vc -> "`" + vc.getName() + "`")
                                        .collect(Collectors.joining(", "));
                    } else {
                        voiceLine = "• Voice: _failed_ (need Manage Channels)";
                    }

                    return "Setup complete for **" + guild.getName() + "**.\n" +
                            "• Server logs: " + (serverLogs != null ? serverLogs.getAsMention() : "_failed_") + "\n" +
                            "• Moderation logs: " + (modLogs != null ? modLogs.getAsMention() : "_failed_") + "\n" +
                            "• Moderator roles: " + roleList(staffRoles.modRoles()) + "\n" +
                            "• Admin roles: " + roleList(staffRoles.adminRoles()) + "\n" +
                            "• Muted role: " + (staffRoles.muteRole() != null ? staffRoles.muteRole().getAsMention() : "_failed_") + "\n" +
                            voiceLine;
                });
    }

    public void declineSetup(Guild guild) {
        dataService.setGuildSetting(guild.getId(), SETUP_STATUS_KEY, STATUS_DECLINED);
    }

    private CompletableFuture<TextChannel> ensureChannel(Guild guild, TextChannel existing, String name, String topic) {
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<TextChannel> future = new CompletableFuture<>();
        guild.createTextChannel(name)
                .setTopic(topic)
                .queue(future::complete, future::completeExceptionally);
        return future.orTimeout(30, TimeUnit.SECONDS).exceptionally(ex -> null);
    }

    /**
     * Ensure Voice Channels category + default Create … VC lobbies, then save server_setup.
     * Blocking RestAction.complete() runs on a worker thread.
     */
    private CompletableFuture<VoiceSetupResult> ensureVoiceSetup(
            Guild guild, ScanResult scan, VoiceLobbyStyle style, boolean withCreatePrefix) {
        return CompletableFuture.supplyAsync(() -> {
            if (!guild.getSelfMember().hasPermission(Permission.MANAGE_CHANNEL)) {
                return new VoiceSetupResult(null, List.of());
            }

            try {
                Category category = scan.voiceCategory();
                if (category == null) {
                    List<Category> named = guild.getCategoriesByName(VOICE_CATEGORY_NAME, true);
                    if (!named.isEmpty()) {
                        category = named.get(0);
                    } else {
                        category = guild.createCategory(VOICE_CATEGORY_NAME).complete();
                    }
                }

                List<VoiceChannel> lobbies = new ArrayList<>();
                for (DefaultLobby lobby : style.lobbies(withCreatePrefix)) {
                    VoiceChannel existing = findLobbyInCategory(category, lobby.name());
                    if (existing == null) {
                        existing = category.createVoiceChannel(lobby.name())
                                .setUserlimit(lobby.userLimit())
                                .complete();
                    } else if (existing.getUserLimit() != lobby.userLimit()) {
                        existing.getManager().setUserLimit(lobby.userLimit()).complete();
                    }
                    lobbies.add(existing);
                }

                String vcIds = lobbies.stream().map(VoiceChannel::getId).collect(Collectors.joining(","));
                voiceChannelDatabase.saveServerSetup(guild.getId(), category.getId(), vcIds);
                System.out.println("[StaffSetup] Voice setup saved for " + guild.getName()
                        + " style=" + style.name() + " createPrefix=" + withCreatePrefix
                        + " category=" + category.getId() + " lobbies=" + vcIds);

                return new VoiceSetupResult(category, lobbies);
            } catch (Exception e) {
                System.err.println("[StaffSetup] Voice setup failed: " + e.getMessage());
                e.printStackTrace();
                return new VoiceSetupResult(null, List.of());
            }
        });
    }

    private static VoiceChannel findLobbyByName(List<VoiceChannel> channels, String name) {
        for (VoiceChannel channel : channels) {
            if (channel.getName().equalsIgnoreCase(name)) {
                return channel;
            }
        }
        return null;
    }

    private static VoiceChannel findLobbyInCategory(Category category, String name) {
        for (VoiceChannel channel : category.getVoiceChannels()) {
            if (channel.getName().equalsIgnoreCase(name)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * Create missing Moderator/Admin/Muted roles, put Admin above Moderator, then apply permissions.
     * Administrator is applied last so Discord still allows reordering.
     * Blocking RestAction.complete() runs on a worker thread — never on JDA callbacks.
     */
    private CompletableFuture<StaffRoles> ensureStaffRoles(Guild guild, ScanResult scan) {
        List<Role> existingMute = scan.muteRole() != null ? List.of(scan.muteRole()) : List.of();
        return ensureRole(guild, scan.modRoles(), "Moderator", ConfigService.getModeratorDiscordPermissions())
                .thenCompose(modRoles -> ensureRole(guild, scan.adminRoles(), "Admin", adminPermissionsWithoutAdministrator())
                        .thenCompose(adminRoles -> ensureRole(guild, existingMute, "Muted", mutedPermissions())
                                .thenCompose(muteRoles -> CompletableFuture.supplyAsync(() -> {
                                    if (!modRoles.isEmpty()) {
                                        syncRolePermissions(guild, modRoles.get(0), ConfigService.getModeratorDiscordPermissions());
                                    }
                                    orderAdminAboveModerator(guild, modRoles, adminRoles);
                                    if (!adminRoles.isEmpty()) {
                                        Role admin = guild.getRoleById(adminRoles.get(0).getId());
                                        if (admin != null) {
                                            syncRolePermissions(guild, admin, adminPermissions());
                                        }
                                    }
                                    Role muteRole = muteRoles.isEmpty() ? null : guild.getRoleById(muteRoles.get(0).getId());
                                    return new StaffRoles(
                                            refreshRoles(guild, modRoles),
                                            refreshRoles(guild, adminRoles),
                                            muteRole
                                    );
                                }))));
    }

    /** Apply channel denies so Muted cannot talk — same as during /adminsetup. */
    private void applyMuteChannelOverrides(Guild guild, Role muteRole) {
        if (muteRole == null || !guild.getSelfMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            return;
        }
        for (TextChannel channel : guild.getTextChannels()) {
            if (!guild.getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_CHANNEL)) {
                continue;
            }
            channel.getPermissionContainer().upsertPermissionOverride(muteRole)
                    .deny(Permission.MESSAGE_SEND,
                            Permission.MESSAGE_SEND_IN_THREADS,
                            Permission.CREATE_PUBLIC_THREADS,
                            Permission.CREATE_PRIVATE_THREADS,
                            Permission.MESSAGE_ADD_REACTION)
                    .queue();
        }
        for (net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel channel : guild.getVoiceChannels()) {
            if (!guild.getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_CHANNEL)) {
                continue;
            }
            channel.getPermissionContainer().upsertPermissionOverride(muteRole)
                    .deny(Permission.VOICE_SPEAK)
                    .queue();
        }
    }

    private static List<Role> refreshRoles(Guild guild, List<Role> roles) {
        List<Role> refreshed = new ArrayList<>();
        for (Role role : roles) {
            Role latest = guild.getRoleById(role.getId());
            if (latest != null) {
                refreshed.add(latest);
            }
        }
        return refreshed;
    }

    // Discord permission bits not always present as enum constants in JDA 5.1
    private static final long PERM_PIN_MESSAGES = 1L << 51;
    private static final long PERM_BYPASS_SLOWMODE = 1L << 52;
    private static final long PERM_CREATE_GUILD_EXPRESSIONS = 1L << 43;

    private CompletableFuture<List<Role>> ensureRole(Guild guild, List<Role> existing, String createName,
                                                     java.util.EnumSet<Permission> permissions) {
        if (!existing.isEmpty()) {
            return CompletableFuture.completedFuture(existing);
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            return CompletableFuture.completedFuture(List.of());
        }

        long raw = permissionsRaw(guild, permissions);

        CompletableFuture<List<Role>> future = new CompletableFuture<>();
        guild.createRole()
                .setName(createName)
                .setMentionable(true)
                .setPermissions(raw)
                .queue(
                        role -> future.complete(List.of(role)),
                        error -> {
                            System.err.println("[StaffSetup] Failed to create role " + createName + ": " + error.getMessage());
                            future.complete(List.of());
                        }
                );
        return future.orTimeout(30, TimeUnit.SECONDS).exceptionally(ex -> List.of());
    }

    private void syncRolePermissions(Guild guild, Role role, java.util.EnumSet<Permission> desired) {
        if (role == null) {
            return;
        }
        Role botTop = guild.getSelfMember().getRoles().stream()
                .max(java.util.Comparator.comparingInt(Role::getPosition))
                .orElse(null);
        if (botTop == null || role.getPosition() >= botTop.getPosition()) {
            System.err.println("[StaffSetup] Cannot edit " + role.getName() + " — move my role above it");
            return;
        }
        try {
            role.getManager().setPermissions(permissionsRaw(guild, desired)).complete();
        } catch (Exception e) {
            System.err.println("[StaffSetup] Failed to update permissions for " + role.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Build Discord permission bitfield, including newer flags (Pin Messages, Bypass Slowmode)
     * that JDA 5.1 may not expose as enum constants.
     */
    private static long permissionsRaw(Guild guild, java.util.EnumSet<Permission> desired) {
        java.util.EnumSet<Permission> granted = filterGrantable(guild, desired);
        long raw = Permission.getRaw(granted);

        boolean canGrantExtras = guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR)
                || guild.getSelfMember().hasPermission(Permission.MESSAGE_MANAGE)
                || guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES);

        if (canGrantExtras) {
            // Always include for admin (all perms) and for mod when MESSAGE_MANAGE is desired
            if (desired.contains(Permission.ADMINISTRATOR) || desired.contains(Permission.MESSAGE_MANAGE)) {
                raw |= PERM_PIN_MESSAGES;
                raw |= PERM_BYPASS_SLOWMODE;
            }
            if (desired.contains(Permission.MANAGE_GUILD_EXPRESSIONS)
                    || desired.contains(Permission.ADMINISTRATOR)) {
                raw |= PERM_CREATE_GUILD_EXPRESSIONS;
            }
        }
        return raw;
    }

    private static java.util.EnumSet<Permission> filterGrantable(Guild guild, java.util.EnumSet<Permission> desired) {
        java.util.EnumSet<Permission> granted = java.util.EnumSet.noneOf(Permission.class);
        for (Permission permission : desired) {
            if (guild.getSelfMember().hasPermission(permission)) {
                granted.add(permission);
            }
        }
        return granted;
    }

    private void orderAdminAboveModerator(Guild guild, List<Role> modRoles, List<Role> adminRoles) {
        if (modRoles.isEmpty() || adminRoles.isEmpty()) {
            return;
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            return;
        }

        Role modRole = guild.getRoleById(modRoles.get(0).getId());
        Role adminRole = guild.getRoleById(adminRoles.get(0).getId());
        if (modRole == null || adminRole == null) {
            return;
        }

        Role botTop = guild.getSelfMember().getRoles().stream()
                .max(java.util.Comparator.comparingInt(Role::getPosition))
                .orElse(null);
        if (botTop == null) {
            return;
        }
        // Must stay below the bot's highest role
        if (modRole.getPosition() >= botTop.getPosition() || adminRole.getPosition() >= botTop.getPosition()) {
            System.err.println("[StaffSetup] Cannot reorder — move my role above Admin/Moderator, then re-run /adminsetup");
            return;
        }

        try {
            // Discord: higher position = higher in the list. Admin must be above Moderator.
            if (adminRole.getPosition() < modRole.getPosition()) {
                guild.modifyRolePositions()
                        .selectPosition(adminRole)
                        .swapPosition(modRole)
                        .complete();
                System.out.println("[StaffSetup] Swapped roles so Admin is above Moderator");
            }

            // Ensure Admin sits just under the bot, Moderator just under Admin
            adminRole = guild.getRoleById(adminRoles.get(0).getId());
            modRole = guild.getRoleById(modRoles.get(0).getId());
            if (adminRole == null || modRole == null) {
                return;
            }

            int underBot = botTop.getPosition() - 1;
            if (adminRole.getPosition() < underBot) {
                guild.modifyRolePositions()
                        .selectPosition(adminRole)
                        .moveTo(underBot)
                        .complete();
            }

            adminRole = guild.getRoleById(adminRoles.get(0).getId());
            modRole = guild.getRoleById(modRoles.get(0).getId());
            if (adminRole == null || modRole == null) {
                return;
            }

            if (modRole.getPosition() >= adminRole.getPosition()) {
                guild.modifyRolePositions()
                        .selectPosition(modRole)
                        .moveTo(Math.max(0, adminRole.getPosition() - 1))
                        .complete();
            }

            adminRole = guild.getRoleById(adminRoles.get(0).getId());
            modRole = guild.getRoleById(modRoles.get(0).getId());
            if (adminRole != null && modRole != null) {
                System.out.println("[StaffSetup] Role order — Admin pos " + adminRole.getPosition()
                        + ", Moderator pos " + modRole.getPosition());
            }
        } catch (Exception e) {
            System.err.println("[StaffSetup] Failed to reorder Admin above Moderator: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** All permissions except Administrator — used while ordering roles. */
    private static java.util.EnumSet<Permission> adminPermissionsWithoutAdministrator() {
        java.util.EnumSet<Permission> perms = adminPermissions();
        perms.remove(Permission.ADMINISTRATOR);
        return perms;
    }

    private static java.util.EnumSet<Permission> adminPermissions() {
        java.util.EnumSet<Permission> perms = java.util.EnumSet.allOf(Permission.class);
        perms.removeIf(p -> p.name().equals("UNKNOWN"));
        return perms;
    }

    private static java.util.EnumSet<Permission> mutedPermissions() {
        // Muted role gets no grants — channel overrides deny speak/send
        return java.util.EnumSet.noneOf(Permission.class);
    }

    private static String roleList(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return "_none_";
        }
        Set<String> names = new LinkedHashSet<>();
        for (Role role : roles) {
            names.add(role.getAsMention());
        }
        return String.join(", ", names);
    }

    public record ScanResult(
            TextChannel serverLogs,
            TextChannel modLogs,
            List<Role> modRoles,
            List<Role> adminRoles,
            Role muteRole,
            Category voiceCategory,
            List<VoiceChannel> createLobbies,
            boolean voiceSetupSaved
    ) {}

    private record StaffRoles(List<Role> modRoles, List<Role> adminRoles, Role muteRole) {}

    private record VoiceScan(Category category, List<VoiceChannel> createLobbies, boolean setupSaved) {}

    private record VoiceSetupResult(Category category, List<VoiceChannel> createLobbies) {}

    private record DefaultLobby(String name, int userLimit) {}
}
