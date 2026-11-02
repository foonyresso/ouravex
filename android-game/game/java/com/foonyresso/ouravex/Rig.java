package com.foonyresso.ouravex;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Procedural neon humanoid. Pose = 13 floats, local space: feet origin, +x forward, -y up. */
public final class Rig {
    public static final int N = 13;
    // 0 hipX, 1 hipY, 2 lean, 3 headDx, 4 headDy, 5-6 hand1(front), 7-8 hand2(back), 9-10 foot1(front), 11-12 foot2(back)

    public static final float[] IDLE = {0, -38, 0.12f, 2, 0, 16, -58, 8, -52, 10, 0, -10, 0};
    public static final float[] JUMP = {0, -36, 0.10f, 2, 0, 22, -60, -8, -56, 14, -14, -4, -8};
    public static final float[] FALL = {0, -40, 0.05f, 0, -2, 22, -76, -14, -72, 8, -2, -8, -6};
    public static final float[] CROUCH = {0, -22, 0.35f, 4, 3, 22, -34, 10, -30, 14, 0, -14, 0};
    public static final float[] SLIDE = {-8, -14, -1.15f, 8, 4, 22, -14, 8, -10, 34, -4, 22, 0};
    public static final float[] DASH = {-4, -34, 0.95f, 4, 2, -22, -46, -30, -40, -24, -6, -34, -14};
    public static final float[] CHARGE = {0, -32, 0.05f, 0, -2, 14, -34, -12, -34, 18, 0, -18, 0};

    public static final float[] PUNCH1_W = {-2, -38, -0.05f, 0, 0, -4, -58, 16, -58, 10, 0, -12, 0};
    public static final float[] PUNCH1_H = {4, -36, 0.35f, 4, 0, 56, -64, 4, -54, 20, 0, -14, 0};
    public static final float[] PUNCH2_W = {-2, -38, -0.05f, 0, 0, 16, -58, -4, -56, 10, 0, -12, 0};
    public static final float[] PUNCH2_H = {4, -36, 0.35f, 4, 0, 2, -54, 58, -68, 20, 0, -14, 0};
    public static final float[] KICK1_W = {-4, -38, -0.15f, 0, 0, 14, -60, 4, -54, -6, -14, -10, 0};
    public static final float[] KICK1_H = {-6, -40, -0.30f, 0, 0, -4, -70, 8, -62, 56, -46, -8, 0};
    public static final float[] KICK2_W = {-2, -38, 0.0f, 0, 0, 14, -60, 4, -54, -10, -22, -8, 0};
    public static final float[] KICK2_H = {-6, -40, -0.45f, -2, 0, -6, -74, 6, -66, 48, -74, -8, 0};
    public static final float[] UPPER_W = {0, -26, 0.30f, 2, 3, 14, -26, 6, -30, 16, 0, -14, 0};
    public static final float[] UPPER_H = {6, -46, -0.12f, 2, 0, 30, -104, 6, -60, 14, -4, -10, 0};
    public static final float[] SHOOT = {-3, -38, -0.12f, 0, 0, 50, -62, -6, -54, 12, 0, -12, 0};
    public static final float[] CAST = {0, -36, 0.10f, 2, 0, 46, -60, 40, -54, 16, 0, -14, 0};
    public static final float[] BEAM = {-2, -34, -0.05f, 0, 0, 42, -60, 36, -63, 22, 0, -20, 0};
    public static final float[] SLAM_UP = {0, -36, -0.20f, 0, -2, 10, -102, 4, -98, 10, 0, -10, 0};
    public static final float[] SLAM_DN = {8, -22, 0.70f, 6, 4, 36, -4, 28, -10, 22, 0, -16, 0};
    public static final float[] HURT = {-6, -36, -0.35f, -4, 0, -6, -70, -16, -64, 6, 0, -10, 0};

    private static final float THIGH = 20f, SHIN = 20f, UARM = 15f, FARM = 15f, TORSO = 28f;

    private static final Path hairPath = new Path();
    private static final float[] tmp = new float[2];

    public static void lerp(float[] out, float[] a, float[] b, float t) {
        for (int i = 0; i < N; i++) out[i] = a[i] + (b[i] - a[i]) * t;
    }

    public static void copy(float[] dst, float[] src) {
        System.arraycopy(src, 0, dst, 0, N);
    }

    /** Procedural run cycle. */
    public static void run(float[] out, float ph) {
        float s = (float) Math.sin(ph), c = (float) Math.cos(ph);
        out[0] = 2f;
        out[1] = -34f + Math.abs(c) * 3.5f;
        out[2] = 0.62f;
        out[3] = 6f;
        out[4] = 0f;
        // руки зігнуті, махають вперед-вгору / назад-вниз, як у бігуна
        // руки відкинуті назад, як у Наруто
        out[5] = -26f - s * 3f; out[6] = -40f - s * 2f;
        out[7] = -32f + s * 3f; out[8] = -44f + s * 2f;
        // широкий крок, коліно піднімається
        out[9] = 6f + s * 40f;  out[10] = -Math.max(0f, c) * 26f;
        out[11] = 6f - s * 40f; out[12] = -Math.max(0f, -c) * 26f;
    }

