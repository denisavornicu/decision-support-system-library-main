// Filters.java
package ro.uaic.sad;

import java.time.YearMonth;
import java.util.Map;

/** Global filters provided by the interface. Period = month in "YYYY-MM" format; null = unfiltered. */
public record Filters(String from, String to, String refFrom, String refTo, String store, String channel,
                      String category, String gender, String ageGroup, String education) {

    private static String n(String s) { return s == null || s.isBlank() || s.equals("ALL") ? null : s; }

    public static Filters of(Map<String, String> m) {
        return new Filters(n(m.get("from")), n(m.get("to")), n(m.get("refFrom")), n(m.get("refTo")), n(m.get("store")),
                n(m.get("channel")), n(m.get("category")), n(m.get("gender")), n(m.get("ageGroup")), n(m.get("education")));
    }

    /** Resolved period: current + reference (by default, the previous period of the same length). */
    public record Period(String from, String to, String refFrom, String refTo, int months) {}

    public static String shift(String ym, int months) { return YearMonth.parse(ym).plusMonths(months).toString(); }

    public static int between(String a, String b) {
        YearMonth x = YearMonth.parse(a), y = YearMonth.parse(b);
        return (y.getYear() - x.getYear()) * 12 + y.getMonthValue() - x.getMonthValue();
    }

    public Period resolve(String maxYm) {
        String t = to == null ? maxYm : to;
        String fr = from == null ? shift(t, -11) : from;
        int len = between(fr, t) + 1;
        String rf = refFrom != null && refTo != null ? refFrom : shift(fr, -len);
        String rt = refFrom != null && refTo != null ? refTo : shift(t, -len);
        return new Period(fr, t, rf, rt, len);
    }
}
