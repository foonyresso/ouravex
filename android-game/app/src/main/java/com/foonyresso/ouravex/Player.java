package com.foonyresso.ouravex;

import com.foonyresso.ouravex.Ent.Enemy;

/** The hero: stats, movement, melee/ranged basics, dash, slide, charge. Skills live in Skills. */
final class Player {
    static final int ST_FREE = 0, ST_ATK = 1, ST_DASH = 2, ST_SLIDE = 3, ST_CHARGE = 4,
            ST_CROUCH = 5, ST_SKILL = 6, ST_HURT = 7, ST_DEAD = 9;

    private static final float[] BASE_STAT = {8, 7, 6, 5, 3, 6, 5, 6, 8, 4};
    private static final float[] GROW = {1.6f, 1.3f, 1.0f, 0.6f, 0.3f, 1.0f, 0.9f, 1.0f, 1.5f, 0.5f};

    // melee chain: punch, kick, punch, high kick, kick, uppercut
    private static final float[] A_DMG = {9, 10, 10, 12, 12, 20};
    private static final float[] A_REACH = {62, 68, 64, 74, 72, 80};
    private static final float[] A_KB = {120, 150, 120, 180, 190, 150};
    private static final float[][] A_W = {Rig.PUNCH1_W, Rig.KICK1_W, Rig.PUNCH2_W, Rig.KICK2_W, Rig.KICK1_W, Rig.UPPER_W};
    private static final float[][] A_H = {Rig.PUNCH1_H, Rig.KICK1_H, Rig.PUNCH2_H, Rig.KICK2_H, Rig.KICK1_H, Rig.UPPER_H};

    final Game g;
    int cls;
    float x, y, vx, vy;
    int face = 1;
    boolean ground = true;
    float hp, ki;
    int level = 1;
    float xp;
    float str, vit, agi, spi, luk;
    float dmgCard, regenCard, dashCdCard = 1f, kiEffCard = 1f, hpPct;
    int dashMax = 2;
    final int[] items = new int[Data.ITEMS];
    final boolean[] unlocked = new boolean[4];
    final int[] skLvl = new int[4];
    final float[] skCd = new float[4];
    boolean transformed;

    int st;
    float stT;
    float invuln, dashCharge = 1f, slideCd, comboT, chainT, dropT, coyote, chargeT, hurtGrace, hitBlink;
    int combo, chain, atkIdx, shotN, airJumps, slideId;
    boolean atkHit;
    float behindBonus;
    float runPh, idleT;
    float lastJumpT = 9f;
    float lungeV;

    // input buffers
    float bufJump, bufSlide, bufDash, bufAtk;
    int bufJumpDir, bufSlideDir, bufDashDir;
    final float[] bufSkill = new float[4];

    // skill state (see Skills)
    int skill, skStage, skI, blitzN;
    float skT, skNext;
    final Enemy[] blitzE = new Enemy[4];
    boolean beamOn;
    float beamX, beamY, beamAng, beamT, beamTick;

    // animation
    final float[] pose = new float[Rig.N];
    private final float[] tgt = new float[Rig.N];
    private final float[] tmpA = new float[Rig.N];
    float blendK = 18f;
    float auraT;

    Player(Game g) { this.g = g; }

    void init(int cls) {
        this.cls = cls;
        str = BASE_STAT[cls * 5]; vit = BASE_STAT[cls * 5 + 1]; agi = BASE_STAT[cls * 5 + 2];
        spi = BASE_STAT[cls * 5 + 3]; luk = BASE_STAT[cls * 5 + 4];
        level = 1; xp = 0; dmgCard = 0; regenCard = 0; dashCdCard = 1f; kiEffCard = 1f; hpPct = 0; dashMax = 2;
        for (int i = 0; i < items.length; i++) items[i] = 0;
        for (int i = 0; i < 4; i++) { unlocked[i] = false; skLvl[i] = 0; skCd[i] = 0; bufSkill[i] = 0; }
        transformed = false;
        hp = maxHp();
        ki = maxKi() * 0.4f;
        st = ST_FREE; stT = 0; invuln = 0; dashCharge = dashMax; combo = 0; chain = 0; chainT = 0;
        beamOn = false; face = 1; vx = vy = 0; ground = true; behindBonus = 0;
        Rig.copy(pose, Rig.IDLE);
    }

