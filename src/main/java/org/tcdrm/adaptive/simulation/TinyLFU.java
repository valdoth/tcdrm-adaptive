package org.tcdrm.adaptive.simulation;

/**
 * Minimal TinyLFU-style frequency estimator with Count-Min Sketch and periodic aging.
 * Not a full implementation; good enough for admission/suppression decisions.
 */
class TinyLFU {
    private final int width;
    private final int depth;
    private final int agingPeriod;
    private final int[][] table;
    private final int[] seeds;
    private int ops;
    private int maxCount;

    TinyLFU(int width, int depth, int agingPeriod) {
        this.width = Math.max(256, width);
        this.depth = Math.max(2, depth);
        this.agingPeriod = Math.max(32, agingPeriod);
        this.table = new int[this.depth][this.width];
        this.seeds = new int[this.depth];
        for (int i = 0; i < depth; i++) seeds[i] = 0x9E3779B9 * (i + 1);
        this.ops = 0;
        this.maxCount = 1;
    }

    void increment(int key) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            int idx = index(i, key);
            int v = ++table[i][idx];
            if (v > maxCount) maxCount = v;
            if (v < min) min = v;
        }
        if (++ops % agingPeriod == 0) age();
    }

    double estimate(int key) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            int idx = index(i, key);
            int v = table[i][idx];
            if (v < min) min = v;
        }
        if (maxCount <= 0) return 0.0;
        double norm = Math.min(1.0, (double) min / (double) maxCount);
        return norm;
    }

    private void age() {
        maxCount = 1;
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                table[i][j] = table[i][j] >>> 1; // halve
                if (table[i][j] > maxCount) maxCount = table[i][j];
            }
        }
    }

    private int index(int i, int key) {
        int h = mix(key ^ seeds[i]);
        // keep positive and modulo width
        return (h & 0x7fffffff) % width;
    }

    private static int mix(int x) {
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return x;
    }
}
