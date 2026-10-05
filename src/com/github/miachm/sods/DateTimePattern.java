// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.github.miachm.sods.PatternText.isAsciiDigit;
import static com.github.miachm.sods.PatternText.isAsciiLetter;
import static com.github.miachm.sods.PatternText.reject;

/**
 * A date/time pattern in {@code java.time.format.DateTimeFormatter} syntax,
 * restricted to a strict subset: every accepted pattern is a valid Java
 * pattern, renders the same text as Java (with {@code Locale.US}), and can
 * be stored exactly as an ODF 1.2 {@code number:date-style} or
 * {@code number:time-style}. Anything else throws
 * {@link IllegalArgumentException} naming the offending character, its index
 * and a fix. The caller chooses the kind (date/time or number), so the string
 * is never guessed.
 *
 * <p>Text in {@code '...'} is literal and {@code ''} is a literal single
 * quote ({@code "HH''mm"} &rarr; {@code 14'07}). Unquoted, only space,
 * {@code - : / . ,} and the digits {@code 0-9} are literal; any other text,
 * including {@code T}, must be quoted. Java prints unquoted digits as they
 * are ({@code "HH:mm:00"} &rarr; {@code 14:07:00}).
 *
 * <table border="1">
 * <caption>Examples for 2026-10-05 14:07:09.123</caption>
 * <tr><th>Pattern</th><th>Meaning</th><th>Example</th></tr>
 * <tr><td>{@code yy}, {@code yyyy}</td><td>year</td><td>{@code 26}, {@code 2026}</td></tr>
 * <tr><td>{@code M} to {@code MMMM}</td><td>month</td><td>{@code 10}, {@code Oct}, {@code October}</td></tr>
 * <tr><td>{@code d}, {@code dd}</td><td>day of month</td><td>{@code 5}, {@code 05}</td></tr>
 * <tr><td>{@code E} to {@code EEEE}</td><td>day of week</td><td>{@code Mon}, {@code Monday}</td></tr>
 * <tr><td>{@code QQQ}, {@code QQQQ}</td><td>quarter</td><td>{@code Q4}, {@code 4th quarter}</td></tr>
 * <tr><td>{@code H}, {@code HH}</td><td>hour 0-23</td><td>{@code 14}</td></tr>
 * <tr><td>{@code h a}, {@code hh a}</td><td>hour 1-12, requires {@code a}</td><td>{@code 2 PM}, {@code 02 PM}</td></tr>
 * <tr><td>{@code m}, {@code mm}</td><td>minute; must follow an hour or precede seconds</td><td>{@code 14:07}</td></tr>
 * <tr><td>{@code s}, {@code ss}</td><td>second</td><td>{@code 9}, {@code 09}</td></tr>
 * <tr><td>{@code ss.S} to {@code ss.SSSSSSSSS}</td><td>fraction of second, only after {@code s.}</td><td>{@code 09.1}, {@code 09.123}</td></tr>
 * </table>
 *
 * <p>Each field may appear once. Dates use the ISO calendar, as Java does
 * for a {@code LocalDateTime}. Unquoted number symbols ({@code #},
 * {@code %}) are rejected with a hint to use the number kind.
 */
final class DateTimePattern {

    /** What one letter run in a date/time pattern means; TEXT is literal text. */
    enum FieldType {
        YEAR, MONTH, DAY, DAY_OF_WEEK, QUARTER, HOUR24, HOUR12, MINUTE,
        SECOND, AMPM, TEXT
    }

    /**
     * One tokenized run. {@code "yyyy-MM-dd"} tokenizes to YEAR/4, TEXT "-",
     * MONTH/2, TEXT "-", DAY/2.
     */
    static final class Field {
        final FieldType type;
        final int length;         // repeat count, e.g. "yyyy" -> 4
        final int decimalPlaces;  // SECOND only: "ss.SSS" -> 3
        final String text;        // TEXT only: the literal characters

        private Field(FieldType type, int length, int decimalPlaces,
                String text) {
            this.type = type;
            this.length = length;
            this.decimalPlaces = decimalPlaces;
            this.text = text;
        }

        static Field of(FieldType type, int length) {
            return new Field(type, length, 0, null);
        }

        static Field ofSecond(int length, int decimalPlaces) {
            return new Field(FieldType.SECOND, length, decimalPlaces, null);
        }

        static Field ofText(String text) {
            return new Field(FieldType.TEXT, 0, 0, text);
        }
    }

    // T is not here: Java reserves letters, so users write 'T' (P1).
    // Digits are literal to DateTimeFormatter too (see isBareLiteral).
    static final String BARE_LITERALS = " -:/.,";

    final List<Field> fields;

    private DateTimePattern(List<Field> fields) {
        this.fields = fields;
    }

