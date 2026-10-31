package com.foonyresso.ouravex;

import com.foonyresso.ouravex.Ent.Burst;
import com.foonyresso.ouravex.Ent.Chest;
import com.foonyresso.ouravex.Ent.Drop;
import com.foonyresso.ouravex.Ent.Enemy;
import com.foonyresso.ouravex.Ent.Platform;
import com.foonyresso.ouravex.Ent.Proj;
import com.foonyresso.ouravex.Ent.Tele;

import java.util.ArrayList;
import java.util.Random;

/** Simulation: stage flow, spawning, combat resolution, drops, cards. */
final class Game {
    static final float WORLD_W = 17000f;
    static final float WORLD_H = 5200f;
    static final float ZOOM = 0.40f;
    static final float GRAV = 1500f;

    static final class Ctl {
        boolean left, right, chargeHeld, crouchHeld, atkHeld;
        boolean jumpReq, slideReq, dashReq, atkReq, interactReq;
        int jumpDir, slideDir, dashDir;
        final boolean[] skillReq = new boolean[4];

        void clearEvents() {
            jumpReq = slideReq = dashReq = atkReq = interactReq = false;
            for (int i = 0; i < 4; i++) skillReq[i] = false;
        }
    }

    final Fx fx = new Fx();
    final Audio audio;
    final Player p;
    final Ctl ctl = new Ctl();
    final ArrayList<Enemy> enemies = new ArrayList<>();
    final ArrayList<Proj> projs = new ArrayList<>();
    final ArrayList<Tele> teles = new ArrayList<>();
    final ArrayList<Burst> bursts = new ArrayList<>();
    final ArrayList<Drop> drops = new ArrayList<>();
    final ArrayList<Platform> plats = new ArrayList<>();
    final ArrayList<Chest> chests = new ArrayList<>();
    final Random rnd = new Random();

    int diff, stage, kills, killGoal, totalKills, bossesKilled;
    float runTime, stageTime, time;
    int portalState;
    float portalX, despawnT, spawnT;
    Enemy boss;
    int lastBoss = -1;
    float camX, camY = -700f, viewW = 2000f, viewH = 1000f;
    float hitstop, slowT, slowScale = 1f, timeScale = 1f, zoomKick;
    boolean levelUpPending, dead, over;
    float deadT;
    int pendingLevels;
    final int[] cards = new int[3];
    String banner = "";
    float bannerT;
    int bannerCol = 0xFFFFFFFF;
    float hpScale = 1f, dmgScale = 1f, atkSpd = 1f;
    int palIdx;
    float bossIntro;

    Game(Audio audio) {
        this.audio = audio;
        this.p = new Player(this);
    }

    // ---------------- run / stage ----------------

    void newRun(int cls, int diff) {
        this.diff = diff;
        stage = 0; runTime = 0; time = 0; totalKills = 0; bossesKilled = 0;
        dead = false; over = false; deadT = 0; levelUpPending = false; pendingLevels = 0;
        lastBoss = -1;
        fx.clear();
        p.init(cls);
        startStage();
    }

    void startStage() {
        stage++;
        stageTime = 0; kills = 0;
        killGoal = 15 + stage * 4;
        enemies.clear(); projs.clear(); teles.clear(); bursts.clear(); drops.clear();
        plats.clear(); chests.clear();
        boss = null; portalState = 0; despawnT = 0; bossIntro = 0;
        portalX = 3000f + rnd.nextFloat() * (WORLD_W - 4500f);
        p.x = 400f; p.y = 0f; p.vx = 0f; p.vy = 0f; p.st = Player.ST_FREE; p.stT = 0f;
        if (stage > 1) p.heal(p.maxHp() * 0.35f);
        palIdx = (stage - 1) % Data.PAL.length;
        int rows = (int) ((WORLD_H - 350f) / 118f);
        for (int r = 0; r < rows; r++) {
            float y = -112f - r * 118f - rnd.nextFloat() * 14f;
            float off = (r % 2) * 210f;
            for (float x = 150f + off; x < WORLD_W - 300f; x += 440f) {
                if (rnd.nextFloat() < 0.22f) continue;
                Platform pl = new Platform();
                pl.w = 170f + rnd.nextFloat() * 110f;
                pl.x = x + (rnd.nextFloat() - 0.5f) * 140f;
                pl.y = y;
                plats.add(pl);
            }
        }
        int nc = 7 + rnd.nextInt(4);
        for (int i = 0; i < nc; i++) {
            Chest c = new Chest();
            Platform pl = plats.get(rnd.nextInt(plats.size()));
            c.x = pl.x + pl.w * 0.5f;
            c.y = pl.y;
            chests.add(c);
        }
        spawnT = 1.2f;
        camX = 0f; camY = -0.75f * viewH;
        flashBanner("ЛОКАЦІЯ " + stage, Data.PAL[palIdx][2]);
        audio.setMusic(Audio.MUS_STAGE);
    }

    /** Поверхня під ногами: найвища платформа не вище за y, інакше земля 0. */
    float floorBelow(float x, float y) {
        float best = 0f;
        for (int i = 0; i < plats.size(); i++) {
            Platform pl = plats.get(i);
            if (pl.y >= y - 4f && pl.y < best && x > pl.x - 6f && x < pl.x + pl.w + 6f) best = pl.y;
        }
        return best;
    }

