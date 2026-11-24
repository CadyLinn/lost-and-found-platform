package com.lostfound.backend.match;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * How alike two short texts are, from 0 (nothing in common) to 1 (same words).
 *
 * <p>Chinese has no spaces, so it is split into overlapping two-character pieces ("bigrams"):
 * "咖啡色皮夾" → 咖啡, 啡色, 色皮, 皮夾. English and numbers are split into lowercase words.
 * The two texts become word-count vectors and are compared with cosine similarity.
 */
public final class TextSimilarity {

    /** Pieces that appear in almost every post, so they say nothing about whether two posts match. */
    private static final Set<String> STOP_WORDS = Set.of(
            "撿到", "一個", "遺失", "掉了", "拾獲", "弄丟", "不見", "請問", "有人",
            "the", "a", "an", "my", "lost", "found", "near", "with", "in", "at", "of", "and");

    private TextSimilarity() {
    }

    public static double cosine(String a, String b) {
        Map<String, Integer> va = tokenize(a);
        Map<String, Integer> vb = tokenize(b);
        if (va.isEmpty() || vb.isEmpty()) return 0;

        double dot = 0;
        for (Map.Entry<String, Integer> e : va.entrySet()) {
            dot += e.getValue() * vb.getOrDefault(e.getKey(), 0);
        }
        return dot / (norm(va) * norm(vb));
    }

    /** Text → how many times each token appears. Package-private for tests. */
    static Map<String, Integer> tokenize(String text) {
        Map<String, Integer> counts = new HashMap<>();
        if (text == null) return counts;

        String lower = text.toLowerCase();
        StringBuilder word = new StringBuilder(); // current run of letters/digits
        StringBuilder han = new StringBuilder();  // current run of Chinese characters

        lower.codePoints().forEach(cp -> {
            if (isHan(cp)) {
                flushWord(word, counts);
                han.appendCodePoint(cp);
            } else if (Character.isLetterOrDigit(cp)) {
                flushHan(han, counts);
                word.appendCodePoint(cp);
            } else {
                flushWord(word, counts);
                flushHan(han, counts);
            }
        });
        flushWord(word, counts);
        flushHan(han, counts);
        return counts;
    }

    private static void flushWord(StringBuilder word, Map<String, Integer> counts) {
        if (word.length() >= 2) add(word.toString(), counts);
        word.setLength(0);
    }

    private static void flushHan(StringBuilder han, Map<String, Integer> counts) {
        int[] cps = han.codePoints().toArray();
        if (cps.length == 1) {
            add(new String(cps, 0, 1), counts);
        }
        for (int i = 0; i + 1 < cps.length; i++) {
            add(new String(cps, i, 2), counts);
        }
        han.setLength(0);
    }

    private static void add(String token, Map<String, Integer> counts) {
        if (!STOP_WORDS.contains(token)) counts.merge(token, 1, Integer::sum);
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private static double norm(Map<String, Integer> vector) {
        double sum = 0;
        for (int v : vector.values()) sum += (double) v * v;
        return Math.sqrt(sum);
    }
}