    /** True if the pattern has a year, month, day, weekday or quarter. */
    boolean hasDateFields() {
        for (Field field : fields) {
            switch (field.type) {
                case YEAR: case MONTH: case DAY:
                case DAY_OF_WEEK: case QUARTER:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    static DateTimePattern parse(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException(
                    "Date/time pattern cannot be null");
        }
        PatternText.rejectSpreadsheetName(pattern);
        PatternText.Flat flat = PatternText.flatten(pattern);
        List<Field> fields = scan(flat, pattern);
        checkHasFields(fields, flat, pattern);
        mergeAdjacentText(fields);
        validateEachFieldOnce(fields, pattern);
        validateHourAmPmConsistency(fields, pattern);
        validateMinuteHasNeighbour(fields, pattern);
        // Safety net (P1): whatever we accepted must be valid Java.
        try {
            DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Not a valid DateTimeFormatter pattern: " + e.getMessage()
                    + ": " + pattern, e);
        }
        return new DateTimePattern(fields);
    }

    private static boolean isBareLiteral(char c) {
        return BARE_LITERALS.indexOf(c) >= 0 || isAsciiDigit(c);
    }

    // A pattern with no letters is a number pattern or plain text.
    private static void checkHasFields(
            List<Field> fields, PatternText.Flat flat, String pattern) {
        for (Field field : fields) {
            if (field.type != FieldType.TEXT) return;
        }
        for (int i = 0; i < flat.text.length(); i++) {
            if (!flat.literal[i] && flat.text.charAt(i) == '0') {
                throw reject("No date/time letters; a digit placeholder "
                        + "means this looks like a number pattern, use "
                        + "DataFormat.number", pattern);
            }
        }
        throw reject("No date/time letters (expected letters like "
                + "y, M, d, H, h, m, s, S, a)", pattern);
    }

    private static List<Field> scan(PatternText.Flat flat, String pattern) {
        List<Field> fields = new ArrayList<>();
        String text = flat.text;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (flat.literal[i] || isBareLiteral(c)) {
                fields.add(Field.ofText(String.valueOf(c)));
                i++;
                continue;
            }
            if (!isAsciiLetter(c)) {
                if (c == '#' || c == '%') {
                    throw reject("Unquoted '" + c + "' at index " + flat.src[i]
                            + " is not allowed in a date/time pattern; this "
                            + "looks like a number pattern, use "
                            + "DataFormat.number (or quote it as '" + c
                            + "')", pattern);
                }
                if (c == '[' || c == ']') {
                    throw reject(PatternText.bracketProblem(
                            c, flat.src[i], true), pattern);
                }
                throw reject("Unquoted '" + c + "' at index " + flat.src[i]
                        + " is not allowed in a date/time pattern; quote it "
                        + "as '" + c + "'", pattern);
            }
            if (c == 'S') {
                throw reject("Fraction letter 'S' at index " + flat.src[i]
                        + " must directly follow a seconds run and '.', "
                        + "e.g. ss.SSS", pattern);
            }
            FieldType type = letterFieldType(c);
            if (type == null) {
                if (c == 'T') {
                    throw reject("Unquoted 'T' at index " + flat.src[i]
                            + "; quote it as 'T' to print it literally "
                            + "(e.g. yyyy-MM-dd'T'HH:mm)", pattern);
                }
                throw reject("Unsupported letter '" + c + "' at index "
                        + flat.src[i] + " (" + letterHint(flat, i)
                        + "); quote it as '" + c + "' to print it literally",
                        pattern);
            }
            int j = flat.runEnd(i);
            checkCount(flat, i, j, pattern);
            if (type == FieldType.SECOND && j + 1 < n
                    && !flat.literal[j] && text.charAt(j) == '.'
                    && !flat.literal[j + 1] && text.charAt(j + 1) == 'S') {
                // ODF 1.2: number:seconds carries decimal-places (P2).
                int end = flat.runEnd(j + 1);
                checkCount(flat, j + 1, end, pattern);
                fields.add(Field.ofSecond(j - i, end - j - 1));
                i = end;
                continue;
            }
            fields.add(Field.of(type, j - i));
            i = j;
        }
        return fields;
    }

    // Why a letter is rejected, with the Java (or spreadsheet) fix.
    private static String letterHint(PatternText.Flat flat, int i) {
        char c = flat.text.charAt(i);
        switch (c) {
            case 'Y': return "Java 'Y' is week-based year; for the calendar "
                    + "year use yyyy or yy";
            case 'D': return "Java 'D' is day of year; for day of month use "
                    + "d or dd";
            case 'A': return "Java 'A' is milli-of-day; for AM/PM use a";
            case 'N': return "for a weekday name use EEE or EEEE";
            case 'W': return flat.runEnd(i) - i == 2
                    ? "week of year is not supported"
                    : "Java 'W' is week of month, not supported";
            case 'w': return "week of year is not supported";
            case 'G': return "era is not supported";
            case 'q': return "use QQQ or QQQQ for the quarter";
            default: return "not representable in ODF 1.2";
        }
    }

    private static FieldType letterFieldType(char c) {
        // Case-sensitive, matching DateTimeFormatter's own letters.
        switch (c) {
            case 'y': return FieldType.YEAR;
            case 'M': return FieldType.MONTH;
            case 'd': return FieldType.DAY;
            case 'E': return FieldType.DAY_OF_WEEK;
            case 'Q': return FieldType.QUARTER;
            case 'H': return FieldType.HOUR24;
            case 'h': return FieldType.HOUR12;
            case 'm': return FieldType.MINUTE;
            case 's': return FieldType.SECOND;
            case 'a': return FieldType.AMPM;
            default: return null;
        }
    }

