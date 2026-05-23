package com.bryce.discord.analytics;

import com.bryce.discord.models.ModAction;
import com.bryce.discord.services.DataService;
import net.dv8tion.jda.api.entities.User;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ModerationAnalytics {
    private final List<ModAction> actions = new ArrayList<>();
    private final Map<String, List<ModAction>> actionsByModerator = new ConcurrentHashMap<>();
    private final Map<String, List<ModAction>> actionsByTarget = new ConcurrentHashMap<>();
    private final Map<ActionType, List<ModAction>> actionsByType = new ConcurrentHashMap<>();

    private final DataService dataService;

    public ModerationAnalytics(DataService dataService) {
        this.dataService = dataService;
        System.out.println("ModerationAnalytics initialized (database-backed)");
    }

    public void recordAction(ActionType actionType, User moderator, User target,
                             String reason, int duration, int count) {
        ModAction action = new ModAction(
                actionType,
                moderator.getId(),
                moderator.getName(),
                target != null ? target.getId() : "0",
                target != null ? target.getName() : "None",
                reason,
                System.currentTimeMillis(),
                duration,
                count
        );

        actions.add(action);

        actionsByModerator.computeIfAbsent(moderator.getId(), k -> new ArrayList<>()).add(action);
        if (target != null) {
            actionsByTarget.computeIfAbsent(target.getId(), k -> new ArrayList<>()).add(action);
        }
        actionsByType.computeIfAbsent(actionType, k -> new ArrayList<>()).add(action);

        dataService.saveModerationAnalytics(action);
    }

    public void recordPurge(User moderator, int messageCount, String channelName) {
        ModAction action = new ModAction(
                ActionType.PURGE,
                moderator.getId(),
                moderator.getName(),
                "0",
                "None",
                "Purged " + messageCount + " messages in #" + channelName,
                System.currentTimeMillis(),
                0,
                messageCount
        );

        actions.add(action);

        actionsByModerator.computeIfAbsent(moderator.getId(), k -> new ArrayList<>()).add(action);
        actionsByType.computeIfAbsent(ActionType.PURGE, k -> new ArrayList<>()).add(action);

        dataService.saveModerationAnalytics(action);
    }

    public List<ModAction> getActionsByPeriod(int days) {
        if (days <= 0) return new ArrayList<>(actions);

        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        return actions.stream()
                .filter(action -> action.getTimestamp() >= cutoffTime)
                .collect(Collectors.toList());
    }

    public List<ModAction> getActionsByModerator(String moderatorId, int days) {
        List<ModAction> moderatorActions = actionsByModerator.getOrDefault(moderatorId, new ArrayList<>());

        if (days <= 0) return moderatorActions;

        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        return moderatorActions.stream()
                .filter(action -> action.getTimestamp() >= cutoffTime)
                .collect(Collectors.toList());
    }

    public List<ModAction> getActionsByTarget(String targetId, int days) {
        List<ModAction> targetActions = actionsByTarget.getOrDefault(targetId, new ArrayList<>());

        if (days <= 0) return targetActions;

        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        return targetActions.stream()
                .filter(action -> action.getTimestamp() >= cutoffTime)
                .collect(Collectors.toList());
    }

    public List<ModAction> getActionsByType(ActionType type, int days) {
        List<ModAction> typeActions = actionsByType.getOrDefault(type, new ArrayList<>());

        if (days <= 0) return typeActions;

        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        return typeActions.stream()
                .filter(action -> action.getTimestamp() >= cutoffTime)
                .collect(Collectors.toList());
    }

    public Map<String, Integer> getTopModerators(int days, int limit) {
        List<ModAction> periodActions = getActionsByPeriod(days);

        Map<String, Integer> moderatorCounts = new HashMap<>();
        Map<String, String> moderatorNames = new HashMap<>();

        for (ModAction action : periodActions) {
            moderatorCounts.put(action.getModeratorId(),
                    moderatorCounts.getOrDefault(action.getModeratorId(), 0) + 1);
            moderatorNames.put(action.getModeratorId(), action.getModeratorName());
        }

        return moderatorCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        entry -> moderatorNames.getOrDefault(entry.getKey(), entry.getKey()),
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public Map<String, Integer> getTopModeratedUsers(int days, int limit) {
        List<ModAction> periodActions = getActionsByPeriod(days);

        Map<String, Integer> targetCounts = new HashMap<>();
        Map<String, String> targetNames = new HashMap<>();

        for (ModAction action : periodActions) {
            if (action.getTargetId().equals("0")) continue;

            targetCounts.put(action.getTargetId(),
                    targetCounts.getOrDefault(action.getTargetId(), 0) + 1);
            targetNames.put(action.getTargetId(), action.getTargetName());
        }

        return targetCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        entry -> targetNames.getOrDefault(entry.getKey(), entry.getKey()),
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public Map<ActionType, Integer> getActionCountsByType(int days) {
        List<ModAction> periodActions = getActionsByPeriod(days);

        Map<ActionType, Integer> typeCounts = new HashMap<>();

        for (ModAction action : periodActions) {
            typeCounts.put(action.getActionType(),
                    typeCounts.getOrDefault(action.getActionType(), 0) + 1);
        }

        return typeCounts;
    }

    public Map<LocalDate, Integer> getActionCountsByDay(int days) {
        List<ModAction> periodActions = getActionsByPeriod(days);

        Map<LocalDate, Integer> dayCounts = new TreeMap<>();

        for (ModAction action : periodActions) {
            LocalDate date = action.getDate();
            dayCounts.put(date, dayCounts.getOrDefault(date, 0) + 1);
        }

        return dayCounts;
    }
}


