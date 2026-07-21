package com.bryce.discord.services;

import com.bryce.discord.config.EventsServerConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceChannelManager {
    /** Key: guildId:userId → active auto-created channel id */
    private final Map<String, Long> userCreatedVoiceChannels = new ConcurrentHashMap<>();
    /** Key: guildId:userId → last create attempt timestamp */
    private final Map<String, Long> lastCreateAt = new ConcurrentHashMap<>();
    private final Set<Long> autoCreatedChannels = ConcurrentHashMap.newKeySet();
    private final EventsServerConfig EventsServerConfig;
    private final VoiceChannelService voiceService;

    private static final long CREATE_COOLDOWN_MS = 30_000L;

    private static final Pattern CREATE_VC_PATTERN = Pattern.compile(
            "(?:Create )?(\\d+s?|Duo|Trio|Squad|Duos|Trios|Squads|6mans|Solo|Solos)(?: VC)?",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, Integer> NAME_TO_NUMBER = new HashMap<>();

    static {

        NAME_TO_NUMBER.put("solo", 1);
        NAME_TO_NUMBER.put("duo", 2);
        NAME_TO_NUMBER.put("trio", 3);
        NAME_TO_NUMBER.put("squad", 4);
        NAME_TO_NUMBER.put("6mans", 6);

        NAME_TO_NUMBER.put("solos", 1);
        NAME_TO_NUMBER.put("duos", 2);
        NAME_TO_NUMBER.put("trios", 3);
        NAME_TO_NUMBER.put("squads", 4);

        NAME_TO_NUMBER.put("1", 1);
        NAME_TO_NUMBER.put("2", 2);
        NAME_TO_NUMBER.put("3", 3);
        NAME_TO_NUMBER.put("4", 4);
        NAME_TO_NUMBER.put("6", 6);

        NAME_TO_NUMBER.put("1s", 1);
        NAME_TO_NUMBER.put("2s", 2);
        NAME_TO_NUMBER.put("3s", 3);
        NAME_TO_NUMBER.put("4s", 4);
        NAME_TO_NUMBER.put("6s", 6);
    }

    public VoiceChannelManager() {
        this.EventsServerConfig = new EventsServerConfig();
        this.voiceService = new VoiceChannelService();
    }

    public boolean isCreateVcChannel(String channelName) {
        return matchesCreateVcName(channelName);
    }

    public static boolean matchesCreateVcName(String channelName) {
        if (channelName == null) {
            return false;
        }
        boolean matches = CREATE_VC_PATTERN.matcher(channelName).matches();
        System.out.println("DEBUG: Checking if '" + channelName + "' matches pattern: " + matches);
        return matches;
    }

    public void createAutomaticVoiceChannel(Member member, String channelName) {
        System.out.println("DEBUG: createAutomaticVoiceChannel called for: " + channelName);

        Matcher matcher = CREATE_VC_PATTERN.matcher(channelName);
        if (!matcher.matches()) {
            return;
        }

        System.out.println("DEBUG: Pattern matched!");
        String userLimitStr = matcher.group(1);
        System.out.println("DEBUG: Extracted user limit string: '" + userLimitStr + "'");

        int userLimit;

        if (userLimitStr.matches("\\d+s")) {
            userLimitStr = userLimitStr.substring(0, userLimitStr.length() - 1);
        }

        if (userLimitStr.matches("\\d+")) {
            userLimit = Integer.parseInt(userLimitStr);
        } else {
            userLimit = NAME_TO_NUMBER.getOrDefault(userLimitStr.toLowerCase(), 0);
        }

        User user = member.getUser();
        Guild guild = member.getGuild();
        String guildId = guild.getId();
        String ownerKey = ownerKey(guildId, user.getId());

        if (!EventsServerConfig.hasServerCategory(guildId)) {
            return;
        }

        // Reuse existing temp channel instead of spawning another
        Long existingId = userCreatedVoiceChannels.get(ownerKey);
        if (existingId != null) {
            VoiceChannel existing = guild.getVoiceChannelById(existingId);
            if (existing != null && autoCreatedChannels.contains(existingId)) {
                System.out.println("DEBUG: User already has temp VC " + existingId + " — moving instead of creating");
                if (member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                    guild.moveVoiceMember(member, existing).queue();
                }
                return;
            }
            userCreatedVoiceChannels.remove(ownerKey);
            autoCreatedChannels.remove(existingId);
        }

        Long lastCreate = lastCreateAt.get(ownerKey);
        long now = System.currentTimeMillis();
        if (lastCreate != null && now - lastCreate < CREATE_COOLDOWN_MS) {
            System.out.println("DEBUG: Create VC cooldown active for " + ownerKey
                    + " (" + (CREATE_COOLDOWN_MS - (now - lastCreate)) + "ms left)");
            return;
        }
        lastCreateAt.put(ownerKey, now);

        String categoryId = EventsServerConfig.getCategoryId(guildId);
        String newChannelName = formatChannelNameWithUser(guildId, userLimit, userLimitStr, member);

        guild.createVoiceChannel(newChannelName)
                .setParent(guild.getCategoryById(categoryId))
                .setUserlimit(userLimit)
                .queue(voiceChannel -> {
                    userCreatedVoiceChannels.put(ownerKey, voiceChannel.getIdLong());
                    autoCreatedChannels.add(voiceChannel.getIdLong());

                    voiceService.logAutomaticChannelCreation(voiceChannel, member);

                    if (member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                        guild.moveVoiceMember(member, voiceChannel).queue();
                    }
                }, error -> {
                    // Allow retry sooner if Discord rejected the create
                    lastCreateAt.remove(ownerKey);
                    System.err.println("DEBUG: Failed to create temp VC: " + error.getMessage());
                });
    }

    public void deleteEmptyVoiceChannel(VoiceChannel channel) {
        long channelId = channel.getIdLong();

        if (autoCreatedChannels.contains(channelId) && channel.getMembers().isEmpty()) {

            voiceService.logChannelDeletion(channel.getId());

            channel.delete().queue(success -> {
                autoCreatedChannels.remove(channelId);
                userCreatedVoiceChannels.values().removeIf(id -> id == channelId);
            });
        }
    }

    private String formatChannelNameWithUser(String guildId, int userLimit, String userLimitStr, Member member) {

        String displayName = member.getEffectiveName();

        return displayName + "'s VC";
    }

    private static String ownerKey(String guildId, String userId) {
        return guildId + ":" + userId;
    }

    public boolean isAutoCreatedChannel(long channelId) {
        return autoCreatedChannels.contains(channelId);
    }

    public EventsServerConfig getEventsServerConfig() {
        return EventsServerConfig;
    }

    public VoiceChannelService getVoiceService() {
        return voiceService;
    }
}
