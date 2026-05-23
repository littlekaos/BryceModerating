package com.bryce.discord.utils;

import java.util.Arrays;

public class MessageUtils {
    public static boolean containsLinks(String content) {
        return content.matches(".*https?://\\S+.*");
    }
    public static boolean containsOnlyLinks(String content) {
        String[] words = content.trim().split("\\s+");

        return Arrays.stream(words)
                .filter(word -> !word.isEmpty())
                .allMatch(word -> word.matches("https?://\\S+.*"));
    }
}


