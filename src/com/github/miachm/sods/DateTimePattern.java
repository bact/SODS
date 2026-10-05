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
 * restricted to a strict subset: every accepted pattern is valid Java, renders
 * the same text as Java (with {@code Locale.US}), and can be stored exactly as
 * an ODF 1.2 {@code number:date-style} or {@code number:time-style}. Anything
 * else throws {@link IllegalArgumentException}.
 *
 * <p>The rules and examples are in {@link DataFormat}; this class only
 * tokenizes and validates.
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
        final int at;             // index of the first character in the pattern

        private Field(FieldType type, int length, int decimalPlaces,
                String text, int at) {
            this.type = type;
            this.length = length;
            this.decimalPlaces = decimalPlaces;
            this.text = text;
            this.at = at;
        }

        static Field of(FieldType type, int length, int at) {
            return new Field(type, length, 0, null, at);
        }

        static Field ofSecond(int length, int decimalPlaces, int at) {
            return new Field(FieldType.SECOND, length, decimalPlaces, null, at);
        }

        static Field ofText(String text, int at) {
            return new Field(FieldType.TEXT, 0, 0, text, at);
        }
    }

    // T is not here: Java reserves letters, so users write 'T'.
    // Digits and non-ASCII characters are literal to DateTimeFormatter too
    // (see isBareLiteral); its pattern letters are ASCII only.
    private static final String BARE_LITERALS = " -:/.,()";

    final List<Field> fields;

    private DateTimePattern(List<Field> fields) {
        this.fields = fields;
    }

    static DateTimePattern parse(String pattern) {
        PatternText.rejectSpreadsheetName(pattern);
        PatternText.Flat flat = PatternText.flatten(pattern);
        List<Field> fields = scan(flat, pattern);
        checkHasFields(fields, flat, pattern);
        validateHourAmPmConsistency(fields, pattern);
        validateMinuteHasNeighbour(fields, pattern);
        mergeAdjacentText(fields);
        PatternText.requireJava("DateTimeFormatter", pattern,
                () -> DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
        return new DateTimePattern(fields);
    }

    private static boolean isBareLiteral(char c) {
        return BARE_LITERALS.indexOf(c) >= 0 || isAsciiDigit(c) || c >= 0x80;
    }

    // A pattern with no letters is a number pattern or plain text.
    private static void checkHasFields(
            List<Field> fields, PatternText.Flat flat, String pattern) {
        for (Field field : fields) {
            if (field.type != FieldType.TEXT) return;
        }
        for (int i = 0; i < flat.text.length(); i++) {
            if (!flat.literal[i] && flat.text.charAt(i) == '0') {
                throw reject("No date/time letters; a digit placeholder looks "
                        + "like a number pattern, use DataFormat.number",
                        pattern);
            }
        }
        throw reject((pattern.isEmpty() ? "Empty pattern" : "No date/time letters")
                + "; use letters like y, M, d, H, h, m, s, S, a", pattern);
    }

    private static List<Field> scan(PatternText.Flat flat, String pattern) {
        List<Field> fields = new ArrayList<>();
        Set<FieldType> seen = EnumSet.noneOf(FieldType.class);
        String text = flat.text;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (flat.literal[i] || isBareLiteral(c)) {
                fields.add(Field.ofText(String.valueOf(c), flat.src[i]));
                i++;
                continue;
            }
            if (!isAsciiLetter(c)) {
                throw PatternText.unquoted(flat.src[i], unquotedTail(c), pattern);
            }
            if (c == 'S') {
                throw reject("Fraction letter 'S' at index " + flat.src[i]
                        + " must directly follow a seconds run and '.'; "
                        + "write e.g. ss.SSS", pattern);
            }
            FieldType type = letterFieldType(c);
            if (type == null) {
                if (c == 'T') {
                    throw PatternText.unquoted(flat.src[i], "; quote it as "
                            + "'T' to print it (e.g. yyyy-MM-dd'T'HH:mm)",
                            pattern);
                }
                throw PatternText.rejectAt("Unsupported letter '" + c
                        + "' at index " + flat.src[i] + letterTail(c),
                        flat.src[i], pattern);
            }
            int j = flat.runEnd(i);
            checkCount(flat, i, j, pattern);
            // ODF 1.2 16.27.10/16.27.18: one instance of each element.
            if (!seen.add(type)) {
                throw reject("Duplicate field '" + text.substring(i, j)
                        + "' at index " + flat.src[i] + "; ODF allows each "
                        + "field once" + duplicateTail(fields, type), pattern);
            }
            if (type == FieldType.SECOND && j + 1 < n
                    && !flat.literal[j] && text.charAt(j) == '.'
                    && !flat.literal[j + 1] && text.charAt(j + 1) == 'S') {
                // ODF 1.2: number:seconds carries decimal-places.
                int end = flat.runEnd(j + 1);
                checkCount(flat, j + 1, end, pattern);
                fields.add(Field.ofSecond(j - i, end - j - 1, flat.src[i]));
                i = end;
                continue;
            }
            fields.add(Field.of(type, j - i, flat.src[i]));
            i = j;
        }
        return fields;
    }

    // Why a repeated field is rejected: a lone mm next to a date is a month.
    private static String duplicateTail(List<Field> fields, FieldType type) {
        return type == FieldType.MINUTE
                && !minuteHasNeighbour(fields, firstMinute(fields))
                ? " (spreadsheet mm next to a date is a month: use MM)"
                : ", remove one";
    }

    // Tail of the message for an unquoted non-letter, non-literal character.
    private static String unquotedTail(char c) {
        switch (c) {
            case '#': case '%':
                return "; this looks like a number pattern, use "
                        + "DataFormat.number or quote it as '" + c + "'";
            case '[': case ']':
                return PatternText.spreadsheetTail("like [h] (elapsed time) or "
                        + "[~buddhist]", c);
            case '{': case '}':
                return "; '{' and '}' are reserved, remove it (quote it as '"
                        + c + "' only to print it)";
            default:
                return "; quote it as '" + c + "'";
        }
    }

    // Why a letter is rejected, with the Java (or spreadsheet) fix.
    private static String letterTail(char c) {
        if ("zZXxOV".indexOf(c) >= 0) {
            return " (time zones cannot be stored in ODF 1.2); remove it";
        }
        return " (" + letterHint(c) + "); quote it as '" + c
                + "' to print it literally";
    }

    private static String letterHint(char c) {
        switch (c) {
            case 'Y': return "Java 'Y' is week-based year; for the calendar "
                    + "year use yyyy or yy";
            case 'D': return "Java 'D' is day of year; for day of month use "
                    + "d or dd";
            case 'A': return "Java 'A' is milli-of-day; for AM/PM use a";
            case 'N': return "Java 'N' is nano-of-day; for a weekday name "
                    + "use EEE or EEEE";
            case 'W': return "Java 'W' is week of month; spreadsheet WW (week "
                    + "of year) is not supported either";
            case 'w': return "week of year is not supported";
            case 'k': case 'K': return "Java 'k' is hour 1-24 and 'K' hour "
                    + "0-11; use H, or h with a";
            case 'G': return "era is not supported";
            case 'u': return "Java 'u' is the proleptic year; for the "
                    + "calendar year use yyyy or yy";
            case 'L': return "Java 'L' is the standalone month; use M, MM, "
                    + "MMM or MMMM";
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

    // Allowed run lengths per letter; each maps to an ODF style.
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
            case 'S': return "use S to SSSSSSSSS";
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

    private static FieldType neighbour(List<Field> fields, int from, int step) {
        for (int k = from + step; k >= 0 && k < fields.size(); k += step) {
            if (fields.get(k).type != FieldType.TEXT) {
                return fields.get(k).type;
            }
        }
        return null;
    }

    private static int firstMinute(List<Field> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).type == FieldType.MINUTE) return i;
        }
        return -1;
    }

    // LibreOffice re-reads a number:minutes without an hour before it or
    // seconds after it as a month.
    private static boolean minuteHasNeighbour(List<Field> fields, int i) {
        FieldType before = neighbour(fields, i, -1);
        return before == FieldType.HOUR24 || before == FieldType.HOUR12
                || neighbour(fields, i, 1) == FieldType.SECOND;
    }

    private static void validateMinuteHasNeighbour(
            List<Field> fields, String pattern) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).type != FieldType.MINUTE) continue;
            if (!minuteHasNeighbour(fields, i)) {
                throw reject("Minute 'm' at index " + fields.get(i).at + " is read "
                        + "as a month by LibreOffice; put it after an hour or "
                        + "before seconds, or use M or MM for a month",
                        pattern);
            }
        }
    }

    // ODF 1.2 16.27.22: an am-pm element makes hours 1-12.
    private static void validateHourAmPmConsistency(
            List<Field> fields, String pattern) {
        int h12 = -1;
        int h24 = -1;
        int ampm = -1;
        for (int i = 0; i < fields.size(); i++) {
            switch (fields.get(i).type) {
                case HOUR12: if (h12 < 0) h12 = fields.get(i).at; break;
                case HOUR24: if (h24 < 0) h24 = fields.get(i).at; break;
                case AMPM: ampm = fields.get(i).at; break;
                default: break;
            }
        }
        if (h12 >= 0 && h24 >= 0) {
            throw reject("Mixing 'H' and 'h' at index " + Math.max(h12, h24)
                    + "; use one of them", pattern);
        }
        if (h12 >= 0 && ampm < 0) {
            throw reject("'h' at index " + h12 + " is the 12-hour clock and "
                    + "needs 'a' (hh:mm a); for a 24-hour clock use HH",
                    pattern);
        }
        if (h24 >= 0 && ampm >= 0) {
            throw reject("'H' (24-hour) with 'a' at index " + ampm
                    + "; use 'h' for a 12-hour clock", pattern);
        }
    }

    private static void mergeAdjacentText(List<Field> fields) {
        for (int i = 0; i < fields.size() - 1; ) {
            Field current = fields.get(i);
            Field next = fields.get(i + 1);
            if (current.type == FieldType.TEXT && next.type == FieldType.TEXT) {
                fields.set(i, Field.ofText(current.text + next.text, current.at));
                fields.remove(i + 1);
            } else {
                i++;
            }
        }
    }
}
