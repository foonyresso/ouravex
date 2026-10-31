package com.foonyresso.ouravex;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;

/** Fully procedural audio: synthesized SFX + live-sequenced music, mixed on one thread. */
public final class Audio {
    public static final int SR = 22050;
    private static final float TAU = 6.2831853f;

    public static final int HIT_L = 0, HIT_H = 1, WHOOSH = 2, TELEPORT = 3, JUMP = 4, SHOOT = 5,
            BLAST = 6, BEAM = 7, EXPLODE = 8, PICKUP = 9, LEVELUP = 10, HURT = 11, DIE = 12,
            WARN = 13, BOSS = 14, CLICK = 15, TRANSFORM = 16, SLAM = 17, ITEM = 18, COUNT = 19;

    public static final int MUS_OFF = 0, MUS_TITLE = 1, MUS_STAGE = 2, MUS_BOSS = 3;

    private interface Gen { float at(float t, float n); }

    private static int seed = 123456789;

    private static float noise() {
        seed ^= seed << 13;
        seed ^= seed >>> 17;
        seed ^= seed << 5;
        return (seed & 0xFFFF) / 32768f - 1f;
    }

    private static float sn(float x) { return (float) Math.sin(x); }

    private static short[] make(float dur, Gen g) {
        int n = (int) (dur * SR);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            float v = g.at(i / (float) SR, i / (float) n);
            if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
            out[i] = (short) (v * 30000);
        }
        return out;
    }

    /** sine with linearly swept frequency f0->f1 over dur */
    private static float sweep(float f0, float f1, float dur, float t) {
        return sn(TAU * (f0 * t + (f1 - f0) * t * t / (2f * dur)));
    }

    private final short[][] sfx = new short[COUNT][];

    // mixer state
    private static final int VOICES = 20;
    private final short[][] vData = new short[VOICES][];
    private final int[] vPos = new int[VOICES];
    private final float[] vVol = new float[VOICES];
    private final Object lock = new Object();

    private volatile boolean running;
    private Thread thread;
    private AudioTrack track;

    private volatile int musicMode = MUS_OFF;
    private volatile float musicTarget = 1f;
    private volatile boolean charging;
    private volatile float chargeLevel;
    public volatile boolean muted;

    public Audio() {
        buildSfx();
    }

    private void buildSfx() {
        sfx[HIT_L] = make(0.12f, (t, n) -> {
            float e = (1 - n) * (1 - n);
            return noise() * 0.6f * e + sweep(190f, 60f, 0.12f, t) * 0.9f * e;
        });
        sfx[HIT_H] = make(0.26f, (t, n) -> {
            float e = (1 - n) * (1 - n);
            float click = t < 0.012f ? noise() : 0f;
            return noise() * 0.55f * e + sweep(120f, 32f, 0.26f, t) * 1.0f * e + click;
        });
        final float[] lpW = new float[1];
        sfx[WHOOSH] = make(0.22f, (t, n) -> {
            lpW[0] += (noise() - lpW[0]) * (0.05f + 0.4f * n);
            return lpW[0] * sn(3.1416f * n) * 2.4f;
        });
        sfx[TELEPORT] = make(0.22f, (t, n) -> {
            float v;
            if (t < 0.09f) {
                v = sn(TAU * (500f * t + 4000f * t * t * t / (3f * 0.0081f))) * 0.5f;
            } else {
                float u = t - 0.09f;
                v = sn(TAU * (3000f * u - 2700f * u * u / (2f * 0.13f))) * 0.5f * (1f - u / 0.13f);
            }
            v += sn(TAU * 6000f * t) * 0.14f * (1 - n);
            if (t < 0.006f) v += noise() * 0.8f;
            return v;
        });
        sfx[JUMP] = make(0.13f, (t, n) -> sweep(240f, 560f, 0.13f, t) * 0.5f * (1 - n));
        sfx[SHOOT] = make(0.12f, (t, n) -> {
            float sq = sweep(1200f, 250f, 0.12f, t) > 0 ? 0.3f : -0.3f;
            return (sq + noise() * 0.15f) * (1 - n);
        });
        final float[] lpB = new float[1];
        sfx[BLAST] = make(0.36f, (t, n) -> {
            lpB[0] += (noise() - lpB[0]) * 0.2f;
            float e = (float) Math.pow(1 - n, 0.7);
            return sweep(100f, 500f, 0.36f, t) * 0.6f * e + lpB[0] * 0.8f * e;
        });
        sfx[BEAM] = make(1.0f, (t, n) -> {
            float saw = ((t * 110f) % 1f) * 2f - 1f;
            float e = Math.min(1f, n * 10f) * (1f - Math.max(0f, (n - 0.8f) / 0.2f));
            return (saw * 0.4f + sn(TAU * 220f * t) * 0.3f + noise() * 0.12f) * e;
        });
        final float[] lpE = new float[1];
        sfx[EXPLODE] = make(0.6f, (t, n) -> {
            lpE[0] += (noise() - lpE[0]) * 0.15f;
            float e = (float) Math.pow(1 - n, 1.6);
            return lpE[0] * 1.6f * e + sweep(90f, 28f, 0.6f, t) * e;
        });
        sfx[PICKUP] = make(0.25f, (t, n) -> sn(TAU * (n < 0.5f ? 880f : 1320f) * t) * 0.5f * (1 - n));
        final float[] arp = {523f, 659f, 784f, 1047f};
        sfx[LEVELUP] = make(0.8f, (t, n) -> {
            int k = Math.min(3, (int) (t / 0.12f));
            float local = t - k * 0.12f;
            float e = (float) Math.exp(-local * (k == 3 ? 3f : 9f)) * (n > 0.85f ? (1 - n) / 0.15f : 1f);
            return (sn(TAU * arp[k] * t) * 0.5f + sn(TAU * arp[k] * 2f * t) * 0.2f) * e;
        });
        sfx[HURT] = make(0.2f, (t, n) -> {
            float saw = ((t * (300f - 210f * n)) % 1f) * 2f - 1f;
            return (saw * 0.5f + noise() * 0.3f) * (1 - n);
        });
        final float[] lpD = new float[1];
        sfx[DIE] = make(0.3f, (t, n) -> {
            lpD[0] += (noise() - lpD[0]) * 0.3f;
            return (lpD[0] * 0.8f + sweep(400f, 60f, 0.3f, t) * 0.6f) * (1 - n);
        });
        sfx[WARN] = make(0.3f, (t, n) -> (t % 0.15f) < 0.08f ? sn(TAU * 900f * t) * 0.4f : 0f);
        final float[] lpBo = new float[1];
        sfx[BOSS] = make(1.4f, (t, n) -> {
            lpBo[0] += (noise() - lpBo[0]) * 0.03f;
            float f = 55f - 15f * n;
            float saw = ((t * f) % 1f) * 2f - 1f;
            float e = Math.min(1f, n * 6f) * (float) Math.sqrt(1 - n);
            return (saw * 0.5f + sn(TAU * 110f * t) * 0.3f + lpBo[0] * 1.5f) * e;
        });
        sfx[CLICK] = make(0.04f, (t, n) -> sn(TAU * 1500f * t) * 0.5f * (1 - n));
        final float[] lpT = new float[1];
        sfx[TRANSFORM] = make(1.2f, (t, n) -> {
            lpT[0] += (noise() - lpT[0]) * (0.05f + 0.5f * n);
            float e = (float) Math.pow(n, 0.6) * (1f - (float) Math.pow(n, 6));
            float saw = ((t * (150f + 1650f * n)) % 1f) * 2f - 1f;
            return (sweep(150f, 1800f, 1.2f, t) * 0.4f + saw * 0.2f + lpT[0] * 0.5f) * e;
        });
        final float[] lpS = new float[1];
        sfx[SLAM] = make(0.5f, (t, n) -> {
            lpS[0] += (noise() - lpS[0]) * 0.1f;
            float e = (float) Math.pow(1 - n, 1.5);
            return (sweep(70f, 25f, 0.5f, t) * 1.1f + lpS[0] * 0.7f) * e;
        });
        final float[] shim = {1046f, 1318f, 1568f, 2093f};
        sfx[ITEM] = make(0.7f, (t, n) -> {
            float v = 0f;
            for (int k = 0; k < 4; k++) {
                float lt = t - k * 0.07f;
                if (lt > 0) v += sn(TAU * shim[k] * t) * 0.25f * (float) Math.exp(-lt * 5f);
            }
            return v;
        });
    }

    // ---------------- public API ----------------

    public void play(int id, float vol) {
        if (muted || id < 0 || id >= COUNT || !running) return;
        synchronized (lock) {
            int slot = -1;
            for (int i = 0; i < VOICES; i++) {
                if (vData[i] == null) { slot = i; break; }
            }
            if (slot < 0) slot = 0;
            vData[slot] = sfx[id];
            vPos[slot] = 0;
            vVol[slot] = vol;
        }
    }

    public void setMusic(int mode) { musicMode = mode; }

    /** true = heavy duck (level-up pause) */
    public void setDuck(boolean duck) { musicTarget = duck ? 0.18f : 1f; }

    public void setCharging(boolean on, float level) {
        charging = on;
        chargeLevel = level;
    }

    public void start() {
        if (running) return;
        try {
            int minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack.Builder b = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(minBuf, 2048))
                    .setTransferMode(AudioTrack.MODE_STREAM);
            if (Build.VERSION.SDK_INT >= 26) {
                b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            }
            track = b.build();
            track.play();
        } catch (Throwable t) {
            track = null;
            return;
        }
        running = true;
        thread = new Thread(this::loop, "ouravex-audio");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
        if (track != null) {
            try { track.stop(); } catch (Throwable ignored) { }
            try { track.release(); } catch (Throwable ignored) { }
            track = null;
        }
        synchronized (lock) {
            for (int i = 0; i < VOICES; i++) vData[i] = null;
        }
    }

    // ---------------- mixer ----------------

    private void loop() {
        short[] buf = new short[512];
        while (running) {
            synchronized (lock) {
                for (int i = 0; i < buf.length; i++) buf[i] = nextSample();
            }
            AudioTrack tr = track;
            if (tr == null) break;
            tr.write(buf, 0, buf.length);
        }
    }

    // music state
    private int lastMode = -1;
    private int stepSamples = 3000;
    private int stepCounter;
    private int step;
    private int bar;
    private float bassFreq, bassEnv, bassPh;
    private float arpFreq, arpEnv, arpPh;
    private float leadFreq, leadEnv, leadPh;
    private float kickEnv, kickPh, snEnv, hatEnv, prevNoise;
    private float musGain = 0.5f;
    private float chargeAmp, chargePh, tremPh;

    private static final int[] ROOTS = {0, -4, 3, -2};
    private static final boolean[] MINOR = {true, false, false, false};
    private static final int[] BOSS_ROOTS = {0, 0, -4, -5};
    private static final int[] ARP_PAT = {0, 1, 2, 3, 2, 1, 2, 1};
    private static final int[] LEAD_PAT = {12, -99, 10, -99, 7, -99, 10, 12, 15, -99, 12, -99, 10, -99, 7, -99};

    private static float semi(float base, int s) {
        return base * (float) Math.pow(2.0, s / 12.0);
    }

    private boolean padActive;
    private float padEnv, padA, padB, padC, padPa, padPb, padPc, leadVib;
    private final float[] echo = new float[SR * 3 / 10];
    private int echoI;
    private float lpM;

    private static float tri(float ph) {
        float x = (ph / TAU) % 1f;
        return Math.abs(x * 4f - 2f) - 1f;
    }

    private short nextSample() {
        int mode = musicMode;
        if (mode != lastMode) {
            lastMode = mode;
            int bpm = mode == MUS_BOSS ? 168 : (mode == MUS_STAGE ? 148 : 92);
            stepSamples = SR * 60 / (bpm * 4);
            stepCounter = stepSamples;
            step = 0;
            bar = 0;
        }
        float out = 0f;
        float tg = musicTarget * (mode == MUS_TITLE ? 0.5f : 0.55f);
        musGain += (tg - musGain) * 0.0005f;

        if (mode != MUS_OFF) {
            if (++stepCounter >= stepSamples) {
                stepCounter = 0;
                triggerStep(mode);
                step = (step + 1) & 15;
                if (step == 0) bar = (bar + 1) & 3;
            }
            // bass
            bassPh += TAU * bassFreq / SR;
            float bs = sn(bassPh) + 0.4f * sn(bassPh * 2f) + 0.2f * (sn(bassPh * 3f));
            bassEnv *= 0.99985f;
            float m = bs * bassEnv * 0.30f;
            // arp
            arpPh += TAU * arpFreq / SR;
            float ar = tri(arpPh) * 0.55f + sn(arpPh) * 0.35f;
            arpEnv *= 0.9994f;
            m += ar * arpEnv * (mode == MUS_TITLE ? 0.10f : 0.12f);
            // lead (boss)
            if (leadEnv > 0.001f) {
                leadPh += TAU * leadFreq / SR;
                leadVib += TAU * 5.5f / SR;
                float ld = sn(leadPh + 0.25f * sn(leadVib)) * 0.7f + tri(leadPh) * 0.25f;
                leadEnv *= 0.9996f;
                m += ld * leadEnv * 0.12f;
            }
            // тепла «подушка» акордів
            if (padActive) { if (padEnv < 1f) padEnv += 1f / (SR * 0.9f); } else padEnv *= 0.9997f;
            padPa += TAU * padA / SR; padPb += TAU * padB / SR; padPc += TAU * padC / SR;
            m += (sn(padPa) + sn(padPb) + sn(padPc) + 0.4f * sn(padPa * 0.5f)) * padEnv * 0.045f;
            // drums
            if (kickEnv > 0.001f) {
                kickPh += TAU * (42f + 120f * kickEnv * kickEnv) / SR;
                m += sn(kickPh) * kickEnv * 0.55f;
                kickEnv *= 0.9992f;
            }
            float nz = noise();
            if (snEnv > 0.001f) {
                lpM += (nz - lpM) * 0.35f;
                m += (lpM * 0.8f + sn(step * 1.7f + stepCounter * 0.03f) * 0.15f) * snEnv * 0.30f;
                snEnv *= 0.9988f;
            }
            if (hatEnv > 0.001f) {
                m += (nz - prevNoise) * hatEnv * 0.07f;
                hatEnv *= 0.9975f;
            }
            prevNoise = nz;
            // м'яка луна
            float dl = echo[echoI];
            echo[echoI] = m * 0.5f + dl * 0.42f;
            if (++echoI >= echo.length) echoI = 0;
            m = m * 0.85f + dl * 0.35f;
            out += m * musGain;
        }

        // sfx voices
        float sv = 0f;
        for (int i = 0; i < VOICES; i++) {
            short[] d = vData[i];
            if (d == null) continue;
            sv += d[vPos[i]] * vVol[i] / 32768f;
            if (++vPos[i] >= d.length) vData[i] = null;
        }
        out += sv * 0.9f;

        // charge hum
        float targetAmp = charging ? 1f : 0f;
        chargeAmp += (targetAmp - chargeAmp) * 0.0008f;
        if (chargeAmp > 0.003f) {
            chargePh += TAU * (70f + chargeLevel * 150f) / SR;
            tremPh += TAU * 9f / SR;
            float h = sn(chargePh) * 0.5f + sn(chargePh * 2.01f) * 0.3f + sn(chargePh * 3.02f) * 0.12f;
            out += h * chargeAmp * (0.75f + 0.25f * sn(tremPh)) * 0.22f;
        }

        out = out / (1f + 0.5f * Math.abs(out));
        if (out > 1f) out = 1f; else if (out < -1f) out = -1f;
        return (short) (out * 28000);
    }

    private void triggerStep(int mode) {
        int[] roots = mode == MUS_BOSS ? BOSS_ROOTS : ROOTS;
        int root = roots[bar];
        boolean minor = mode == MUS_BOSS ? bar < 2 : MINOR[bar];
        int third = minor ? 3 : 4;
        float rootF = semi(55f, root);

        boolean bassHit;
        if (mode == MUS_TITLE) {
            bassHit = (step == 0 || step == 8);
        } else {
            bassHit = (step == 0 || step == 3 || step == 6 || step == 8 || step == 11 || step == 14);
        }
        if (bassHit) {
            bassFreq = (step == 6 || step == 14) ? rootF * 2f : rootF;
            bassEnv = 1f;
        }
        if (step == 0) {
            padA = semi(rootF * 2f, 0); padB = semi(rootF * 2f, third); padC = semi(rootF * 2f, 7);
            padEnv = 0f; padActive = true;
        }
        if (step == 13) padActive = false;
        // arp
        int pi = ARP_PAT[step & 7];
        int off = pi == 0 ? 0 : (pi == 1 ? third : (pi == 2 ? 7 : 12));
        if (mode != MUS_TITLE || (step & 1) == 0) {
            arpFreq = semi(rootF * 4f, off);
            arpEnv = 1f;
        }
        if (mode == MUS_BOSS) {
            int l = LEAD_PAT[step];
            if (l != -99) {
                leadFreq = semi(rootF * 8f, l - 12 + (bar >= 2 ? 0 : 0));
                leadEnv = 1f;
            }
        }
        if (mode != MUS_TITLE) {
            if (step % 4 == 0) { kickEnv = 1f; kickPh = 0f; }
            if (mode == MUS_BOSS && step == 10) { kickEnv = 0.9f; kickPh = 0f; }
            if (step == 4 || step == 12) snEnv = 1f;
            hatEnv = (step & 1) == 0 ? 0.6f : 1f;
        }
    }
}
