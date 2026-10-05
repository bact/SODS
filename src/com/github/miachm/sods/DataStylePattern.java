// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses the pattern strings accepted by {@link Style#setDataStyle(String)}.
 *
 * <p>Patterns use {@code java.time.format.DateTimeFormatter} syntax for
 * dates and times and {@code java.text.DecimalFormat} syntax for numbers,
 * restricted to a strict subset: every accepted pattern is a valid Java
 * pattern, renders the same text as Java (with {@code Locale.US}), and can
 * be stored exactly as an ODF 1.2 {@code number:*-style}. Anything else
 * throws {@link IllegalArgumentException} naming the offending character,
 * its index and a fix.
 *
 * <p>Any unquoted {@code 0} or {@code #} makes a number pattern; otherwise
 * unquoted date/time letters make a date/time pattern. Text in
 * {@code '...'} is literal, and {@code ''} is a literal single quote
 * ({@code "HH''mm"} &rarr; {@code 14'07}).
 *
 * <p>Spreadsheet format codes are a different language: {@code "yyyy-mm-dd"}
 * means year-month-day in a spreadsheet but would be year-minute-day in
 * Java, so SODS rejects it and suggests {@code yyyy-MM-dd}. Patterns are
 * never guessed from spreadsheet syntax; such codes are rejected with a hint
 * to the Java spelling.
 *
 * <h2>Date/time letters</h2>
 * <table border="1">
 * <caption>Examples for 2026-10-05 14:07:09.123</caption>
 * <tr><th>Pattern</th><th>Meaning</th><th>Example</th></tr>
 * <tr><td>{@code yy}, {@code yyyy}</td><td>year, 2 or 4 digits</td><td>{@code 26}, {@code 2026}</td></tr>
 * <tr><td>{@code M}, {@code MM}, {@code MMM}, {@code MMMM}</td><td>month</td><td>{@code 10}, {@code 10}, {@code Oct}, {@code October}</td></tr>
 * <tr><td>{@code d}, {@code dd}</td><td>day of month</td><td>{@code 5}, {@code 05}</td></tr>
 * <tr><td>{@code E} to {@code EEE}, {@code EEEE}</td><td>day of week</td><td>{@code Mon}, {@code Monday}</td></tr>
 * <tr><td>{@code QQQ}, {@code QQQQ}</td><td>quarter</td><td>{@code Q4}, {@code 4th quarter}</td></tr>
 * <tr><td>{@code H}, {@code HH}</td><td>hour 0-23</td><td>{@code 14}, {@code 14}</td></tr>
 * <tr><td>{@code h a}, {@code hh a}</td><td>hour 1-12, requires {@code a} (AM/PM)</td><td>{@code 2 PM}, {@code 02 PM}</td></tr>
 * <tr><td>{@code m}, {@code mm}</td><td>minute; must follow an hour or precede seconds</td><td>{@code HH:mm} &rarr; {@code 14:07}</td></tr>
 * <tr><td>{@code s}, {@code ss}</td><td>second</td><td>{@code 9}, {@code 09}</td></tr>
 * <tr><td>{@code ss.S} to {@code ss.SSSSSSSSS}</td><td>fraction of second, only after {@code s.}</td><td>{@code 09.1}, {@code 09.123}</td></tr>
 * </table>
 *
 * <p>Each field may appear once. Unquoted separators may be space and
 * {@code - : / . ,}; quote any other text, including {@code T}:
 * {@code "yyyy-MM-dd'T'HH:mm:ss.SSS"} &rarr;
 * {@code 2026-10-05T14:07:09.123}.
 *
 * <p>Dates use the ISO (proleptic Gregorian) calendar, as Java does when
 * formatting a {@code LocalDate} or {@code LocalDateTime}; a calendar
 * named in a locale (e.g. {@code th-TH-u-ca-buddhist}) does not change
 * that. Other calendars are not supported: patterns have no calendar
 * letter, and Java only switches calendar through
 * {@code DateTimeFormatter.withChronology}.
 *
 * <h2>Number symbols</h2>
 * <table border="1">
 * <caption>Examples for 1234.5 and 0.5</caption>
 * <tr><th>Symbol</th><th>Meaning</th><th>Example</th></tr>
 * <tr><td>{@code 0}</td><td>mandatory digit</td><td>{@code 000.0} &rarr; {@code 1234.5}, {@code 000.5}</td></tr>
 * <tr><td>{@code #}</td><td>optional integer digit, before any {@code 0}</td><td>{@code #.00} &rarr; {@code 1234.50}, {@code .50}</td></tr>
 * <tr><td>{@code ,}</td><td>grouping; exactly 3 digits after the last comma</td><td>{@code #,##0.00} &rarr; {@code 1,234.50}</td></tr>
 * <tr><td>{@code .}</td><td>decimal point; only {@code 0} after it</td><td>{@code 0.00} &rarr; {@code 1234.50}</td></tr>
 * <tr><td>{@code %}</td><td>percentage (value &times; 100); once, in the prefix or suffix</td><td>{@code 0.0%} &rarr; {@code 50.0%}; {@code %0.0} &rarr; {@code %50.0}</td></tr>
 * <tr><td>other text</td><td>literal prefix or suffix</td><td>{@code $#,##0.00} &rarr; {@code $1,234.50}; {@code 0.00 kg} &rarr; {@code 1234.50 kg}</td></tr>
 * </table>
 *
 * <h2>Rejected, and what to use instead</h2>
 * <ul>
 *     <li>{@code y}, {@code yyy}: use {@code yy} or {@code yyyy}.</li>
 *     <li>{@code Q}, {@code QQ}, {@code q}: use {@code QQQ} or {@code QQQQ}.</li>
 *     <li>Bare {@code T}: use {@code 'T'}.</li>
 *     <li>{@code G} (era), {@code w} (week) and every letter not listed
 *     above: no matching ODF 1.2 rendering. Quote a letter to print it.</li>
 *     <li>{@code h} without {@code a}, {@code H} with {@code a}, a lone
 *     {@code S}, {@code m} without an hour or seconds next to it, a
 *     repeated field.</li>
 *     <li>{@code 0.0#}, {@code #.##}: use {@code 0.00} (ODF 1.2 has a
 *     fixed number of decimal places).</li>
 *     <li>{@code E} (scientific notation); quote it in text, e.g.
 *     {@code "0.00 'EUR'"}.</li>
 *     <li>Unquoted {@code ?} (spreadsheet fraction digit, e.g. {@code "# ?/?"}): fractions are not supported; quote it to print it.</li>
 *     <li>Other spreadsheet format-code syntax: unquoted {@code [} {@code ]} (colours, conditions, elapsed time), {@code "} (use {@code '}), {@code \}, {@code _} and {@code *}; quote them to print them.</li>
 *     <li>{@code ;} (negative subpattern), &permil;, &curren;,
 *     a grouping size other than 3, and bare {@code -} (use
 *     {@code '-'}).</li>
 *     <li>Uppercase {@code Y}, {@code D}, {@code A} anywhere, as a typo
 *     guard; quote them to print them.</li>
 * </ul>
 *
 * <h2>Known rendering differences</h2>
 * <p>These come from the value or the reader, not from the pattern:
 * <ul>
 *     <li>Rounding: Java rounds the exact binary value half-even;
 *     LibreOffice rounds a 15-digit decimal half-up ({@code 0.00} on 1.005:
 *     Java {@code 1.00}, LibreOffice {@code 1.01}; {@code 0} on 2.5:
 *     Java {@code 2}, LibreOffice {@code 3}).</li>
 *     <li>LibreOffice shows at most 15 significant digits; Java shows the
 *     full double ({@code 123456789012345680} vs
 *     {@code 123456789012346000}). This is a reader limit: ODF puts no
 *     maximum on decimal places, so neither does this class.</li>
 *     <li>Java shows {@code -0.00} for negative zero; LibreOffice shows
 *     {@code 0.00}.</li>
 *     <li>LibreOffice uses the Julian calendar before 1582-10-15.</li>
 *     <li>Names, AM/PM text, separators and digits come from locale data,
 *     which differs between LibreOffice and each JDK for most locales; the
 *     examples above hold for {@code Locale.US}.</li>
 * </ul>
 *
 * <h2>Legacy patterns</h2>
 * <p>{@code @} (plain text) and {@code YYYY-MM-DD} (same as
 * {@code yyyy-MM-dd}) are kept for backward compatibility. They are matched
 * exactly and are not Java patterns.
 *
 * @see <a href="https://docs.oasis-open.org/office/v1.2/os/OpenDocument-v1.2-os-part1.html#__RefHeading__1416346_253892949">OpenDocument v1.2 Part 1: OpenDocument Schema</a>
 * @see <a href="https://docs.oasis-open.org/office/v1.2/os/OpenDocument-v1.2-os-schema.rng">OpenDocument v1.2 RelaxNG schema</a>
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html">java.time.format.DateTimeFormatter</a>
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/text/DecimalFormat.html">java.text.DecimalFormat</a>
 */
final class DataStylePattern {

    // Which ODF style family a whole pattern becomes, e.g. "yyyy-MM-dd" ->
    // DATE_TIME, "0.00" -> NUMBER, "@" -> TEXT.
    enum Kind { TEXT, DATE_TIME, NUMBER }

    // What one letter run in a date/time pattern means, e.g. 'y' -> YEAR,
    // 's' -> SECOND. TEXT is literal/separator text, not a letter run.
    enum DateTimeFieldType {
        YEAR, MONTH, DAY, DAY_OF_WEEK, QUARTER, HOUR24, HOUR12, MINUTE,
        SECOND, AMPM, TEXT
    }

    // One tokenized run from a date/time pattern. "yyyy-MM-dd" tokenizes
    // to [DateTimeField(YEAR,4), DateTimeField(TEXT,"-"),
    // DateTimeField(MONTH,2), DateTimeField(TEXT,"-"),
    // DateTimeField(DAY,2)].
    static final class DateTimeField {
        final DateTimeFieldType type;  // e.g. YEAR
        final int length;              // repeat count, e.g. "yyyy" -> 4
        final int decimalPlaces;       // SECOND only: "ss.SSS" -> 3
        final String text;             // TEXT only: the literal characters

        private DateTimeField(
                DateTimeFieldType type, int length, int decimalPlaces,
                String text) {
            this.type = type;
            this.length = length;
            this.decimalPlaces = decimalPlaces;
            this.text = text;
        }

        static DateTimeField of(DateTimeFieldType type, int length) {
            return new DateTimeField(type, length, 0, null);
        }

        static DateTimeField ofSecond(int length, int decimalPlaces) {
            return new DateTimeField(
                    DateTimeFieldType.SECOND, length, decimalPlaces, null);
        }

        static DateTimeField ofText(String text) {
            return new DateTimeField(DateTimeFieldType.TEXT, 0, 0, text);
        }
    }

    // The whole parsed shape of a numeric pattern, e.g. "$#,##0.00%" ->
    // prefix="$", minIntegerDigits=1, decimalPlaces=2, grouping=true,
    // percentage=true, suffix="%".
    static final class NumberSpec {
        final String prefix;           // e.g. "$#,##0.00" -> "$"
        final String suffix;           // e.g. "0.00 kg" -> " kg"
        final int minIntegerDigits;    // count of '0's in the integer part
        final int decimalPlaces;       // count of '0's after '.'
        final boolean grouping;        // ',' present
        final boolean percentage;      // '%' present (kept in prefix/suffix)

        NumberSpec(
                String prefix,
                String suffix,
                int minIntegerDigits,
                int decimalPlaces,
                boolean grouping,
                boolean percentage) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.minIntegerDigits = minIntegerDigits;
            this.decimalPlaces = decimalPlaces;
            this.grouping = grouping;
            this.percentage = percentage;
        }
    }

    // Y/D/A have no valid meaning anywhere in this grammar, so they are
    // rejected even inside an otherwise-permissive NUMBER pattern.
    private static final String ALWAYS_INVALID_LETTERS = "YDA";
    private static final String LEGACY_ISO_DATE_LITERAL = "YYYY-MM-DD";
    private static final String CANONICAL_ISO_DATE = "yyyy-MM-dd";
    // T is not here: Java reserves letters, so users write 'T' (P1).
    static final String ALLOWED_BARE_LITERALS = " -:/.,";

    final Kind kind;
    final List<DateTimeField> dateTimeFields;
    final NumberSpec numberSpec;

    private DataStylePattern(
            Kind kind, List<DateTimeField> dateTimeFields,
            NumberSpec numberSpec) {
        this.kind = kind;
        this.dateTimeFields = dateTimeFields;
        this.numberSpec = numberSpec;
    }

    boolean hasDateFields() {
        if (dateTimeFields == null) return false;
        for (DateTimeField field : dateTimeFields) {
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

    static DataStylePattern parse(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException(
                    "Data style pattern cannot be null");
        }
        if (Style.PLAIN_DATA_STYLE.equals(pattern)) {
            return new DataStylePattern(Kind.TEXT, null, null);
        }
        if (LEGACY_ISO_DATE_LITERAL.equals(pattern)) {
            // Permanent compat shim for the pre-existing literal.
            return parse(CANONICAL_ISO_DATE);
        }

        if ("General".equals(pattern) || "Standard".equals(pattern)) {
            throw reject("'" + pattern + "' is a spreadsheet format name, "
                    + "not a pattern; use null for the default format",
                    pattern);
        }

        Flat flat = flatten(pattern);
        boolean hasPlaceholder = false;
        boolean hasLetter = false;
        for (int i = 0; i < flat.text.length(); i++) {
            if (flat.literal[i]) continue;
            char c = flat.text.charAt(i);
            // Checked before classification so "YYYY-MM-01" can't hide
            // behind a digit placeholder.
            if (ALWAYS_INVALID_LETTERS.indexOf(c) >= 0) {
                throw reject("Unsupported letter '" + c + "' at index "
                        + flat.src[i] + " (" + invalidLetterHint(c, pattern)
                        + "); quote it as '" + c
                        + "' to print it literally", pattern);
            }
            if (c == '0' || c == '#') hasPlaceholder = true;
            else if (isAsciiLetter(c)) hasLetter = true;
        }

        DataStylePattern result;
        if (hasPlaceholder) {
            result = new DataStylePattern(
                    Kind.NUMBER, null, parseNumber(flat, pattern));
        } else if (hasLetter) {
            result = new DataStylePattern(
                    Kind.DATE_TIME, parseDateTime(flat, pattern), null);
        } else {
            throw reject("Unrecognized data style pattern (expected "
                    + "date/time letters like y,M,d,H,h,m,s,S,a or numeric "
                    + "placeholders like 0,#)", pattern);
        }
        // Safety net (P1): whatever we accepted must be valid Java.
        javaGate(result.kind, pattern);
        return result;
    }

    // Why Y/D/A are rejected, e.g. 'Y' -> "Java 'Y' is week-based year; ...".
    private static String invalidLetterHint(char c, String pattern) {
        switch (c) {
            case 'Y': return "Java 'Y' is week-based year; for the calendar "
                    + "year use yyyy or yy";
            case 'D': return "Java 'D' is day of year; for day of month use "
                    + "d or dd";
            default: return pattern.contains("AM/PM") || pattern.contains("A/P")
                    ? "for AM/PM use a" : "Java 'A' is milli-of-day";
        }
    }

    // Extra hint for spreadsheet letters, e.g. 'N' -> "; for a weekday name ...".
    private static String unsupportedLetterHint(Flat flat, int i) {
        char c = flat.text.charAt(i);
        if (c == 'N') return "; for a weekday name use EEE or EEEE";
        if (c == 'W' && runEnd(flat, i) - i == 2) {
            return "; week of year is not supported";
        }
        return "";
    }

    // P1: an accepted pattern must also be a valid JDK pattern.
    private static void javaGate(Kind kind, String pattern) {
        String api = kind == Kind.DATE_TIME
                ? "DateTimeFormatter" : "DecimalFormat";
        try {
            if (kind == Kind.DATE_TIME) {
                DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
            } else {
                new DecimalFormat(pattern,
                        DecimalFormatSymbols.getInstance(Locale.ROOT));
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a valid " + api
                    + " pattern: " + e.getMessage() + ": " + pattern, e);
        }
    }

    // P4: every message ends with the original pattern.
    private static IllegalArgumentException reject(
            String problem, String pattern) {
        return new IllegalArgumentException(problem + ": " + pattern);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    // The pattern with quoting resolved. literal[i] marks characters that
    // were quoted; src[i] is the index of text[i] in the original pattern.
    private static final class Flat {
        final String text;
        final boolean[] literal;
        final int[] src;

        Flat(String text, boolean[] literal, int[] src) {
            this.text = text;
            this.literal = literal;
            this.src = src;
        }
    }

    private static Flat flatten(String pattern) {
        int n = pattern.length();
        StringBuilder text = new StringBuilder();
        boolean[] literal = new boolean[n];
        int[] src = new int[n];
        boolean inQuote = false;
        int quoteStart = -1;
        int i = 0;
        while (i < n) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                int run = 0;
                while (i + run < n && pattern.charAt(i + run) == '\'') run++;
                // DateTimeFormatter and DecimalFormat disagree on this (P1).
                if (run >= 4 && !inQuote) {
                    throw reject("Run of " + run + " apostrophes at index "
                            + i + " is ambiguous; use 'it''s' style text "
                            + "or fewer apostrophes", pattern);
                }
                if (i + 1 < n && pattern.charAt(i + 1) == '\'') {
                    // Java: '' is always one literal quote.
                    src[text.length()] = i;
                    literal[text.length()] = true;
                    text.append('\'');
                    i += 2;
                } else {
                    inQuote = !inQuote;
                    quoteStart = i;
                    i++;
                }
            } else {
                src[text.length()] = i;
                literal[text.length()] = inQuote;
                text.append(c);
                i++;
            }
        }
        if (inQuote) {
            throw reject("Unterminated ' (quote opened at index "
                    + quoteStart + ")", pattern);
        }
        return new Flat(text.toString(), literal, src);
    }

    private static DateTimeFieldType letterFieldType(char c) {
        // Case-sensitive, matching DateTimeFormatter's own letters.
        switch (c) {
            case 'y': return DateTimeFieldType.YEAR;
            case 'M': return DateTimeFieldType.MONTH;
            case 'd': return DateTimeFieldType.DAY;
            case 'E': return DateTimeFieldType.DAY_OF_WEEK;
            case 'Q': return DateTimeFieldType.QUARTER;
            case 'H': return DateTimeFieldType.HOUR24;
            case 'h': return DateTimeFieldType.HOUR12;
            case 'm': return DateTimeFieldType.MINUTE;
            case 's': return DateTimeFieldType.SECOND;
            case 'a': return DateTimeFieldType.AMPM;
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

    private static int runEnd(Flat flat, int start) {
        int j = start;
        while (j < flat.text.length() && !flat.literal[j]
                && flat.text.charAt(j) == flat.text.charAt(start)) {
            j++;
        }
        return j;
    }

    private static void checkCount(
            Flat flat, int start, int end, String pattern) {
        char c = flat.text.charAt(start);
        if (!validCount(c, end - start)) {
            throw reject("Letter run '" + flat.text.substring(start, end)
                    + "' at index " + flat.src[start] + " is not allowed; "
                    + countHint(c, end - start), pattern);
        }
    }

    private static List<DateTimeField> parseDateTime(
            Flat flat, String pattern) {
        List<DateTimeField> fields = new ArrayList<>();
        String text = flat.text;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (flat.literal[i] || ALLOWED_BARE_LITERALS.indexOf(c) >= 0) {
                fields.add(DateTimeField.ofText(String.valueOf(c)));
                i++;
                continue;
            }
            if (!isAsciiLetter(c)) {
                throw reject("Unquoted '" + c + "' at index " + flat.src[i]
                        + " is not allowed in a date/time pattern"
                        + (c == '[' ? " (spreadsheet elapsed time like [h] "
                        + "and calendar modifiers like [~buddhist] are not "
                        + "supported)" : "")
                        + "; quote it as '" + c + "'", pattern);
            }
            if (c == 'S') {
                throw reject("Fraction letter 'S' at index " + flat.src[i]
                        + " must directly follow a seconds run and '.', "
                        + "e.g. ss.SSS", pattern);
            }
            DateTimeFieldType type = letterFieldType(c);
            if (type == null) {
                throw reject("Unsupported letter '" + c + "' at index "
                        + flat.src[i] + " (not representable in ODF 1.2"
                        + unsupportedLetterHint(flat, i)
                        + "); quote it as '" + c + "' to print it literally",
                        pattern);
            }
            int j = runEnd(flat, i);
            checkCount(flat, i, j, pattern);
            if (type == DateTimeFieldType.SECOND && j + 1 < n
                    && !flat.literal[j] && text.charAt(j) == '.'
                    && !flat.literal[j + 1] && text.charAt(j + 1) == 'S') {
                // ODF 1.2: number:seconds carries decimal-places (P2).
                int end = runEnd(flat, j + 1);
                checkCount(flat, j + 1, end, pattern);
                fields.add(DateTimeField.ofSecond(j - i, end - j - 1));
                i = end;
                continue;
            }
            fields.add(DateTimeField.of(type, j - i));
            i = j;
        }

        mergeAdjacentText(fields);
        validateEachFieldOnce(fields, pattern);
        validateHourAmPmConsistency(fields, pattern);
        validateMinuteHasNeighbour(fields, pattern);
        return fields;
    }

    private static void validateEachFieldOnce(
            List<DateTimeField> fields, String pattern) {
        // ODF 1.2 16.27.10/16.27.18: one instance of each element (P2).
        Set<DateTimeFieldType> seen = EnumSet.noneOf(DateTimeFieldType.class);
        for (DateTimeField field : fields) {
            if (field.type != DateTimeFieldType.TEXT
                    && !seen.add(field.type)) {
                throw reject("Date/time field " + field.type
                        + " appears more than once; ODF allows each field "
                        + "at most once", pattern);
            }
        }
    }

    private static DateTimeFieldType neighbour(
            List<DateTimeField> fields, int from, int step) {
        for (int k = from + step; k >= 0 && k < fields.size(); k += step) {
            if (fields.get(k).type != DateTimeFieldType.TEXT) {
                return fields.get(k).type;
            }
        }
        return null;
    }

    private static void validateMinuteHasNeighbour(
            List<DateTimeField> fields, String pattern) {
        // LibreOffice re-reads a lone number:minutes as month (P2).
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).type != DateTimeFieldType.MINUTE) continue;
            DateTimeFieldType before = neighbour(fields, i, -1);
            DateTimeFieldType after = neighbour(fields, i, 1);
            boolean ok = before == DateTimeFieldType.HOUR24
                    || before == DateTimeFieldType.HOUR12
                    || after == DateTimeFieldType.SECOND;
            if (!ok) {
                throw reject("Minutes 'm' must follow an hour field or "
                        + "precede a seconds field, otherwise ODF readers "
                        + "treat them as a month; for a month use M or MM",
                        pattern);
            }
        }
    }

    private static void validateHourAmPmConsistency(
            List<DateTimeField> fields, String originalPattern) {
        // ODF 1.2 16.27.22: an am-pm element makes hours 1-12, so h and a must pair (P2).
        boolean hasHour12 = false;
        boolean hasHour24 = false;
        boolean hasAmPm = false;
        for (DateTimeField field : fields) {
            if (field.type == DateTimeFieldType.HOUR12) hasHour12 = true;
            if (field.type == DateTimeFieldType.HOUR24) hasHour24 = true;
            if (field.type == DateTimeFieldType.AMPM) hasAmPm = true;
        }
        if (hasHour12 && hasHour24) {
            throw reject("Data style pattern mixes both 'H' (24-hour) and "
                    + "'h' (12-hour) tokens", originalPattern);
        }
        if (hasHour12 && !hasAmPm) {
            throw reject("Data style pattern uses 'h' (12-hour clock) "
                    + "without a paired 'a' AM/PM marker; ODF cannot "
                    + "distinguish that from a 24-hour clock. Add 'a', or "
                    + "use 'H' for a 24-hour clock (spreadsheet hh is "
                    + "24-hour; in Java that is HH)", originalPattern);
        }
        if (hasHour24 && hasAmPm) {
            throw reject("Data style pattern uses 'H' (24-hour clock) "
                    + "together with an 'a' AM/PM marker; ODF always "
                    + "renders a 12-hour clock whenever an AM/PM marker is "
                    + "present, so this is contradictory. Use 'h' for a "
                    + "12-hour clock instead", originalPattern);
        }
    }

    private static void mergeAdjacentText(List<DateTimeField> fields) {
        for (int i = 0; i < fields.size() - 1; ) {
            DateTimeField current = fields.get(i);
            DateTimeField next = fields.get(i + 1);
            if (current.type == DateTimeFieldType.TEXT
                    && next.type == DateTimeFieldType.TEXT) {
                fields.set(i, DateTimeField.ofText(current.text + next.text));
                fields.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    private enum Phase { PREFIX, INTEGER, FRACTION, SUFFIX }

    // One forward scan: PREFIX, INTEGER, optional FRACTION, SUFFIX.
    private static NumberSpec parseNumber(Flat flat, String pattern) {
        String text = flat.text;
        Phase phase = Phase.PREFIX;
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();
        int zeros = 0;
        int fractionDigits = 0;
        int sinceComma = 0;
        boolean grouping = false;
        boolean dot = false;
        boolean percent = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int at = flat.src[i];
            if (!flat.literal[i]) {
                if (c == '0' || c == '#') {
                    if (phase == Phase.PREFIX) phase = Phase.INTEGER;
                    if (phase == Phase.INTEGER) {
                        if (c == '#' && zeros > 0) {
                            throw reject("'#' after '0' at index " + at
                                    + "; put all '#' before the '0's", pattern);
                        }
                        if (c == '0') zeros++;
                        sinceComma++;
                    } else if (phase == Phase.FRACTION) {
                        // ODF 1.2: number:decimal-places is a fixed count (P2).
                        if (c == '#') {
                            throw reject("'#' after '.' at index " + at
                                    + "; ODF decimal places are fixed, use "
                                    + "0.00", pattern);
                        }
                        fractionDigits++;
                    } else {
                        throw reject("Digit placeholder '" + c + "' at index "
                                + at + " after the number; quote it as '"
                                + c + "'", pattern);
                    }
                    continue;
                }
                if (c == ',') {
                    if (phase != Phase.INTEGER || sinceComma == 0) {
                        throw reject("',' at index " + at + " must sit "
                                + "between integer digit placeholders; "
                                + "quote it as ','", pattern);
                    }
                    grouping = true;
                    sinceComma = 0;
                    continue;
                }
                if (c == '.') {
                    if (phase == Phase.PREFIX || phase == Phase.INTEGER) {
                        phase = Phase.FRACTION;
                        dot = true;
                        continue;
                    }
                    throw reject("'.' at index " + at + " is a second or "
                            + "misplaced decimal point; quote it as '.'",
                            pattern);
                }
                if (c == '%') {
                    if (percent) {
                        throw reject("Second '%' at index " + at
                                + "; only one percent sign is allowed, "
                                + "quote it as '%'", pattern);
                    }
                    percent = true;
                } else if (c == ';') {
                    throw reject("';' at index " + at + ": negative "
                            + "subpatterns are not supported", pattern);
                } else if (c == '-') {
                    // DecimalFormat maps a bare '-' to the locale minus.
                    throw reject("Unquoted '-' at index " + at
                            + "; quote it as '-'", pattern);
                } else if (c == 'E') {
                    // ODF 1.2 cannot match Java's exponent rendering (P2).
                    throw reject("Unquoted 'E' at index " + at
                            + " (scientific notation is not supported); "
                            + "quote it, e.g. '0.00 'EUR''", pattern);
                } else if (c == '?') {
                    // '?' is a fraction digit in spreadsheet formats; Java prints it literally (P4).
                    throw reject("Unquoted '?' at index " + at
                            + ": fraction formats are not supported; "
                            + "quote it as '?' to print it", pattern);
                } else if (c == '[' || c == ']') {
                    // Spreadsheet format-code syntax that Java would print literally (P4).
                    throw reject("Unquoted '" + c + "' at index " + at
                            + ": spreadsheet colours and conditions like "
                            + "[Red] or [<100] are not supported; quote it "
                            + "as '" + c + "'", pattern);
                } else if (c == '"') {
                    throw reject("Unquoted '\"' at index " + at
                            + ": Java quotes literal text with ', not \"; "
                            + "write e.g. 0.00' kg'", pattern);
                } else if (c == '\\') {
                    throw reject("Unquoted '\\' at index " + at
                            + ": Java has no backslash escape; quote the "
                            + "text instead, e.g. 0.00' kg'", pattern);
                } else if (c == '_') {
                    throw reject("Unquoted '_' at index " + at
                            + ": spreadsheet spacing (_x) is not supported; "
                            + "quote it as '_'", pattern);
                } else if (c == '*') {
                    throw reject("Unquoted '*' at index " + at
                            + ": spreadsheet fill (*x) is not supported; "
                            + "quote it as '*'", pattern);
                } else if (c == '‰' || c == '¤') {
                    throw reject("Unquoted '" + c + "' at index " + at
                            + " is not supported; quote it as '" + c + "'",
                            pattern);
                }
            }
            if (phase == Phase.PREFIX) {
                prefix.append(c);
            } else {
                phase = Phase.SUFFIX;
                suffix.append(c);
            }
        }
        // ODF 1.2: number:grouping has no group size; 3 matches Java (P2).
        if (grouping && sinceComma != 3) {
            throw reject("Grouping needs exactly 3 digit placeholders after "
                    + "the last ',' (e.g. #,##0)", pattern);
        }
        if (dot && fractionDigits == 0) {
            throw reject("'.' must be followed by at least one '0'", pattern);
        }
        return new NumberSpec(prefix.toString(), suffix.toString(), zeros,
                fractionDigits, grouping, percent);
    }
}
