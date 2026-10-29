// Stats.java
package ro.uaic.sad;

/** Simple statistical utilities for estimates. */
public final class Stats {
    private Stats() {}

    /** Linear regression y = a + b*x (x = 0..n-1). Returns {a, b}. */
    public static double[] regress(double[] y) {
        int n = y.length;
        if (n < 2) return new double[]{n == 1 ? y[0] : 0, 0};
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (int i = 0; i < n; i++) { sx += i; sy += y[i]; sxy += i * y[i]; sxx += (double) i * i; }
        double b = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        return new double[]{(sy - b * sx) / n, b};
    }

    public static double mean(double[] v) { double s = 0; for (double d : v) s += d; return v.length == 0 ? 0 : s / v.length; }

    public static double std(double[] v) {
        double m = mean(v), s = 0; for (double d : v) s += (d - m) * (d - m);
        return v.length < 2 ? 0 : Math.sqrt(s / (v.length - 1));
    }

    /** Moving average over window w (the window shrinks at the beginning). */
    public static double[] movingAvg(double[] v, int w) {
        double[] r = new double[v.length];
        for (int i = 0; i < v.length; i++) { double s = 0; int c = 0; for (int j = Math.max(0, i - w + 1); j <= i; j++) { s += v[j]; c++; } r[i] = s / c; }
        return r;
    }

    public static double pct(double cur, double ref) { return ref == 0 ? 0 : (cur / ref - 1) * 100; }
    public static double round(double v, int d) { double p = Math.pow(10, d); return Math.round(v * p) / p; }
}
