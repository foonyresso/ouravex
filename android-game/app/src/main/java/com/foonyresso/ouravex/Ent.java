package com.foonyresso.ouravex;

/** Plain data holders for world entities. */
public final class Ent {
    private Ent() { }

    public static final int GRUNT = 0, FLYER = 1, BRUTE = 2, BOSS1 = 3, BOSS2 = 4;

    public static final class Enemy {
        int type;
        float x, y, vx, vy;          // x,y = bottom-center
        float w = 14f, h = 46f;      // half width, height
        float hp, maxHp, dmg, speed;
        int face = 1;
        int state;                   // AI state
        float t;                     // state timer
        float cd;                    // attack cooldown
        float stun, flash, jt;
        float kbResist;              // 0..1
        float armor;                 // >0 => light hits do not stagger
        float a1, a2, a3;            // misc AI vars
        int n1, phase, pattern;
        float spawnT = 0.35f;
        int hitStamp = -1;
        boolean boss;
        float alpha = 1f;
        float bob;
    }

    /** kind: 0 player bolt, 1 player orb, 2 player homing, 3 enemy orb, 4 enemy ground wave */
    public static final class Proj {
        int kind;
        float x, y, vx, vy, r, dmg, life, aoe, ang;
        boolean friendly, homing;
        int color;
        float trail;
    }

    /** shape: 0 circle, 1 line, 2 vertical pillar. kind: 0 zone damage, 1 spawn bolt(s) */
    public static final class Tele {
        int shape, kind;
        float x, y, ang, len, w, t, tmax, dmg;
        int color = 0xFFFF3050;
        int count;
        float spread;
        Enemy owner;
    }

    /** purely visual bright flash of a shape */
    public static final class Burst {
        int shape;
        float x, y, ang, len, w, life, maxLife;
        int color;
    }

    /** type 0 hp orb, 1 ki orb, 2 item */
    public static final class Drop {
        int type, item;
        float x, y, vx, vy, t;
    }

    public static final class Platform {
        float x, y, w;
    }

    public static final class Chest {
        float x, y;
        boolean open;
        float t;
    }
}