    // Allowed run lengths per letter; each maps to an ODF style (P2).
    private static boolean validCount(char c, int count) {
        switch (c) {
            case 'y': return count == 2 || count == 4;
            case 'M': case 'E': return count <= 4;
            case 'Q': return count == 3 || count == 4;
            case 'a': return count == 1;
            case 'S': return count <= 9;
            default: return count <= 2;
        }
    }

    private static String countHint(char c, int count) {
        if (c == 'm' && count >= 3) {
            return "minutes take m or mm; for a month name use MMM or MMMM";
        }
        if (c == 'd' && count >= 3) {
            return "day of month takes d or dd; for a weekday name use EEE "
                    + "or EEEE";
        }
        if (c == 'Q' && count <= 2) {
            return "use QQQ (spreadsheet Q) or QQQQ (spreadsheet QQ)";
        }
        switch (c) {
            case 'y': return "use yy or yyyy";
            case 'M': return "use M, MM, MMM or MMMM";
            case 'E': return "use E, EE, EEE or EEEE";
            case 'Q': return "use QQQ or QQQQ";
            case 'a': return "use a";
            case 'S': return "use 1 to 9 S";
            default: return "use " + c + " or " + c + c;
        }
    }

    private static void checkCount(
            PatternText.Flat flat, int start, int end, String pattern) {
        char c = flat.text.charAt(start);
        if (!validCount(c, end - start)) {
            throw reject("Letter run '" + flat.text.substring(start, end)
                    + "' at index " + flat.src[start] + " is not allowed; "
                    + countHint(c, end - start), pattern);
        }
    }

    private static void validateEachFieldOnce(
            List<Field> fields, String pattern) {
        // ODF 1.2 16.27.10/16.27.18: one instance of each element (P2).
        Set<FieldType> seen = EnumSet.noneOf(FieldType.class);
        for (Field field : fields) {
            if (field.type != FieldType.TEXT && !seen.add(field.type)) {
                throw reject("Date/time field " + field.type
                        + " appears more than once; ODF allows each field "
                        + "at most once", pattern);
            }
        }
    }

    private static FieldType neighbour(List<Field> fields, int from, int step) {
        for (int k = from + step; k >= 0 && k < fields.size(); k += step) {
            if (fields.get(k).type != FieldType.TEXT) {
                return fields.get(k).type;
            }
        }
        return null;
    }

    private static void validateMinuteHasNeighbour(
            List<Field> fields, String pattern) {
        // LibreOffice re-reads a lone number:minutes as month (P2).
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).type != FieldType.MINUTE) continue;
            FieldType before = neighbour(fields, i, -1);
            FieldType after = neighbour(fields, i, 1);
            boolean ok = before == FieldType.HOUR24
                    || before == FieldType.HOUR12
                    || after == FieldType.SECOND;
            if (!ok) {
                throw reject("Minutes 'm' must follow an hour field or "
                        + "precede a seconds field, otherwise ODF readers "
                        + "treat them as a month; for a month use M or MM",
                        pattern);
            }
        }
    }

    private static void validateHourAmPmConsistency(
            List<Field> fields, String pattern) {
        // ODF 1.2 16.27.22: an am-pm element makes hours 1-12 (P2).
        boolean hasHour12 = false;
        boolean hasHour24 = false;
        boolean hasAmPm = false;
        for (Field field : fields) {
            if (field.type == FieldType.HOUR12) hasHour12 = true;
            if (field.type == FieldType.HOUR24) hasHour24 = true;
            if (field.type == FieldType.AMPM) hasAmPm = true;
        }
        if (hasHour12 && hasHour24) {
            throw reject("Pattern mixes both 'H' (24-hour) and "
                    + "'h' (12-hour) tokens", pattern);
        }
        if (hasHour12 && !hasAmPm) {
            throw reject("Pattern uses 'h' (12-hour clock) "
                    + "without a paired 'a' AM/PM marker; ODF cannot "
                    + "distinguish that from a 24-hour clock. Add 'a', or "
                    + "use 'H' for a 24-hour clock (spreadsheet hh is "
                    + "24-hour; in Java that is HH)", pattern);
        }
        if (hasHour24 && hasAmPm) {
            throw reject("Pattern uses 'H' (24-hour clock) "
                    + "together with an 'a' AM/PM marker; ODF always "
                    + "renders a 12-hour clock whenever an AM/PM marker is "
                    + "present, so this is contradictory. Use 'h' for a "
                    + "12-hour clock instead", pattern);
        }
    }

    private static void mergeAdjacentText(List<Field> fields) {
        for (int i = 0; i < fields.size() - 1; ) {
            Field current = fields.get(i);
            Field next = fields.get(i + 1);
            if (current.type == FieldType.TEXT && next.type == FieldType.TEXT) {
                fields.set(i, Field.ofText(current.text + next.text));
                fields.remove(i + 1);
            } else {
                i++;
            }
        }
    }
}