    // ---------- derived stats ----------
    float maxHp() { return (70f + vit * 9f + items[1] * 15f) * (1f + hpPct); }
    float maxKi() { return 70f + spi * 5f + items[2] * 6f; }
    float effLuk() { return luk + items[11] * 2f; }
    float dmgMul() {
        float m = 1f + str * 0.05f + items[0] * 0.05f + dmgCard;
        if (transformed) m *= 1.4f;
        m *= 1f + Math.min(0.3f, combo * 0.01f);
        return m;
    }
    float defense() { return vit * 1.5f + items[7] * 8f; }
    float moveSpeed() { return 470f * (1f + agi * 0.012f + items[3] * 0.04f) * (transformed ? 1.25f : 1f); }
    float aspd() { return (1f + agi * 0.015f + items[3] * 0.03f) * (transformed ? 1.25f : 1f); }
    float critChance() { return 0.05f + effLuk() * 0.006f + items[8] * 0.04f; }
    float critMul() { return 1.6f + items[8] * 0.08f; }
    float dropMul() { return 1f + effLuk() * 0.03f; }
    float kiRegen() { return 2f + spi * 0.12f + items[2] * 0.8f; }
    float hpRegen() { return 0.5f + vit * 0.05f + regenCard; }
    float chargeRate() { return 28f * (1f + spi * 0.04f); }
    float dashCd() {
        float c = 1.5f * (1f - Math.min(0.5f, agi * 0.01f)) * dashCdCard * (1f - items[10] * 0.08f);
        if (transformed) c *= 0.6f;
        return Math.max(0.15f, c);
    }
    float dashDist() { return 420f * (1f + items[10] * 0.06f); }
    float xpNeed() { return 40f + level * 28f; }
    float skillCost(int i) {
        float base = Data.SKILL_COST[cls][i];
        return base * kiEffCard * (1f - 0.1f * skLvl[i]);
    }
    float skMul(int i) { return 1f + 0.3f * skLvl[i]; }

    boolean addXp(float amt) {
        xp += amt;
        boolean up = false;
        while (xp >= xpNeed()) {
            xp -= xpNeed();
            level++;
            str += GROW[cls * 5]; vit += GROW[cls * 5 + 1]; agi += GROW[cls * 5 + 2];
            spi += GROW[cls * 5 + 3]; luk += GROW[cls * 5 + 4];
            hp = Math.min(maxHp(), hp + maxHp() * 0.3f);
            up = true;
        }
        return up;
    }

    void heal(float v) { hp = Math.min(maxHp(), hp + v); }

    int moveDir() {
        Game.Ctl c = g.ctl;
        return (c.left ? -1 : 0) + (c.right ? 1 : 0);
    }