    float dc() { return 1f + runTime / 120f + (stage - 1) * 0.35f; }

    void flashBanner(String s, int col) {
        banner = s; bannerT = 2.4f; bannerCol = col;
    }

    // ---------------- helpers used by Player / Skills / Ai ----------------

    void stop(float s) { hitstop = Math.max(hitstop, s); }
    void shake(float a) { fx.shake(a); }
    void slowmo(float scale, float dur) { slowScale = scale; slowT = dur; }

    void burst(int shape, float x, float y, float ang, float len, float w, float life, int color) {
        Burst b = new Burst();
        b.shape = shape; b.x = x; b.y = y; b.ang = ang; b.len = len; b.w = w;
        b.life = life; b.maxLife = life; b.color = color;
        bursts.add(b);
    }

    Tele addTele(int shape, int kind, float x, float y, float ang, float len, float w, float t, float dmg, Enemy owner) {
        Tele tl = new Tele();
        tl.shape = shape; tl.kind = kind; tl.x = x; tl.y = y; tl.ang = ang; tl.len = len; tl.w = w;
        tl.t = t; tl.tmax = t; tl.dmg = dmg; tl.owner = owner;
        teles.add(tl);
        audio.play(Audio.WARN, 0.35f);
        return tl;
    }

    Proj newProj(int kind, float x, float y, float vx, float vy, float r, float dmg, float life, float aoe,
                 int color, boolean friendly) {
        Proj b = new Proj();
        b.kind = kind; b.x = x; b.y = y; b.vx = vx; b.vy = vy; b.r = r; b.dmg = dmg; b.life = life;
        b.aoe = aoe; b.color = color; b.friendly = friendly; b.homing = kind == 2;
        projs.add(b);
        return b;
    }

