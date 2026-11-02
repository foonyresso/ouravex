package com.foonyresso.ouravex;

import android.content.SharedPreferences;

import java.util.HashSet;

/** Бестіарій: що гравець уже зустрів (зберігається на пристрої). */
final class Codex {
    private Codex() { }

    private static final HashSet<String> seen = new HashSet<>();
    private static SharedPreferences prefs;

    static void init(SharedPreferences p) {
        prefs = p;
        seen.clear();
        String s = p.getString("codex", "");
        if (s.length() > 0) for (String k : s.split(",")) seen.add(k);
    }

    static boolean has(String key) { return seen.contains(key); }

    static void mark(String key) {
        if (!seen.add(key) || prefs == null) return;
        StringBuilder sb = new StringBuilder();
        for (String k : seen) { if (sb.length() > 0) sb.append(','); sb.append(k); }
        prefs.edit().putString("codex", sb.toString()).apply();
    }
}
