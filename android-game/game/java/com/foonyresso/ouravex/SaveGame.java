package com.foonyresso.ouravex;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Збереження забігу: зберігається стан героя та номер локації; локація починається спочатку. */
final class SaveGame {
    private SaveGame() { }

    static boolean exists(SharedPreferences pr) { return pr.getString("save", null) != null; }

    static void clear(SharedPreferences pr) { pr.edit().remove("save").apply(); }

    static String summary(SharedPreferences pr) {
        try {
            JSONObject j = new JSONObject(pr.getString("save", ""));
            return Data.CLS_NAME[j.getInt("cls")] + " · локація " + j.getInt("stage") + " · рівень " + j.getInt("level");
        } catch (Exception e) { return ""; }
    }

    private static JSONArray arr(int[] a) throws Exception { JSONArray r = new JSONArray(); for (int v : a) r.put(v); return r; }

    static void save(SharedPreferences pr, Game g) {
        try {
            Player p = g.p;
            if (g.over || p.st == Player.ST_DEAD) return;
            JSONObject j = new JSONObject();
            j.put("cls", p.cls); j.put("diff", g.diff); j.put("stage", g.stage);
            j.put("runTime", g.runTime); j.put("kills", g.totalKills); j.put("bosses", g.bossesKilled);
            j.put("level", p.level); j.put("xp", p.xp);
            j.put("str", p.str); j.put("vit", p.vit); j.put("agi", p.agi); j.put("spi", p.spi); j.put("luk", p.luk);
            j.put("dmgCard", p.dmgCard); j.put("regenCard", p.regenCard); j.put("dashCdCard", p.dashCdCard);
            j.put("kiEffCard", p.kiEffCard); j.put("hpPct", p.hpPct); j.put("dashMax", p.dashMax);
            j.put("hp", p.hp); j.put("ki", p.ki);
            j.put("items", arr(p.items)); j.put("skLvl", arr(p.skLvl)); j.put("skBr", arr(p.skBr));
            j.put("slots", arr(p.slotSkill)); j.put("pass", arr(p.passLvl)); j.put("passSlot", arr(p.passSlot));
            j.put("sockN", arr(p.sockN));
            JSONArray un = new JSONArray(); for (boolean b : p.unlocked) un.put(b); j.put("unl", un);
            JSONArray so = new JSONArray();
            for (int i = 0; i < Data.NSK; i++) for (int k = 0; k < 8; k++) {
                Ent.Gem gm = p.sock[i][k];
                if (gm != null) so.put(new JSONArray().put(i).put(k).put(gm.type).put(gm.lvl));
            }
            j.put("sock", so);
            JSONArray bg = new JSONArray();
            for (Ent.Gem gm : p.bag) bg.put(new JSONArray().put(gm.type).put(gm.lvl));
            j.put("bag", bg);
            pr.edit().putString("save", j.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static void rd(JSONArray a, int[] t) throws Exception { for (int i = 0; i < t.length && i < a.length(); i++) t[i] = a.getInt(i); }

    /** Повертає true, якщо збереження застосовано. */
    static boolean load(SharedPreferences pr, Game g) {
        try {
            JSONObject j = new JSONObject(pr.getString("save", ""));
            g.newRun(j.getInt("cls"), j.getInt("diff"));
            Player p = g.p;
            p.level = j.getInt("level"); p.xp = (float) j.getDouble("xp");
            p.str = (float) j.getDouble("str"); p.vit = (float) j.getDouble("vit"); p.agi = (float) j.getDouble("agi");
            p.spi = (float) j.getDouble("spi"); p.luk = (float) j.getDouble("luk");
            p.dmgCard = (float) j.getDouble("dmgCard"); p.regenCard = (float) j.getDouble("regenCard");
            p.dashCdCard = (float) j.getDouble("dashCdCard"); p.kiEffCard = (float) j.getDouble("kiEffCard");
            p.hpPct = (float) j.getDouble("hpPct"); p.dashMax = j.getInt("dashMax");
            rd(j.getJSONArray("items"), p.items); rd(j.getJSONArray("skLvl"), p.skLvl); rd(j.getJSONArray("skBr"), p.skBr);
            rd(j.getJSONArray("slots"), p.slotSkill); rd(j.getJSONArray("pass"), p.passLvl); rd(j.getJSONArray("passSlot"), p.passSlot);
            rd(j.getJSONArray("sockN"), p.sockN);
            JSONArray un = j.getJSONArray("unl");
            for (int i = 0; i < p.unlocked.length && i < un.length(); i++) p.unlocked[i] = un.getBoolean(i);
            JSONArray so = j.getJSONArray("sock");
            for (int i = 0; i < so.length(); i++) {
                JSONArray e = so.getJSONArray(i);
                p.sock[e.getInt(0)][e.getInt(1)] = new Ent.Gem(e.getInt(2), e.getInt(3));
            }
            p.bag.clear();
            JSONArray bg = j.getJSONArray("bag");
            for (int i = 0; i < bg.length(); i++) p.bag.add(new Ent.Gem(bg.getJSONArray(i).getInt(0), bg.getJSONArray(i).getInt(1)));
            g.runTime = (float) j.getDouble("runTime"); g.totalKills = j.getInt("kills"); g.bossesKilled = j.getInt("bosses");
            g.stage = j.getInt("stage") - 1;
            g.startStage();
            p.hp = Math.min(p.maxHp(), Math.max((float) j.getDouble("hp"), p.maxHp() * 0.5f));
            p.ki = Math.min(p.maxKi(), (float) j.getDouble("ki"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
