package com.bryce.discord.services;

import com.bryce.discord.config.EventsServerConfig;
import com.bryce.discord.services.VoiceChannelService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceChannelManager {
    private final Map<String, Long> userCreatedVoiceChannels = new ConcurrentHashMap<>();
    private final Set<Long> autoCreatedChannels = new HashSet<>();
    private final EventsServerConfig EventsServerConfig;
    private final VoiceChannelService voiceService;

    private static final Pattern CREATE_VC_PATTERN = Pattern.compile(
            "Create (\\d+s?|Duo|Trio|Squad|Duos|Trios|Squads|6mans|Solo|Solos)( VC)?",
            Pattern.CASE_INSENSITIVE);

    private static final String NAMED_FORMAT_SERVER = "709746803901464587";
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
        boolean matches = CREATE_VC_PATTERN.matcher(channelName).matches();
        System.out.println("DEBUG: Checking if '" + channelName + "' matches pattern: " + matches);
        return matches;
    }

    public void createAutomaticVoiceChannel(Member member, String channelName) {
        System.out.println("DEBUG: createAutomaticVoiceChannel called for: " + channelName);

        Matcher matcher = CREATE_VC_PATTERN.matcher(channelName);
        if (matcher.matches()) {
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

            if (!EventsServerConfig.hasServerCategory(guildId)) {
                return;
            }

            String categoryId = EventsServerConfig.getCategoryId(guildId);

            String newChannelName = formatChannelNameWithUser(guildId, userLimit, userLimitStr, member);

            guild.createVoiceChannel(newChannelName)
                    .setParent(guild.getCategoryById(categoryId))
                    .setUserlimit(userLimit)
                    .queue(voiceChannel -> {
                        userCreatedVoiceChannels.put(user.getId(), voiceChannel.getIdLong());
                        autoCreatedChannels.add(voiceChannel.getIdLong());

                        voiceService.logAutomaticChannelCreation(voiceChannel, member);

                        if (member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                            guild.moveVoiceMember(member, voiceChannel).queue();
                        }
                    });
        }
    }

    public void createCustomVoiceChannel(Guild guild, User user, String name, int limit, Runnable onSuccess, Runnable onError) {
        String guildId = guild.getId();
        if (!EventsServerConfig.hasServerCategory(guildId)) {
            throw new IllegalArgumentException("This bot is not configured for this server.");
        }

        String categoryId = EventsServerConfig.getCategoryId(guildId);

        guild.createVoiceChannel(name)
                .setParent(guild.getCategoryById(categoryId))
                .setUserlimit(limit)
                .queue(voiceChannel -> {
                    userCreatedVoiceChannels.put(user.getId(), voiceChannel.getIdLong());
                    autoCreatedChannels.add(voiceChannel.getIdLong());

                    voiceService.logCustomChannelCreation(voiceChannel, user);

                    onSuccess.run();
                }, error -> onError.run());
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

    public boolean isVoiceChannelCreator(User user, VoiceChannel channel) {
        return userCreatedVoiceChannels.containsKey(user.getId()) &&
                userCreatedVoiceChannels.get(user.getId()) == channel.getIdLong();
    }

    public void deleteUserVoiceChannel(User user, VoiceChannel channel) {
        long channelId = channel.getIdLong();

        voiceService.logChannelDeletion(channel.getId());

        if (isVoiceChannelCreator(user, channel)) {
            userCreatedVoiceChannels.remove(user.getId());
        }
        autoCreatedChannels.remove(channelId);
    }

    private String formatChannelNameWithUser(String guildId, int userLimit, String userLimitStr, Member member) {

        String displayName = member.getEffectiveName();

        return displayName + "'s VC";
    }

    private String formatChannelName(String guildId, int userLimit, String userLimitStr) {
        if (guildId.equals(NAMED_FORMAT_SERVER)) {
            if (userLimit == 1) {
                return "Solo";
            } else if (userLimit == 2) {
                return "Duos";
            } else if (userLimit == 3) {
                return "Trios";
            } else if (userLimit == 4) {
                return "Squads";
            } else if (userLimit == 6) {
                return "6 Mans";
            } else {
                return userLimit + "s";
            }
        } else {
            if (userLimitStr.equalsIgnoreCase("6mans")) {
                return "6 Mans";
            } else if (userLimit > 0) {
                return userLimit + "s";
            } else {
                return "VC";
            }
        }
    }

    public boolean isAutoCreatedChannel(long channelId) {
        return autoCreatedChannels.contains(channelId);
    }

    public EventsServerConfig getEventsServerConfig() {
        return EventsServerConfig;
    }

    public void addUserCreatedChannel(String userId, long channelId) {
        userCreatedVoiceChannels.put(userId, channelId);
        autoCreatedChannels.add(channelId);
    }

    public VoiceChannelService getVoiceService() {
        return voiceService;
    }
}



