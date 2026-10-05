// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.testng.AssertJUnit.*;

/**
 * Checks the accepted pattern languages against the JDK: every accepted
 * pattern must be valid Java and render the same as an independent model
 * of the ODF semantics. Every formatter uses an explicit locale, so results
 * do not depend on the JVM default locale or time zone.
 */
public class PatternOracleTest {

    private static final LocalDateTime[] DATES = {
        LocalDateTime.parse("2026-10-05T14:07:09.123"),
        LocalDateTime.parse("1999-12-31T00:30:05"),
        LocalDateTime.parse("2000-02-29T12:00:00.05"),
        LocalDateTime.parse("2026-01-01T00:00"),
        LocalDateTime.parse("2026-12-31T23:59:59.999999999"),
        LocalDateTime.parse("1700-03-01T09:05:01"),
        // Leap years: 2024 is, 1900 and 2100 are not; day 366 of 2024.
        LocalDateTime.parse("2024-02-29T12:00"),
        LocalDateTime.parse("1900-02-28T00:00"),
        LocalDateTime.parse("1900-03-01T00:00"),
        LocalDateTime.parse("2100-03-01T00:00"),
        LocalDateTime.parse("2024-12-31T18:30"),
    };

    // BigDecimal, not double: Java multiplies doubles by 100 for percent,
    // which shifts ties; the ODF model works on exact decimals.
    private static final BigDecimal[] NUMBERS = {
        bd("0"), bd("0.5"), bd("1"), bd("-1"), bd("999"), bd("1000"),
        bd("1234567.891"), bd("1e-10"), bd("1e15"), bd("12.345"), bd("-2.5"),
    };

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    /** Renders a parsed pattern with ODF semantics, independent of the JDK. */
    static final class OdfModel {
        private static final String[] ORDINAL = {"1st", "2nd", "3rd", "4th"};

        static String render(DateTimePattern p, LocalDateTime v) {
            StringBuilder sb = new StringBuilder();
            int h = v.getHour();
            for (DateTimePattern.Field f : p.fields) {
                switch (f.type) {
                    case YEAR:
                        sb.append(f.length == 2
                                ? pad(v.getYear() % 100, 2) : pad(v.getYear(), 4));
                        break;
                    case MONTH:
                        if (f.length <= 2) {
                            sb.append(pad(v.getMonthValue(), f.length));
                        } else {
                            sb.append(Month.of(v.getMonthValue()).getDisplayName(
                                    f.length == 3 ? TextStyle.SHORT : TextStyle.FULL,
                                    Locale.US));
                        }
                        break;
                    case DAY:
                        sb.append(pad(v.getDayOfMonth(), f.length));
                        break;
                    case DAY_OF_WEEK:
                        sb.append(DayOfWeek.of(v.getDayOfWeek().getValue())
                                .getDisplayName(f.length < 4
                                        ? TextStyle.SHORT : TextStyle.FULL,
                                        Locale.US));
                        break;
                    case QUARTER:
                        int q = (v.getMonthValue() - 1) / 3 + 1;
                        sb.append(f.length == 3
                                ? "Q" + q : ORDINAL[q - 1] + " quarter");
                        break;
                    case HOUR24:
                        sb.append(pad(h, f.length));
                        break;
                    case HOUR12:
                        sb.append(pad(h % 12 == 0 ? 12 : h % 12, f.length));
                        break;
                    case AMPM:
                        sb.append(h < 12 ? "AM" : "PM");
                        break;
                    case MINUTE:
                        sb.append(pad(v.getMinute(), f.length));
                        break;
                    case SECOND:
                        sb.append(pad(v.getSecond(), f.length));
                        if (f.decimalPlaces > 0) {
                            String nanos = pad(v.getNano(), 9);
                            sb.append('.').append(nanos.substring(0, f.decimalPlaces));
                        }
                        break;
                    case TEXT:
                        sb.append(f.text);
                        break;
                    default:
                        throw new AssertionError(f.type);
                }
            }
            return sb.toString();
        }