    private static int withAlpha(int color, float a) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (color & 0x00FFFFFF) | (al << 24);
    }

    private static int shade(int color, float k) {
        int r = (int) (((color >> 16) & 255) * k);
        int g = (int) (((color >> 8) & 255) * k);
        int b = (int) ((color & 255) * k);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    /** two-bone IK. writes joint to tmp. */
    private static void ik(float rx, float ry, float tx, float ty, float l1, float l2, float bend) {
        float dx = tx - rx, dy = ty - ry;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float maxD = l1 + l2 - 0.5f;
        if (d > maxD) { dx *= maxD / d; dy *= maxD / d; d = maxD; }
        if (d < 1f) { d = 1f; }
        float a = (l1 * l1 - l2 * l2 + d * d) / (2f * d);
        float h = (float) Math.sqrt(Math.max(0f, l1 * l1 - a * a));
        float ux = dx / d, uy = dy / d;
        tmp[0] = rx + ux * a + (-uy) * h * bend;
        tmp[1] = ry + uy * a + (ux) * h * bend;
    }

    private static void seg(Canvas c, Paint p, float x0, float y0, float x1, float y1, int color,
                            float w, boolean full, float alpha) {
        if (full) {
            p.setStrokeWidth(w + 7f);
            p.setColor(withAlpha(color, 0.22f * alpha));
            c.drawLine(x0, y0, x1, y1, p);
        }
        p.setStrokeWidth(w);
        p.setColor(withAlpha(color, alpha));
        c.drawLine(x0, y0, x1, y1, p);
        if (full) {
            p.setStrokeWidth(w * 0.35f);
            p.setColor(withAlpha(0xFFFFFFFF, 0.75f * alpha));
            c.drawLine(x0, y0, x1, y1, p);
        }
    }

    /**
     * Draw character. hairLen 0 = no hair. alpha 0..1. full=false draws cheap ghost (single pass).
     */
    public static void draw(Canvas c, Paint p, float x, float y, int facing, float[] pose,
                            int color, float alpha, boolean full, float hairLen, int hairColor) {
        c.save();
        c.translate(x, y);
        c.scale(facing, 1f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);

        float hipX = pose[0], hipY = pose[1], lean = pose[2];
        float sl = (float) Math.sin(lean), cl = (float) Math.cos(lean);
        float shX = hipX + sl * TORSO, shY = hipY - cl * TORSO;
        float headX = shX + sl * 13f + pose[3], headY = shY - cl * 13f + pose[4];
        int dark = shade(color, 0.62f);

        // far limbs
        ik(hipX, hipY, pose[11], pose[12], THIGH, SHIN, -1f);
        seg(c, p, hipX, hipY, tmp[0], tmp[1], dark, 6.5f, full, alpha);
        seg(c, p, tmp[0], tmp[1], pose[11], pose[12], dark, 6f, full, alpha);
        ik(shX, shY, pose[7], pose[8], UARM, FARM, 1f);
        seg(c, p, shX, shY, tmp[0], tmp[1], dark, 5.5f, full, alpha);
        seg(c, p, tmp[0], tmp[1], pose[7], pose[8], dark, 5f, full, alpha);

        // torso + head
        seg(c, p, hipX, hipY, shX, shY, color, 10f, full, alpha);
        p.setStrokeWidth(4.5f);
        p.setColor(withAlpha(color, alpha));
        if (full) {
            p.setStrokeWidth(10f);
            p.setColor(withAlpha(color, 0.22f * alpha));
            c.drawCircle(headX, headY, 9f, p);
            p.setStrokeWidth(4.5f);
            p.setColor(withAlpha(color, alpha));
        }
        c.drawCircle(headX, headY, 8f, p);
        if (hairLen > 0f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(withAlpha(hairColor, alpha));
            hairPath.reset();
            float hx = headX, hy = headY - 6f;
            hairPath.moveTo(hx - 9f, hy + 2f);
            hairPath.lineTo(hx - 12f - hairLen * 0.3f, hy - 8f - hairLen);
            hairPath.lineTo(hx - 4f, hy - 4f);
            hairPath.lineTo(hx - 2f, hy - 14f - hairLen * 1.3f);
            hairPath.lineTo(hx + 3f, hy - 5f);
            hairPath.lineTo(hx + 9f, hy - 11f - hairLen);
            hairPath.lineTo(hx + 9f, hy + 3f);
            hairPath.close();
            c.drawPath(hairPath, p);
            p.setStyle(Paint.Style.STROKE);
        }
        // eye
        p.setStrokeWidth(2.2f);
        p.setColor(withAlpha(0xFFFFFFFF, alpha));
        c.drawLine(headX + 2f, headY - 1f, headX + 6f, headY - 1f, p);

        // near limbs
        ik(hipX, hipY, pose[9], pose[10], THIGH, SHIN, -1f);
        seg(c, p, hipX, hipY, tmp[0], tmp[1], color, 7f, full, alpha);
        seg(c, p, tmp[0], tmp[1], pose[9], pose[10], color, 6.5f, full, alpha);
        ik(shX, shY, pose[5], pose[6], UARM, FARM, 1f);
        seg(c, p, shX, shY, tmp[0], tmp[1], color, 6f, full, alpha);
        seg(c, p, tmp[0], tmp[1], pose[5], pose[6], color, 5.5f, full, alpha);

        c.restore();
    }

    /** world position of the front hand / front foot for effect spawning (facing applied). */
    public static float handX(float[] pose, int facing) { return pose[5] * facing; }
    public static float handY(float[] pose) { return pose[6]; }
    public static float shoulderY(float[] pose) {
        return pose[1] - (float) Math.cos(pose[2]) * TORSO;
    }
}