    /** nearest living enemy. dirPref: 0 any, +-1 only that side. */
    Enemy nearest(float x, float y, float maxDist, int dirPref) {
        Enemy best = null;
        float bd = maxDist * maxDist;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (e.hp <= 0f || e.spawnT > 0f) continue;
            if (dirPref != 0 && (e.x - x) * dirPref < -10f) continue;
            float dx = e.x - x, dy = (e.y - e.h * 0.5f) - (y - 40f);
            float d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    static int enemyColor(Enemy e) {
        switch (e.type) {
            case Ent.GRUNT: return 0xFFFF4D6D;
            case Ent.FLYER: return 0xFFB06BFF;
            case Ent.BRUTE: return 0xFFFFB020;
            case Ent.BOSS1: return 0xFFFF3A3A;
            default: return 0xFF9A5CFF;
        }
    }

    Enemy spawnEnemy(int type, float x, float y) {
        Enemy e = new Enemy();
        e.type = type; e.x = x; e.y = y;
        e.face = p.x >= x ? 1 : -1;
        switch (type) {
            case Ent.GRUNT:
                e.w = 14f; e.h = 46f; e.hp = 38f; e.dmg = 9f; e.speed = 100f + rnd.nextFloat() * 25f;
                break;
            case Ent.FLYER:
                e.w = 15f; e.h = 30f; e.hp = 26f; e.dmg = 8f; e.speed = 130f;
                e.a1 = rnd.nextBoolean() ? 1f : -1f;
                break;
            default:
                e.w = 27f; e.h = 76f; e.hp = 130f; e.dmg = 20f; e.speed = 70f;
                e.armor = 1f; e.kbResist = 0.65f;
                break;
        }
        e.hp *= hpScale;
        e.maxHp = e.hp;
        e.dmg *= dmgScale;
        e.cd = 0.6f + rnd.nextFloat() * 0.8f;
        enemies.add(e);
        return e;
    }

    void spawnBoss() {
        int which = lastBoss < 0 ? rnd.nextInt(2) : 1 - lastBoss;
        lastBoss = which;
        Enemy b = new Enemy();
        b.boss = true;
        b.type = which == 0 ? Ent.BOSS1 : Ent.BOSS2;
        if (which == 0) {
            b.w = 46f; b.h = 150f; b.hp = 900f; b.dmg = 26f; b.speed = 90f;
        } else {
            b.w = 26f; b.h = 96f; b.hp = 700f; b.dmg = 20f; b.speed = 140f;
        }
        b.hp *= hpScale * (1f + 0.15f * (stage - 1));
        b.maxHp = b.hp;
        b.dmg *= dmgScale;
        b.kbResist = 0.97f; b.armor = 1f;
        b.spawnT = 0f;
        b.x = Player.clamp(p.x + (p.x < WORLD_W * 0.5f ? 520f : -520f), 200f, WORLD_W - 200f);
        b.y = -520f;
        b.state = 0; b.t = 0f; b.cd = 2.2f;
        b.face = p.x >= b.x ? 1 : -1;
        enemies.add(b);
        boss = b;
        portalState = 3;
        bossIntro = 2.4f;
        flashBanner("БОС: " + Data.BOSS_NAME[which], 0xFFFF3050);
        audio.play(Audio.BOSS, 1f);
        audio.setMusic(Audio.MUS_BOSS);
        shake(0.5f);
        slowmo(0.5f, 0.8f);
        fx.screenFlash(0xFFFF3050, 0.35f);
    }

    // ---------------- combat ----------------

    void hitEnemy(Enemy e, float baseDmg, float kbX, float kbY, float stun, int power, float dir, int color) {
        if (e.hp <= 0f) return;
        boolean crit = rnd.nextFloat() < p.critChance();
        float d = baseDmg * p.dmgMul();
        if (crit) d *= p.critMul();
        if (e.boss && bossIntro > 0f) d = 0f;
        e.hp -= d;
        e.flash = 0.12f;
        float kr = 1f - e.kbResist;
        e.vx += kbX * kr;
        float vyMul = e.type == Ent.FLYER ? 0.4f : 1f;
        e.vy += kbY * kr * vyMul;
        if (stun > 0f) {
            if (e.boss) stun *= 0.12f;
            else if (e.armor > 0f && power < 1) stun = 0f;
            else if (e.armor > 0f) stun *= 0.6f;
            e.stun = Math.max(e.stun, stun);
        }
        float cx = e.x - dir * e.w * 0.3f, cy = e.y - e.h * (0.35f + rnd.nextFloat() * 0.35f);
        float pw = power == 0 ? 0.3f : (power == 1 ? 0.65f : (power == 2 ? 1f : 0.15f));
        fx.hit(cx, cy, dir, pw, crit ? 0xFFFFF04D : color);
        if (power != 3 || crit || rnd.nextFloat() < 0.3f) {
            fx.text(e.x + rnd.nextFloat() * 14f - 7f, e.y - e.h - 8f, String.valueOf((int) Math.max(1f, d)),
                    crit ? 0xFFFFF04D : 0xFFFFFFFF, crit ? 21f : 14f);
        }
        if (power == 0) { stop(0.035f); shake(0.05f); audio.play(Audio.HIT_L, 0.8f); }
        else if (power == 1) { stop(0.06f); shake(0.12f); audio.play(Audio.HIT_H, 0.9f); }
        else if (power == 2) { stop(0.09f); shake(0.22f); zoomKick = 0.035f; audio.play(Audio.HIT_H, 1f); }
        else if (rnd.nextFloat() < 0.35f) audio.play(Audio.HIT_L, 0.3f);
        if (crit) { stop(0.03f); audio.play(Audio.PICKUP, 0.25f); }
        p.combo++;
        p.comboT = 2.2f;
        // item procs
        if (p.items[4] > 0) p.heal(d * 0.015f * p.items[4]);
        if (p.items[5] > 0 && power != 3 && rnd.nextFloat() < Math.min(0.8f, 0.10f * p.items[5])) thunder(e);
        if (e.hp <= 0f) killEnemy(e, dir);
    }

    private void thunder(Enemy src) {
        int n = 0;
        float sx = src.x, sy = src.y - src.h * 0.5f;
        for (int i = 0; i < enemies.size() && n < 3; i++) {
            Enemy t = enemies.get(i);
            if (t == src || t.hp <= 0f || t.spawnT > 0f) continue;
            float dx = t.x - sx, dy = (t.y - t.h * 0.5f) - sy;
            if (dx * dx + dy * dy > 260f * 260f) continue;
            n++;
            float ang = (float) Math.atan2(dy, dx);
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            burst(1, sx, sy, ang, len, 6f, 0.18f, 0xFFFFF04D);
            fx.sparks(t.x, t.y - t.h * 0.5f, 6, 360f, 0.2f, 3f, 0xFFFFF04D, 0f, 0f, 6.3f);
            float dmg = 7f * p.dmgMul();
            t.hp -= dmg;
            t.flash = 0.1f;
            fx.text(t.x, t.y - t.h - 8f, String.valueOf((int) dmg), 0xFFFFF04D, 13f);
            if (t.hp <= 0f) killEnemy(t, 1f);
        }
        if (n > 0) audio.play(Audio.HIT_L, 0.5f);
    }

    void killEnemy(Enemy e, float dir) {
        e.hp = Math.min(e.hp, 0f);
        float cx = e.x, cy = e.y - e.h * 0.5f;
        int col = enemyColor(e);
        if (e.boss) {
            fx.explosion(cx, cy, 170f, col);
            fx.explosion(cx - 50f, cy - 30f, 110f, 0xFFFFFFFF);
            fx.explosion(cx + 60f, cy + 20f, 120f, col);
            fx.screenFlash(0xFFFFFFFF, 0.8f);
            shake(0.9f);
            slowmo(0.25f, 1.4f);
            audio.play(Audio.EXPLODE, 1f);
            audio.play(Audio.SLAM, 1f);
        } else {
            fx.explosion(cx, cy, 26f + e.h * 0.45f, col);
            audio.play(Audio.DIE, 0.7f);
            fx.sparks(cx, cy, 10, 320f, 0.4f, 3f, col, dir, -0.2f, 2.5f);
        }
        if (!e.boss) {
            kills++;
            if (portalState == 0 && kills >= killGoal) {
                portalState = 1;
                flashBanner("ПОРТАЛ ЗАРЯДЖЕНО!", 0xFF4DFFB0);
                audio.play(Audio.LEVELUP, 0.7f);
            }
        }
        totalKills++;
        float xp = e.boss ? 260f : (e.type == Ent.BRUTE ? 30f : (e.type == Ent.FLYER ? 12f : 10f));
        xp *= 1f + 0.12f * (stage - 1);
        if (p.addXp(xp)) {
            pendingLevels++;
            if (!levelUpPending) beginLevelUp();
            fx.ring(p.x, p.y - 40f, 10f, 160f, 0.5f, Data.GOLD);
        }
        // heal on kill
        if (p.items[9] > 0) p.heal(2f * p.items[9]);
        if (p.items[6] > 0 && rnd.nextFloat() < Math.min(0.8f, 0.12f * p.items[6])) {
            explodeAt(cx, cy, 90f, 18f, 0xFFFF9A3C);
        }
        rollDrops(e, cx, cy);
        if (e.boss) {
            bossesKilled++;
            portalState = 4;
            boss = null;
            flashBanner("БОСА ПОБИТО! ПОРТАЛ ВІДКРИТО", Data.GOLD);
            audio.setMusic(Audio.MUS_STAGE);
            // clear leftover hostile stuff
            teles.clear();
            for (int i = projs.size() - 1; i >= 0; i--) if (!projs.get(i).friendly) projs.remove(i);
        }
    }

    void explodeAt(float x, float y, float radius, float dmg, int color) {
        fx.explosion(x, y, radius * 0.7f, color);
        audio.play(Audio.EXPLODE, 0.5f);
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (e.hp <= 0f || e.spawnT > 0f) continue;
            float dx = e.x - x, dy = (e.y - e.h * 0.5f) - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < radius + e.w) {
                int s = dx >= 0f ? 1 : -1;
                hitEnemy(e, dmg, s * 220f, -170f, 0.35f, 1, s, color);
            }
        }
    }

    void hurtPlayer(float dmg, float srcX, float kb) {
        if (p.st == Player.ST_DEAD || p.invuln > 0f || p.hurtGrace > 0f) return;
        float d = dmg;
        if (p.st == Player.ST_CROUCH) d *= 0.4f;
        d *= 60f / (60f + p.defense());
        d = Math.max(1f, d);
        p.hp -= d;
        p.invuln = 0.65f;
        p.hitBlink = 0.65f;
        fx.screenFlash(0xFFFF2040, 0.35f);
        fx.text(p.x, p.y - 110f, String.valueOf((int) d), 0xFFFF5060, 17f);
        fx.sparks(p.x, p.y - 45f, 10, 320f, 0.3f, 3f, 0xFFFF5060, 0f, 0f, 6.3f);
        audio.play(Audio.HURT, 0.9f);
        shake(0.3f);
        stop(0.06f);
        p.onHurt(srcX, kb);
        if (p.hp <= 0f) {
            p.hp = 0f;
            dead = true;
            p.st = Player.ST_DEAD;
            p.vy = -300f;
            p.beamOn = false;
            audio.setCharging(false, 0f);
            slowmo(0.3f, 1.6f);
            fx.explosion(p.x, p.y - 40f, 80f, p.color());
            shake(0.7f);
        }
    }

    // ---------------- drops ----------------

    private void rollDrops(Enemy e, float cx, float cy) {
        float mul = p.dropMul();
        float itemP = e.boss ? 1f : (e.type == Ent.BRUTE ? 0.07f : (e.type == Ent.FLYER ? 0.02f : 0.012f));
        if (rnd.nextFloat() < itemP * mul) spawnItem(cx, cy, rnd.nextInt(Data.ITEMS));
        if (e.boss) {
            spawnItem(cx - 30f, cy, rnd.nextInt(Data.ITEMS));
            for (int i = 0; i < 4; i++) spawnOrb(cx, cy, rnd.nextBoolean() ? 0 : 1);
        } else {
            if (rnd.nextFloat() < 0.07f * mul) spawnOrb(cx, cy, 0);
            if (rnd.nextFloat() < 0.10f * mul) spawnOrb(cx, cy, 1);
        }
    }

    void spawnOrb(float x, float y, int type) {
        Drop d = new Drop();
        d.type = type; d.x = x; d.y = y;
        d.vx = (rnd.nextFloat() - 0.5f) * 240f; d.vy = -200f - rnd.nextFloat() * 160f;
        drops.add(d);
    }

    void spawnItem(float x, float y, int item) {
        Drop d = new Drop();
        d.type = 2; d.item = item; d.x = x; d.y = y;
        d.vx = (rnd.nextFloat() - 0.5f) * 120f; d.vy = -320f;
        drops.add(d);
        fx.ring(x, y, 6f, 70f, 0.4f, Data.ITEM_COL[item]);
        audio.play(Audio.ITEM, 0.5f);
    }

    void giveItem(int id) {
        p.items[id]++;
        if (id == 1) p.heal(15f);
        audio.play(Audio.ITEM, 1f);
        fx.explosion(p.x, p.y - 60f, 60f, Data.ITEM_COL[id]);
        fx.screenFlash(Data.ITEM_COL[id], 0.18f);
        flashBanner(Data.ITEM_NAME[id] + " x" + p.items[id], Data.ITEM_COL[id]);
        fx.text(p.x, p.y - 130f, Data.ITEM_DESC[id], Data.ITEM_COL[id], 13f);
    }

    // ---------------- cards ----------------

    private boolean eligible(int id) {
        if (id <= Data.C_LUK) return true;
        if (id <= Data.C_UNLT) return !p.unlocked[id - Data.C_UNL0];
        if (id <= Data.C_UPGT) return p.unlocked[id - Data.C_UPG0] && p.skLvl[id - Data.C_UPG0] < 4;
        switch (id) {
            case Data.C_REGEN: return p.regenCard < 6f;
            case Data.C_DASHCD: return p.dashCdCard > 0.45f;
            case Data.C_KIEFF: return p.kiEffCard > 0.6f;
            case Data.C_DASH2: return p.dashMax < 3;
            default: return true;
        }
    }

    void beginLevelUp() {
        levelUpPending = true;
        int forced = -1;
        switch (p.level) {
            case 2: forced = Data.C_UNL0; break;
            case 3: forced = Data.C_UNL1; break;
            case 4: forced = Data.C_UNLT; break;
            case 5: forced = Data.C_UNL2; break;
            default: break;
        }
        int n = 0;
        if (forced >= 0 && eligible(forced)) cards[n++] = forced;
        int guard = 0;
        while (n < 3 && guard++ < 200) {
            int id = rnd.nextInt(Data.CARDS);
            if (!eligible(id)) continue;
            boolean dup = false;
            for (int i = 0; i < n; i++) if (cards[i] == id) dup = true;
            if (dup) continue;
            // stat cards are more common
            if (id > Data.C_LUK && rnd.nextFloat() < 0.35f) continue;
            cards[n++] = id;
        }
        while (n < 3) cards[n++] = Data.C_STR + n;
        audio.setDuck(true);
        audio.play(Audio.LEVELUP, 1f);
        audio.setCharging(false, 0f);
    }

    void chooseCard(int i) {
        int id = cards[i];
        switch (id) {
            case Data.C_STR: p.str += 2f; break;
            case Data.C_VIT: p.vit += 2f; p.heal(10f); break;
            case Data.C_AGI: p.agi += 2f; break;
            case Data.C_SPI: p.spi += 2f; break;
            case Data.C_LUK: p.luk += 2f; break;
            case Data.C_UNL0: case Data.C_UNL1: case Data.C_UNL2: case Data.C_UNLT:
                p.unlocked[id - Data.C_UNL0] = true;
                break;
            case Data.C_UPG0: case Data.C_UPG1: case Data.C_UPG2: case Data.C_UPGT:
                p.skLvl[id - Data.C_UPG0]++;
                break;
            case Data.C_REGEN: p.regenCard += 1.2f; break;
            case Data.C_DASHCD: p.dashCdCard *= 0.8f; break;
            case Data.C_KIEFF: p.kiEffCard *= 0.88f; break;
            case Data.C_DMG: p.dmgCard += 0.10f; break;
            case Data.C_DASH2: p.dashMax++; break;
            default: p.hpPct += 0.2f; p.heal(p.maxHp() * 0.1f); break;
        }
        audio.play(Audio.PICKUP, 1f);
        fx.ring(p.x, p.y - 40f, 10f, 90f, 0.35f, Data.cardColor(id));
        pendingLevels--;
        levelUpPending = false;
        if (pendingLevels > 0) beginLevelUp();
        else audio.setDuck(false);
    }

    // ---------------- interaction ----------------

    String interactLabel() {
        if (p.st == Player.ST_DEAD) return null;
        if (Math.abs(p.x - portalX) < 100f && p.y > -120f) {
            if (portalState == 1) return "АКТИВУВАТИ ПОРТАЛ";
            if (portalState == 4) return "ДАЛІ";
        }
        for (int i = 0; i < chests.size(); i++) {
            Chest c = chests.get(i);
            if (!c.open && Math.abs(p.x - c.x) < 80f && Math.abs(p.y - c.y) < 90f) return "ВІДКРИТИ";
        }
        return null;
    }

    private void interact() {
        if (Math.abs(p.x - portalX) < 100f && p.y > -120f) {
            if (portalState == 1) {
                portalState = 2;
                despawnT = 9f;
                flashBanner("ПОРТАЛ АКТИВОВАНО!", 0xFFFF9A3C);
                audio.play(Audio.TRANSFORM, 0.8f);
                shake(0.5f);
                fx.ring(portalX, -60f, 10f, 400f, 0.6f, 0xFF4DFFB0);
                fx.screenFlash(0xFF4DFFB0, 0.3f);
                return;
            }
            if (portalState == 4) {
                audio.play(Audio.TELEPORT, 1f);
                fx.screenFlash(0xFFFFFFFF, 0.9f);
                startStage();
                return;
            }
        }
        for (int i = 0; i < chests.size(); i++) {
            Chest c = chests.get(i);
            if (!c.open && Math.abs(p.x - c.x) < 80f && Math.abs(p.y - c.y) < 90f) {
                c.open = true;
                spawnItem(c.x, c.y - 40f, rnd.nextInt(Data.ITEMS));
                fx.explosion(c.x, c.y - 20f, 50f, 0xFFFFD84A);
                audio.play(Audio.PICKUP, 1f);
                return;
            }
        }
    }

    // ---------------- main update ----------------

    void update(float rdt) {
        if (hitstop > 0f) {
            hitstop -= rdt;
            fx.update(rdt * 0.15f);
            return;
        }
        if (slowT > 0f) {
            slowT -= rdt;
            timeScale = slowScale;
        } else {
            timeScale += (1f - timeScale) * Math.min(1f, rdt * 6f);
        }
        float dt = rdt * timeScale;
        time += dt;
        runTime += dt;
        stageTime += dt;
        bannerT -= rdt;
        zoomKick = Math.max(0f, zoomKick - rdt * 0.25f);
        if (bossIntro > 0f) bossIntro -= dt;

        float dcv = dc();
        float[] hpM = {0.75f, 1f, 1.35f};
        float[] dmM = {0.7f, 1f, 1.3f};
        float[] asM = {0.85f, 1f, 1.25f};
        hpScale = (float) Math.pow(dcv, 0.85) * hpM[diff];
        dmgScale = (1f + 0.12f * (dcv - 1f)) * dmM[diff];
        atkSpd = asM[diff];

        p.update(dt);
        if (p.st != Player.ST_DEAD && ctl.interactReq) interact();
        if (p.st != Player.ST_FREE && p.st != Player.ST_CHARGE) audio.setCharging(false, 0f);

        // spawn director
        if (portalState < 2 && p.st != Player.ST_DEAD) {
            spawnT -= dt;
            if (spawnT <= 0f) {
                int alive = 0;
                for (int i = 0; i < enemies.size(); i++) if (!enemies.get(i).boss && enemies.get(i).hp > 0f) alive++;
                int maxAlive = Math.min(22, 5 + stage * 2 + diff * 2 + (int) (runTime / 90f));
                float[] spM = {0.8f, 1f, 1.3f};
                float interval = Math.max(0.55f, (2.0f - 0.1f * stage) / spM[diff] / (1f + 0.12f * (dcv - 1f)));
                spawnT = interval * (0.8f + rnd.nextFloat() * 0.4f);
                if (alive < maxAlive) {
                    int cnt = 1 + (rnd.nextFloat() < Math.min(0.5f, 0.12f * (dcv - 1f)) ? 1 : 0);
                    for (int k = 0; k < cnt; k++) spawnOne();
                }
            }
        }
        // portal sequence
        if (portalState == 2) {
            despawnT -= dt;
            int alive = 0;
            for (int i = 0; i < enemies.size(); i++) if (enemies.get(i).hp > 0f) alive++;
            if (alive == 0 || despawnT <= 0f) {
                for (int i = 0; i < enemies.size(); i++) {
                    Enemy e = enemies.get(i);
                    if (e.hp > 0f) {
                        e.hp = 0f;
                        fx.explosion(e.x, e.y - e.h * 0.5f, 40f, enemyColor(e));
                    }
                }
                spawnBoss();
            }
        }

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.hp > 0f) Ai.update(this, e, dt);
        }
        for (int i = enemies.size() - 1; i >= 0; i--) {
            if (enemies.get(i).hp <= 0f) enemies.remove(i);
        }
        updateProjs(dt);
        updateTeles(dt);
        updateDrops(dt);
        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst b = bursts.get(i);
            b.life -= dt;
            if (b.life <= 0f) bursts.remove(i);
        }
        for (int i = 0; i < chests.size(); i++) chests.get(i).t += dt;
        if ((portalState == 1 || portalState == 4) && rnd.nextFloat() < 0.6f) {
            int pc = portalState == 4 ? 0xFFFFD84A : 0xFF4DFFB0;
            float a = rnd.nextFloat() * 6.28f;
            fx.add(Fx.DOT, portalX + (float) Math.cos(a) * 46f, -62f + (float) Math.sin(a) * 62f,
                    -(float) Math.cos(a) * 40f, -(float) Math.sin(a) * 40f - 30f, 0.8f, 4f, 0f, pc, 0.5f, 0f);
        }

        // camera
        float target = p.x - viewW * 0.5f + p.face * 90f;
        target = Player.clamp(target, 0f, Math.max(0f, WORLD_W - viewW));
        camX += (target - camX) * Math.min(1f, 7f * dt);
        camX = Player.clamp(camX, 0f, Math.max(0f, WORLD_W - viewW));
        float ty = Player.clamp(p.y - viewH * 0.62f, -WORLD_H, -0.75f * viewH);
        camY += (ty - camY) * Math.min(1f, 6f * dt);
        camY = Player.clamp(camY, -WORLD_H, -0.75f * viewH);

        fx.update(dt);

        if (p.st == Player.ST_DEAD) {
            deadT += rdt;
            if (deadT > 2.2f) over = true;
        }
        ctl.clearEvents();
    }

    private void spawnOne() {
        float dcv = dc();
        float r = rnd.nextFloat();
        float pb = (stage >= 2 || dcv > 1.6f) ? 0.14f : 0f;
        int type = Ent.GRUNT;
        if (r < pb) type = Ent.BRUTE;
        else if (r < pb + 0.30f) type = Ent.FLYER;
        // моби з'являються за краєм екрана, недалеко від гравця
        float x = 0f, y = 0f;
        boolean ok = false;
        if (type == Ent.FLYER) {
            float ang = rnd.nextFloat() * 6.2832f;
            x = p.x + (float) Math.cos(ang) * viewW * 0.62f;
            y = p.y - 60f + (float) Math.sin(ang) * viewH * 0.5f;
            y = Player.clamp(y, -WORLD_H + 100f, -60f);
            ok = true;
        } else {
            for (int tries = 0; tries < 14 && !ok; tries++) {
                int side = rnd.nextBoolean() ? 1 : -1;
                x = p.x + side * (viewW * 0.52f + rnd.nextFloat() * 400f);
                float yy = p.y + (rnd.nextFloat() - 0.5f) * viewH * 0.8f;
                y = floorBelow(x, yy);
                float best = 0f;
                boolean found = y == 0f && yy > -60f;
                for (int i = 0; i < plats.size(); i++) {
                    Platform pl = plats.get(i);
                    if (Math.abs(pl.y - yy) < 130f && x > pl.x + 30f && x < pl.x + pl.w - 30f) { y = pl.y; found = true; break; }
                }
                ok = found;
            }
            if (!ok) { x = p.x + (rnd.nextBoolean() ? 1 : -1) * viewW * 0.55f; y = 0f; }
        }
        x = Player.clamp(x, 30f, WORLD_W - 30f);
        spawnEnemy(type, x, y);
    }

    // ---------------- projectiles ----------------

    private void updateProjs(float dt) {
        for (int i = projs.size() - 1; i >= 0; i--) {
            Proj b = projs.get(i);
            b.life -= dt;
            if (b.life <= 0f || b.x < -100f || b.x > WORLD_W + 100f || b.y < -WORLD_H - 400f) {
                if (b.friendly && b.aoe > 0f && b.life <= 0f) explodeAt(b.x, b.y, b.aoe, b.dmg, b.color);
                projs.remove(i);
                continue;
            }
            if (b.homing) {
                Enemy t = nearest(b.x, b.y + 40f, 650f, 0);
                if (t != null) {
                    float want = (float) Math.atan2((t.y - t.h * 0.5f) - b.y, t.x - b.x);
                    float cur = (float) Math.atan2(b.vy, b.vx);
                    float diff2 = want - cur;
                    while (diff2 > 3.1416f) diff2 -= 6.2832f;
                    while (diff2 < -3.1416f) diff2 += 6.2832f;
                    float turn = Math.max(-7f * dt, Math.min(7f * dt, diff2));
                    cur += turn;
                    float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                    sp = Math.min(760f, sp + 260f * dt);
                    b.vx = (float) Math.cos(cur) * sp;
                    b.vy = (float) Math.sin(cur) * sp;
                }
            }
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (b.kind == 4) b.y = 0f;
            if (b.friendly) {
                fx.add(Fx.DOT, b.x, b.y, 0f, 0f, 0.18f, b.r * 0.8f, 0f, b.color, 0f, 0f);
                if (b.kind == 1) {
                    fx.add(Fx.SPARK, b.x, b.y + fx.rnd(-b.r, b.r), -b.vx * 0.3f, fx.rnd(-80f, 80f), 0.2f, 3f, 0f, 0xFFFFFFFF, 0f, 0f);
                }
                boolean hit = false;
                for (int j = 0; j < enemies.size() && !hit; j++) {
                    Enemy e = enemies.get(j);
                    if (e.hp <= 0f || e.spawnT > 0f) continue;
                    if (b.x + b.r > e.x - e.w && b.x - b.r < e.x + e.w && b.y + b.r > e.y - e.h && b.y - b.r < e.y) {
                        hit = true;
                        int s = b.vx >= 0f ? 1 : -1;
                        if (b.aoe > 0f) {
                            explodeAt(b.x, b.y, b.aoe, b.dmg, b.color);
                        } else {
                            hitEnemy(e, b.dmg, s * 90f, 0f, 0.12f, 0, s, b.color);
                            p.ki = Math.min(p.maxKi(), p.ki + 1.2f);
                        }
                    }
                }
                if (!hit && b.y > 0f && b.kind != 0) {
                    hit = true;
                    if (b.aoe > 0f) explodeAt(b.x, 0f, b.aoe, b.dmg, b.color);
                }
                if (hit) projs.remove(i);
            } else {
                if (b.kind == 4) {
                    fx.add(Fx.SPARK, b.x, 0f, 0f, -g_rnd(200f, 500f), 0.22f, 4f, 0f, 0xFFFF5060, 0f, 0f);
                    fx.add(Fx.DOT, b.x, -6f, 0f, 0f, 0.2f, 10f, 0f, 0xFFFF3050, 0f, 0f);
                    if (p.y > -30f && Math.abs(p.x - b.x) < b.r + 14f) {
                        hurtPlayer(b.dmg, b.x, 280f);
                        projs.remove(i);
                    }
                } else {
                    fx.add(Fx.DOT, b.x, b.y, 0f, 0f, 0.22f, b.r, 0f, 0xFFFF3050, 0f, 0f);
                    float dx = p.x - b.x, dy = (p.y - 42f) - b.y;
                    if (Math.abs(dx) < b.r + 13f && Math.abs(dy) < b.r + 40f) {
                        fx.explosion(b.x, b.y, 28f, 0xFFFF3050);
                        hurtPlayer(b.dmg, b.x, 200f);
                        projs.remove(i);
                    }
                }
            }
        }
    }

    private float g_rnd(float a, float b) { return a + rnd.nextFloat() * (b - a); }

    // ---------------- telegraphs ----------------

    private void updateTeles(float dt) {
        for (int i = teles.size() - 1; i >= 0; i--) {
            Tele t = teles.get(i);
            t.t -= dt;
            if (t.owner != null && t.owner.hp <= 0f) { teles.remove(i); continue; }
            if (t.t <= 0f) {
                fireTele(t);
                teles.remove(i);
            }
        }
    }

    private void fireTele(Tele t) {
        int col = t.color;
        if (t.kind == 2) return; // marker only
        if (t.kind == 1) {
            float sp = t.len;
            for (int k = 0; k < t.count; k++) {
                float a = t.ang + (k - (t.count - 1) * 0.5f) * t.spread;
                newProj(3, t.x, t.y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 9f, t.dmg, 3.2f, 0f, col, false);
            }
            audio.play(Audio.SHOOT, 0.5f);
            fx.flash(t.x, t.y, 20f, 4f, 0.1f, 0xFFFFFFFF);
            return;
        }
        boolean hit = false;
        float px = p.x, py = p.y - 40f;
        if (t.shape == 0) {
            float dx = px - t.x, dy = py - t.y;
            hit = Math.sqrt(dx * dx + dy * dy) < t.w + 12f;
            fx.explosion(t.x, t.y - 10f, t.w * 0.6f, col);
            burst(0, t.x, t.y, 0f, 0f, t.w, 0.3f, col);
            audio.play(Audio.SLAM, 0.8f);
            shake(0.3f);
        } else if (t.shape == 1) {
            float ex = t.x + (float) Math.cos(t.ang) * t.len, ey = t.y + (float) Math.sin(t.ang) * t.len;
            hit = Skills.distToSeg(px, py, t.x, t.y, ex, ey) < t.w * 0.5f + 12f;
            burst(1, t.x, t.y, t.ang, t.len, t.w, 0.35f, col);
            burst(1, t.x, t.y, t.ang, t.len, t.w * 0.4f, 0.35f, 0xFFFFFFFF);
            float ca = (float) Math.cos(t.ang), sa = (float) Math.sin(t.ang);
            for (int k = 0; k < 14; k++) {
                float d = fx.rnd(0f, t.len);
                fx.add(Fx.SPARK, t.x + ca * d, t.y + sa * d, fx.rnd(-80f, 80f), fx.rnd(-300f, -50f), 0.3f, 3f, 0f, col, 1f, 0f);
            }
            audio.play(Audio.BLAST, 0.8f);
            shake(0.3f);
        } else {
            hit = Math.abs(px - t.x) < t.w * 0.5f + 12f;
            burst(2, t.x, t.y, 0f, 0f, t.w, 0.35f, col);
            fx.explosion(t.x, t.y - 6f, t.w * 0.7f, col);
            for (int k = 0; k < 12; k++) {
                fx.add(Fx.SPARK, t.x + fx.rnd(-t.w * 0.5f, t.w * 0.5f), t.y, 0f, -fx.rnd(500f, 1100f), 0.3f, 4f, 0f, k % 2 == 0 ? 0xFFFFFFFF : col, 0f, 0f);
            }
            audio.play(Audio.EXPLODE, 0.6f);
            shake(0.25f);
        }
        if (hit) hurtPlayer(t.dmg, t.x, 300f);
    }

    // ---------------- drops ----------------

    private void updateDrops(float dt) {
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            d.t += dt;
            float dx = p.x - d.x, dy = (p.y - 40f) - d.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (d.type == 2) {
                d.vy += 900f * dt;
                d.x += d.vx * dt;
                d.y += d.vy * dt;
                float fl0 = floorBelow(d.x, d.y - 30f); if (d.y > fl0 - 26f) { d.y = fl0 - 26f; d.vy = -d.vy * 0.35f; if (Math.abs(d.vy) < 40f) d.vy = 0f; d.vx *= 0.8f; }
                if (d.t > 0.4f && dist < 110f) {
                    d.x += dx / Math.max(1f, dist) * 420f * dt;
                    d.y += dy / Math.max(1f, dist) * 420f * dt;
                }
                if (fx.rnd() < 0.5f) {
                    fx.add(Fx.SPARK, d.x + fx.rnd(-6f, 6f), d.y, 0f, -fx.rnd(120f, 300f), 0.4f, 2.5f, 0f, Data.ITEM_COL[d.item], 0f, 0f);
                }
                if (d.t > 0.3f && dist < 30f && p.st != Player.ST_DEAD) {
                    giveItem(d.item);
                    drops.remove(i);
                }
            } else {
                d.vy += 1100f * dt;
                d.x += d.vx * dt;
                d.y += d.vy * dt;
                float fl1 = floorBelow(d.x, d.y - 20f); if (d.y > fl1 - 8f) { d.y = fl1 - 8f; d.vy = 0f; d.vx *= 0.8f; }
                if (d.t > 0.3f && dist < 140f) {
                    d.x += dx / Math.max(1f, dist) * 520f * dt;
                    d.y += dy / Math.max(1f, dist) * 520f * dt;
                }
                if (d.t > 0.25f && dist < 26f && p.st != Player.ST_DEAD) {
                    if (d.type == 0) {
                        p.heal(Math.max(8f, p.maxHp() * 0.08f));
                        fx.text(p.x, p.y - 120f, "+HP", 0xFF4DFF88, 13f);
                    } else {
                        p.ki = Math.min(p.maxKi(), p.ki + 18f);
                        fx.text(p.x, p.y - 120f, "+KI", 0xFF4DA6FF, 13f);
                    }
                    audio.play(Audio.PICKUP, 0.5f);
                    fx.ring(p.x, p.y - 40f, 6f, 40f, 0.2f, d.type == 0 ? 0xFF4DFF88 : 0xFF4DA6FF);
                    drops.remove(i);
                } else if (d.t > 14f) {
                    drops.remove(i);
                }
            }
        }
    }
}