        /** Returns null for negatives that round to zero (Java prints "-0.00"). */
        static String render(NumberPattern n, BigDecimal v) {
            BigDecimal x = n.percentage ? v.multiply(BigDecimal.valueOf(100)) : v;
            // HALF_EVEN is Java's default; LibreOffice rounds half-up.
            BigDecimal r = x.setScale(n.decimalPlaces, RoundingMode.HALF_EVEN);
            boolean negative = r.signum() < 0;
            if (v.signum() < 0 && r.signum() == 0) return null;
            String[] parts = r.abs().toPlainString().split("\\.");
            String digits = parts[0].equals("0") ? "" : parts[0];
            while (digits.length() < n.minIntegerDigits) digits = "0" + digits;
            if (n.grouping) {
                StringBuilder g = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i > 0 && (digits.length() - i) % 3 == 0) g.append(',');
                    g.append(digits.charAt(i));
                }
                digits = g.toString();
            }
            return (negative ? "-" : "") + n.prefix + digits
                    + (n.decimalPlaces > 0 ? "." + parts[1] : "") + n.suffix;
        }

        private static String pad(int value, int width) {
            String s = Integer.toString(value);
            while (s.length() < width) s = "0" + s;
            return s;
        }
    }

    private static List<String> dateTimePatterns() {
        List<String> patterns = new ArrayList<>();
        for (Object[] row : DateTimePatternTest.accepted()) {
            patterns.add((String) row[0]);
        }
        return patterns;
    }

    private static List<String> numberPatterns() {
        List<String> patterns = new ArrayList<>();
        for (Object[] row : NumberPatternTest.accepted()) {
            patterns.add((String) row[0]);
        }
        return patterns;
    }

    /** Returns a description of the first mismatch, or null if none. */
    private static String compareDateTime(DateTimePattern p, String pattern) {
        DateTimeFormatter java = DateTimeFormatter.ofPattern(pattern, Locale.US);
        for (LocalDateTime v : DATES) {
            String expected = java.format(v);
            String actual = OdfModel.render(p, v);
            if (!expected.equals(actual)) {
                return "[" + pattern + "] " + v + ": java=[" + expected
                        + "] model=[" + actual + "]";
            }
        }
        return null;
    }

    private static String compareNumber(NumberPattern p, String pattern) {
        DecimalFormat java = new DecimalFormat(
                pattern, DecimalFormatSymbols.getInstance(Locale.US));
        for (BigDecimal v : NUMBERS) {
            String actual = OdfModel.render(p, v);
            if (actual == null) continue;
            String expected = java.format(v);
            if (!expected.equals(actual)) {
                return "[" + pattern + "] " + v + ": java=[" + expected
                        + "] model=[" + actual + "]";
            }
        }
        return null;
    }

    @Test
    public void javaGateAcceptsEveryAcceptedPattern() {
        for (String pattern : dateTimePatterns()) {
            DateTimePattern.parse(pattern);
            DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        }
        for (String pattern : numberPatterns()) {
            NumberPattern.parse(pattern);
            new DecimalFormat(pattern,
                    DecimalFormatSymbols.getInstance(Locale.ROOT));
        }
    }

    @Test
    public void dateTimeModelMatchesJava() {
        for (String pattern : dateTimePatterns()) {
            String mismatch = compareDateTime(DateTimePattern.parse(pattern), pattern);
            assertNull(mismatch, mismatch);
        }
    }

    @Test
    public void numberModelMatchesJava() {
        for (String pattern : numberPatterns()) {
            String mismatch = compareNumber(NumberPattern.parse(pattern), pattern);
            assertNull(mismatch, mismatch);
        }
    }

    @DataProvider
    public Object[][] dateTimeExamples() {
        LocalDateTime dt = LocalDateTime.parse("2026-10-05T14:07:09.123");
        return new Object[][] {
            {"yy", dt, "26"}, {"yyyy", dt, "2026"}, {"M", dt, "10"},
            {"MM", dt, "10"}, {"MMM", dt, "Oct"}, {"MMMM", dt, "October"},
            {"d", dt, "5"}, {"dd", dt, "05"}, {"E", dt, "Mon"},
            {"EEEE", dt, "Monday"}, {"QQQ", dt, "Q4"},
            {"QQQQ", dt, "4th quarter"}, {"H", dt, "14"}, {"HH", dt, "14"},
            {"h a", dt, "2 PM"}, {"hh a", dt, "02 PM"},
            {"HH:mm", dt, "14:07"}, {"mm:ss", dt, "07:09"},
            {"HH''mm", dt, "14'07"},
            {"yyyy-MM-dd'T'HH:mm:ss.SSS", dt, "2026-10-05T14:07:09.123"},
            {"HH:mm:ss.S", dt, "14:07:09.1"},
            {"HH:mm:00", dt, "14:07:00"},
            {"yyyy-MM-01", dt, "2026-10-01"},
        };
    }

    @Test(dataProvider = "dateTimeExamples")
    public void dateTimeExamplesMatchJavaAndModel(
            String pattern, LocalDateTime value, String expected) {
        DateTimePattern p = DateTimePattern.parse(pattern);
        assertEquals(expected, DateTimeFormatter.ofPattern(pattern, Locale.US).format(value));
        assertEquals(expected, OdfModel.render(p, value));
    }

    @DataProvider
    public Object[][] numberExamples() {
        return new Object[][] {
            {"000.0", bd("1234.5"), "1234.5"},
            {"000.0", bd("0.5"), "000.5"},
            {"#.00", bd("1234.5"), "1234.50"},
            {"#.00", bd("0.5"), ".50"},
            {"#,##0.00", bd("1234.5"), "1,234.50"},
            {"0.00", bd("1234.5"), "1234.50"},
            {"0.0%", bd("0.5"), "50.0%"},
            {"%0.0", bd("0.5"), "%50.0"},
            {"$#,##0.00", bd("1234.5"), "$1,234.50"},
            {"0.00 kg", bd("1234.5"), "1234.50 kg"},
            {"0.00' m'", bd("1234.5"), "1234.50 m"},
            {"0.00 m", bd("1234.5"), "1234.50 m"},
            {"0 days", bd("3"), "3 days"},
            {"0.00 A", bd("1234.5"), "1234.50 A"},
        };
    }

    @Test(dataProvider = "numberExamples")
    public void numberExamplesMatchJavaAndModel(
            String pattern, BigDecimal value, String expected) {
        NumberPattern p = NumberPattern.parse(pattern);
        DecimalFormat java = new DecimalFormat(
                pattern, DecimalFormatSymbols.getInstance(Locale.US));
        assertEquals(expected, java.format(value));
        assertEquals(expected, OdfModel.render(p, value));
    }

    // The fuzz alphabet: every pattern-relevant char plus awkward ones.
    private static final String[] UNITS = unitsOf(
            "yMdEQHhmsSaGwWqDYAuLekKzZXT0#,.%E;\u2030\u00A4-' :/"
            + " \tµ\u0000[]?_*\"1259");

    private static String[] unitsOf(String chars) {
        String[] units = new String[chars.length() + 1];
        for (int i = 0; i < chars.length(); i++) units[i] = chars.substring(i, i + 1);
        units[chars.length()] = "😀";
        return units;
    }

    private static String randomUnit(Random rnd) {
        return UNITS[rnd.nextInt(UNITS.length)];
    }

    private static String mutate(String s, Random rnd) {
        int pos = s.isEmpty() ? 0 : rnd.nextInt(s.length());
        switch (rnd.nextInt(5)) {
            case 0: return s.substring(0, pos) + randomUnit(rnd) + s.substring(pos);
            case 1: return s.isEmpty() ? s : s.substring(0, pos) + s.substring(pos + 1);
            case 2: return s.isEmpty() ? s : s.substring(0, pos + 1) + s.substring(pos);
            case 3:
                if (pos + 1 >= s.length()) return s;
                return s.substring(0, pos) + s.charAt(pos + 1) + s.charAt(pos)
                        + s.substring(pos + 2);
            default:
                int end = pos + rnd.nextInt(s.length() - pos + 1);
                return s.substring(0, pos) + "'" + s.substring(pos, end) + "'"
                        + s.substring(end);
        }
    }

    private static String randomPattern(Random rnd, List<String> accepted) {
        StringBuilder sb = new StringBuilder();
        if (rnd.nextBoolean()) {
            int len = rnd.nextInt(13);
            for (int i = 0; i < len; i++) sb.append(randomUnit(rnd));
            return sb.toString();
        }
        String s = accepted.get(rnd.nextInt(accepted.size()));
        for (int i = 1 + rnd.nextInt(2); i > 0; i--) s = mutate(s, rnd);
        return s;
    }

    private interface Entry {
        /** Parses, returns a mismatch description, or null; rejects with IAE. */
        String check(String pattern);
    }

    private static void fuzz(List<String> accepted, Entry entry, long seed) {
        Random rnd = new Random(seed);
        int parsed = 0;
        for (int i = 0; i < 20000; i++) {
            String pattern = randomPattern(rnd, accepted);
            String where = "seed=" + seed + " iteration=" + i + " pattern=["
                    + pattern + "]: ";
            String mismatch;
            try {
                mismatch = entry.check(pattern);
                parsed++;
            } catch (IllegalArgumentException e) {
                assertTrue(where + e.getMessage(),
                        e.getMessage().contains(pattern));
                continue;
            } catch (RuntimeException e) {
                fail(where + "wrong exception type: " + e);
                return;
            }
            assertNull(where + mismatch, mismatch);
        }
        assertTrue("fuzz never produced an accepted pattern", parsed > 0);
    }

    @Test
    public void fuzzDateTime() {
        fuzz(dateTimePatterns(), new Entry() {
            @Override
            public String check(String pattern) {
                DateTimePattern p = DateTimePattern.parse(pattern);
                // Accepted must mean the Java gate accepts it as well.
                DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
                return compareDateTime(p, pattern);
            }
        }, 20261005L);
    }

    @Test
    public void fuzzNumber() {
        fuzz(numberPatterns(), new Entry() {
            @Override
            public String check(String pattern) {
                NumberPattern p = NumberPattern.parse(pattern);
                new DecimalFormat(pattern,
                        DecimalFormatSymbols.getInstance(Locale.ROOT));
                return compareNumber(p, pattern);
            }
        }, 20261006L);
    }
}