    static float approach(float v, float t, float d) {
        if (v < t) return Math.min(t, v + d);
        return Math.max(t, v - d);
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    int color() { return transformed ? Data.GOLD : Data.CLS_COL[cls]; }
    int hairColor() { return transformed ? 0xFFFFF2A0 : Data.CLS_HAIR[cls]; }
    float hairLen() { return transformed ? 9f : (cls == 0 ? 2f : 0f); }

    // ---------- update ----------
    void update(float dt) {
        Game.Ctl c = g.ctl;
        if (st == ST_DEAD) {
            vy += Game.GRAV * 1.3f * dt;
            y += vy * dt;
            if (y > 0) { y = 0; vy = 0; }
            Rig.copy(pose, Rig.HURT);
            return;
        }
        invuln = Math.max(0f, invuln - dt);
        slideCd -= dt; comboT -= dt; chainT -= dt; dropT -= dt; hurtGrace -= dt; hitBlink -= dt;
        lastJumpT += dt;
        if (comboT <= 0f) combo = 0;
        for (int i = 0; i < 4; i++) { skCd[i] -= dt; bufSkill[i] -= dt; }
        bufJump -= dt; bufSlide -= dt; bufDash -= dt; bufAtk -= dt;
        dashCharge = Math.min(dashMax, dashCharge + dt / dashCd());
        coyote = ground ? 0.1f : coyote - dt;

        if (c.jumpReq) { bufJump = 0.16f; bufJumpDir = c.jumpDir; }
        if (c.slideReq) { bufSlide = 0.16f; bufSlideDir = c.slideDir; }
        if (c.dashReq) { bufDash = 0.14f; bufDashDir = c.dashDir; }
        if (c.atkReq) bufAtk = 0.22f;
        for (int i = 0; i < 4; i++) if (c.skillReq[i]) bufSkill[i] = 0.25f;

        hp = Math.min(maxHp(), hp + hpRegen() * dt);
        if (st != ST_CHARGE) ki = Math.min(maxKi(), ki + kiRegen() * dt);
        if (transformed) {
            ki -= 4.5f * dt;
            if (ki <= 0f) { ki = 0f; Skills.endTransform(this); }
            auraT -= dt;
            if (auraT <= 0f) { auraT = 0.03f; g.fx.aura(x, y, Data.GOLD, 1.6f); }
        }

        blendK = 18f;
        stT += dt;
        boolean gravity = true;

        switch (st) {
            case ST_FREE: freeState(dt); break;
            case ST_ATK: gravity = attackState(dt); break;
            case ST_DASH:
                gravity = false;
                vx = 0; vy = 0;
                blendK = 40f;
                if (stT >= 0.14f) { st = ST_FREE; stT = 0; }
                break;
            case ST_SLIDE: slideState(dt); break;
            case ST_CHARGE: chargeState(dt); break;
            case ST_CROUCH:
                vx = approach(vx, 0, 3500f * dt);
                if (!c.crouchHeld || !ground) { st = ST_FREE; stT = 0; }
                else if (bufDash > 0f) { if (tryDash(bufDashDir)) bufDash = 0f; }
                break;
            case ST_SKILL: gravity = Skills.update(this, dt); break;
            case ST_HURT:
                vx = approach(vx, 0, 900f * dt);
                if (stT >= 0.26f) { st = ST_FREE; stT = 0; }
                break;
            default: break;
        }

        // physics
        if (gravity) vy += Game.GRAV * 1.3f * dt;
        float py0 = y;
        x += vx * dt;
        y += vy * dt;
        x = clamp(x, 24f, Game.WORLD_W - 24f);
        boolean wasGround = ground;
        ground = false;
        if (y >= 0f) {
            y = 0f; ground = true;
            if (vy > 0f) { if (!wasGround && vy > 380f) g.fx.dust(x, 0f, face, 6); vy = 0f; }
        } else if (vy >= 0f && dropT <= 0f) {
            for (int i = 0; i < g.plats.size(); i++) {
                Ent.Platform pl = g.plats.get(i);
                if (x > pl.x - 8f && x < pl.x + pl.w + 8f && py0 <= pl.y + 2f && y >= pl.y) {
                    y = pl.y; ground = true;
                    if (!wasGround && vy > 380f) g.fx.dust(x, pl.y, face, 5);
                    vy = 0f;
                    break;
                }
            }
        }
        if (ground) airJumps = 0;

        if (st != ST_SLIDE && st != ST_SKILL) {
            // run animation phase
            runPh += Math.abs(vx) * dt * 0.036f;
        }
        updatePose(dt);
    }

    // ---------- FREE ----------
    private void freeState(float dt) {
        Game.Ctl c = g.ctl;
        int dir = moveDir();

        if (bufDash > 0f && tryDash(bufDashDir)) { bufDash = 0f; return; }
        for (int i = 0; i < 4; i++) {
            if (bufSkill[i] > 0f && Skills.tryCast(this, i)) { bufSkill[i] = 0f; return; }
        }
        if (bufJump > 0f && tryJump(bufJumpDir)) bufJump = 0f;
        if (bufSlide > 0f) {
            if (ground) { if (slideCd <= 0f) { startSlide(bufSlideDir); bufSlide = 0f; return; } }
            else { vy = Math.max(vy, 760f); g.fx.dust(x, y, face, 3); bufSlide = 0f; }
        }
        if (c.crouchHeld && ground) {
            st = ST_CROUCH; stT = 0;
            if (y < 0f) dropT = 0.28f;
            return;
        }
        if (c.chargeHeld && ground) {
            chargeT += dt;
            dir = 0;
            if (chargeT > 0.12f) { st = ST_CHARGE; stT = 0; chargeT = 0; return; }
        } else chargeT = 0f;

        if (bufAtk > 0f || (c.atkHeld && stT > 0.02f)) {
            startAttack();
            bufAtk = 0f;
            return;
        }
        float sp = moveSpeed();
        if (dir != 0) {
            face = dir;
            vx = approach(vx, dir * sp, (ground ? 6500f : 3600f) * dt);
        } else {
            vx = approach(vx, 0f, (ground ? 7000f : 900f) * dt);
        }
        if (ground && Math.abs(vx) > 150f) {
            idleT -= dt;
            if (idleT <= 0f) { idleT = 0.11f; g.fx.dust(x - face * 6f, 0f, face, 1); }
        }
    }

    boolean tryJump(int dir) {
        if (ground || coyote > 0f) {
            vy = -900f * (transformed ? 1.05f : 1f);
            vx = dir * moveSpeed() * 1.05f;
            if (dir != 0) face = dir;
            ground = false; coyote = 0f; lastJumpT = 0f;
            g.fx.dust(x, y, face, 7);
            g.audio.play(Audio.JUMP, 0.5f);
            return true;
        }
        if (airJumps < 1) {
            airJumps++;
            vy = -800f;
            if (dir != 0) { vx = dir * moveSpeed() * 1.05f; face = dir; }
            lastJumpT = 0f;
            g.fx.ring(x, y - 6f, 6f, 36f, 0.22f, color());
            g.fx.sparks(x, y, 8, 220f, 0.25f, 3f, color(), 0f, 1f, 1.6f);
            g.audio.play(Audio.JUMP, 0.5f);
            return true;
        }
        return false;
    }

    /** both swipes up within a short time: jump straight up */
    void convertJumpVertical() {
        if (lastJumpT < 0.22f) vx = 0f;
    }

    // ---------- DASH / TELEPORT ----------
    boolean tryDash(int dir) {
        if (dashCharge < 1f || dir == 0) return false;
        if (st == ST_HURT || st == ST_DEAD || st == ST_SKILL) return false;
        dashCharge -= 1f;
        float x0 = x, y0 = y;
        float dist = dashDist();
        Enemy best = null;
        float bestD = 1e9f;
        for (int i = 0; i < g.enemies.size(); i++) {
            Enemy e = g.enemies.get(i);
            if (e.hp <= 0f || e.spawnT > 0f) continue;
            float d = (e.x - x) * dir;
            float cy = e.y - e.h * 0.5f;
            if (d > 10f && d < dist + 70f && Math.abs(cy - (y - 40f)) < 130f && d < bestD) { bestD = d; best = e; }
        }
        float tx;
        if (best != null) {
            tx = best.x + dir * (best.w + 34f);
            face = -dir;
            behindBonus = 1.5f;
            g.fx.text(best.x, best.y - best.h - 18f, "ЗІ СПИНИ!", 0xFFFFF04D, 13f);
        } else {
            tx = x + dir * dist;
            face = dir;
        }
        tx = clamp(tx, 24f, Game.WORLD_W - 24f);
        // afterimages along the path
        for (int i = 0; i < 6; i++) {
            float k = i / 6f;
            g.fx.ghost(Rig.DASH, x0 + (tx - x0) * k, y0, dir, color(), hairLen(), hairColor(), 0.18f + 0.03f * i);
        }
        for (int i = 0; i < 9; i++) {
            float k = g.fx.rnd();
            g.fx.add(Fx.SPARK, x0 + (tx - x0) * k, y0 - g.fx.rnd(6f, 84f), dir * 1500f, 0f, 0.14f,
                    g.fx.rnd(1.5f, 3.5f), 0f, i % 2 == 0 ? 0xFFFFFFFF : color(), 0f, 0f);
        }
        g.fx.blink(x0, y0, color());
        x = tx;
        g.fx.blink(x, y, color());
        g.audio.play(Audio.TELEPORT, 0.9f);
        g.fx.shake(0.1f);
        invuln = Math.max(invuln, 0.28f);
        st = ST_DASH; stT = 0;
        vx = 0; vy = 0;
        atkIdx = 0;
        // dash keeps combo alive
        chainT = Math.max(chainT, 0.5f);
        return true;
    }

    // ---------- SLIDE ----------
    private void startSlide(int dir) {
        if (dir == 0) dir = face;
        face = dir;
        st = ST_SLIDE; stT = 0;
        vx = dir * 520f;
        slideId++;
        g.audio.play(Audio.WHOOSH, 0.7f);
    }

    private void slideState(float dt) {
        if (g.ctl.crouchHeld) {
            st = ST_CROUCH; stT = 0f; vx = 0f;
            return;
        }
        vx = approach(vx, 0f, 700f * dt);
        blendK = 30f;
        if (stT % 0.04f < dt) g.fx.dust(x - face * 10f, y, face, 1);
        // hit enemies in the way
        float x0 = Math.min(x, x + face * 54f), x1 = Math.max(x, x + face * 54f);
        for (int i = 0; i < g.enemies.size(); i++) {
            Enemy e = g.enemies.get(i);
            if (e.hp <= 0f || e.spawnT > 0f || e.hitStamp == slideId) continue;
            if (e.x + e.w > x0 && e.x - e.w < x1 && e.y > y - 34f && e.y - e.h < y + 6f) {
                e.hitStamp = slideId;
                g.hitEnemy(e, 9f, face * 170f, -120f, 0.3f, 0, face, color());
                ki = Math.min(maxKi(), ki + 2f);
            }
        }
        if (stT >= 0.4f || Math.abs(vx) < 40f) { st = ST_FREE; stT = 0; slideCd = 0.35f; }
    }

    // ---------- CHARGE ----------
    private void chargeState(float dt) {
        Game.Ctl c = g.ctl;
        vx = approach(vx, 0f, 4000f * dt);
        blendK = 30f;
        ki = Math.min(maxKi(), ki + chargeRate() * dt);
        float lv = ki / maxKi();
        g.audio.setCharging(true, lv);
        auraT -= dt;
        if (auraT <= 0f) {
            auraT = 0.03f;
            g.fx.aura(x, y, color(), 1f + lv * 1.2f);
            if (g.fx.rnd() < 0.25f) g.fx.ring(x, y - 30f, 50f, 4f, 0.3f, color());
        }
        if (stT % 0.4f < dt) g.fx.shake(0.04f);
        if (!c.chargeHeld || !ground) {
            st = ST_FREE; stT = 0;
            g.audio.setCharging(false, 0f);
        } else if (bufDash > 0f) {
            if (tryDash(bufDashDir)) { bufDash = 0f; g.audio.setCharging(false, 0f); }
        }
    }

    // ---------- ATTACK ----------
    private void startAttack() {
        int dir = moveDir();
        Enemy t = g.nearest(x, y, 240f, 0);
        if (dir != 0) face = dir;
        else if (t != null) face = t.x >= x ? 1 : -1;
        st = ST_ATK; stT = 0; atkHit = false;
        if (cls == 1) {
            atkIdx = 10;
            return;
        }
        if (chainT <= 0f) chain = 0;
        atkIdx = chain;
        float lunge = 150f;
        if (t != null && (t.x - x) * face > 0f) {
            float d = Math.abs(t.x - x) - t.w - 34f;
            lunge = clamp(d * 6f, 90f, 460f);
        }
        lungeV = face * lunge;
    }

    /** returns whether gravity applies */
    private boolean attackState(float dt) {
        float as = aspd();
        if (cls == 1) return shootState(dt, as);
        float wind = 0.07f / as, strike = 0.05f / as;
        float rec = (atkIdx == 5 ? 0.24f : 0.13f) / as;
        if (!atkHit && stT >= wind) {
            atkHit = true;
            meleeStrike();
            vx = lungeV;
            if (!ground) vy = Math.min(vy, -60f);
            blendK = 60f;
        }
        if (atkHit) vx = approach(vx, 0f, 2600f * dt);
        float tt = stT;
        if (tt < wind) {
            Rig.lerp(tgt, Rig.IDLE, A_W[atkIdx], tt / wind);
        } else if (tt < wind + strike) {
            Rig.lerp(tgt, A_W[atkIdx], A_H[atkIdx], (tt - wind) / strike);
            blendK = 60f;
        } else {
            Rig.lerp(tgt, A_H[atkIdx], Rig.IDLE, Math.min(1f, (tt - wind - strike) / rec));
        }
        // next attack / dash cancel
        if (atkHit && tt >= wind + strike + 0.03f) {
            if (bufDash > 0f && tryDash(bufDashDir)) { bufDash = 0f; return false; }
            for (int i = 0; i < 4; i++) {
                if (bufSkill[i] > 0f && Skills.tryCast(this, i)) { bufSkill[i] = 0f; return false; }
            }
            if (bufJump > 0f && tryJump(bufJumpDir)) { bufJump = 0f; st = ST_FREE; return true; }
            if (bufAtk > 0f || g.ctl.atkHeld) {
                bufAtk = 0f;
                startAttack();
                return true;
            }
        }
        if (tt >= wind + strike + rec) {
            st = ST_FREE; stT = 0;
        }
        return true;
    }

    private void meleeStrike() {
        int idx = atkIdx;
        float reach = A_REACH[idx];
        float bx0 = face > 0 ? x - 8f : x - reach, bx1 = face > 0 ? x + reach : x + 8f;
        float by0 = y - 110f, by1 = y + 8f;
        boolean any = false;
        float mul = behindBonus > 0f ? 1.25f : 1f;
        for (int i = 0; i < g.enemies.size(); i++) {
            Enemy e = g.enemies.get(i);
            if (e.hp <= 0f || e.spawnT > 0f) continue;
            if (e.x + e.w > bx0 && e.x - e.w < bx1 && e.y > by0 && e.y - e.h < by1) {
                any = true;
                boolean fin = idx == 5;
                g.hitEnemy(e, A_DMG[idx] * mul, face * A_KB[idx], fin ? -460f : -30f, fin ? 0.6f : 0.26f,
                        fin ? 1 : 0, face, color());
                ki = Math.min(maxKi(), ki + (fin ? 7f : 2.6f));
            }
        }
        behindBonus = 0f;
        g.audio.play(any ? (idx == 5 ? Audio.HIT_H : Audio.HIT_L) : Audio.WHOOSH, any ? 0.9f : 0.6f);
        // swing arc visual
        float ax = x + face * (reach * 0.55f), ay = y - (idx == 1 || idx == 4 ? 48f : (idx == 3 ? 66f : (idx == 5 ? 78f : 60f)));
        g.fx.sparks(ax, ay, 5, 420f, 0.14f, 2.5f, 0xFFFFFFFF, face, idx == 5 ? -0.9f : 0f, 0.7f);
        g.fx.add(Fx.SPARK, ax - face * reach * 0.4f, ay + (idx == 5 ? 26f : 0f), face * 1300f, idx == 5 ? -900f : 0f,
                0.08f, 4f, 0f, color(), 0f, 0f);
        if (idx == 5) g.fx.shake(0.18f);
        chain = (idx + 1) % 6;
        chainT = 0.5f;
    }

    private boolean shootState(float dt, float as) {
        float wind = 0.045f / as, rec = 0.10f / as;
        blendK = 40f;
        if (!atkHit && stT >= wind) {
            atkHit = true;
            fireBolts();
        }
        if (atkHit) vx = approach(vx, 0f, 900f * dt);
        Rig.copy(tgt, Rig.SHOOT);
        if ((shotN & 1) == 1) { tgt[5] = 46f; tgt[6] = -56f; tgt[7] = 8f; tgt[8] = -64f; tgt[1] = -40f; }
        if (atkHit && stT >= wind + rec * 0.6f) {
            if (bufDash > 0f && tryDash(bufDashDir)) { bufDash = 0f; return false; }
            for (int i = 0; i < 4; i++) {
                if (bufSkill[i] > 0f && Skills.tryCast(this, i)) { bufSkill[i] = 0f; return false; }
            }
            if (bufJump > 0f && tryJump(bufJumpDir)) { bufJump = 0f; st = ST_FREE; return true; }
        }
        if (stT >= wind + rec) {
            if (g.ctl.atkHeld || bufAtk > 0f) {
                bufAtk = 0f;
                int dir = moveDir();
                if (dir != 0) face = dir;
                st = ST_ATK; stT = 0; atkHit = false; atkIdx = 10;
            } else { st = ST_FREE; stT = 0; }
        }
        return true;
    }

    float aimAngle(float hx, float hy) {
        Enemy t = g.nearest(x, y, 700f, face);
        float a = face > 0 ? 0f : 3.1416f;
        if (t != null) {
            float ang = (float) Math.atan2(t.y - t.h * 0.5f - hy, t.x - hx);
            float diff = ang - a;
            while (diff > 3.1416f) diff -= 6.2832f;
            while (diff < -3.1416f) diff += 6.2832f;
            diff = clamp(diff, -0.7f, 0.7f);
            a += diff;
        }
        return a;
    }

    private void fireBolts() {
        float hx = x + face * 40f, hy = y - 62f;
        float a = aimAngle(hx, hy);
        int n = (shotN % 3 == 2) ? 2 : 1;
        for (int i = 0; i < n; i++) {
            float aa = a + (n == 2 ? (i == 0 ? -0.07f : 0.07f) : 0f);
            Ent.Proj p = g.newProj(0, hx, hy, (float) Math.cos(aa) * 880f, (float) Math.sin(aa) * 880f,
                    7f, 7f, 0.6f, 0f, color(), true);
            p.ang = aa;
        }
        shotN++;
        vx = -face * 70f;
        g.audio.play(Audio.SHOOT, 0.55f);
        g.fx.flash(hx, hy, 12f, 3f, 0.07f, 0xFFFFFFFF);
        g.fx.sparks(hx, hy, 3, 360f, 0.12f, 2f, color(), (float) Math.cos(a), (float) Math.sin(a), 0.7f);
    }

    // ---------- damage taken ----------
    void onHurt(float srcX, float kb) {
        if (st == ST_SKILL || st == ST_DEAD) {
            vx += (x < srcX ? -1 : 1) * kb * 0.3f;
            return;
        }
        g.audio.setCharging(false, 0f);
        st = ST_HURT; stT = 0;
        vx = (x < srcX ? -1f : 1f) * kb;
        vy = Math.min(vy, -160f);
        combo = 0;
    }

    // ---------- pose ----------
    private void updatePose(float dt) {
        switch (st) {
            case ST_ATK:
                if (cls == 1) { /* tgt set by shootState */ }
                else { /* tgt set by attackState */ }
                break;
            case ST_DASH: Rig.copy(tgt, Rig.DASH); break;
            case ST_SLIDE: Rig.copy(tgt, Rig.SLIDE); break;
            case ST_CHARGE:
                Rig.copy(tgt, Rig.CHARGE);
                tgt[5] += g.fx.rnd(-1.5f, 1.5f); tgt[7] += g.fx.rnd(-1.5f, 1.5f);
                tgt[1] += g.fx.rnd(-1f, 1f);
                break;
            case ST_CROUCH: Rig.copy(tgt, Rig.CROUCH); break;
            case ST_HURT: Rig.copy(tgt, Rig.HURT); break;
            case ST_SKILL: Skills.pose(this, tgt); break;
            default:
                if (!ground) {
                    Rig.copy(tgt, vy < 0f ? Rig.JUMP : Rig.FALL);
                } else if (Math.abs(vx) > 40f) {
                    Rig.run(tgt, runPh);
                } else {
                    Rig.copy(tgt, Rig.IDLE);
                    float b = (float) Math.sin(g.time * 4f);
                    tgt[1] += b * 1.2f; tgt[6] += b * 0.8f; tgt[8] += b * 0.8f;
                }
                break;
        }
        float k = Math.min(1f, blendK * dt);
        for (int i = 0; i < Rig.N; i++) pose[i] += (tgt[i] - pose[i]) * k;
    }
}
