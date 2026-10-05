// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import static com.github.miachm.sods.PatternText.reject;

/**
 * A number pattern in {@code java.text.DecimalFormat} syntax, restricted to a
 * strict subset: every accepted pattern is valid Java, renders the same text
 * as Java (with {@code Locale.US}), and can be stored exactly as an ODF 1.2
 * {@code number:number-style} or {@code number:percentage-style}. Anything
 * else throws {@link IllegalArgumentException}.
 *
 * <p>The rules and examples are in {@link DataFormat}; this class only
 * tokenizes and validates.
 */
final class NumberPattern {

    final String prefix;           // e.g. "$#,##0.00" -> "$"
    final String suffix;           // e.g. "0.00 kg" -> " kg"
    final int minIntegerDigits;    // count of '0's in the integer part
    final int decimalPlaces;       // count of '0's after '.'
    final boolean grouping;        // ',' present
    final boolean percentage;      // '%' present (kept in prefix/suffix)

    private NumberPattern(
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

    private enum Phase { PREFIX, INTEGER, FRACTION, SUFFIX }

    // Date/time letters supported by DateTimePattern, for the mismatch hint.
    private static final String DATE_TIME_LETTERS = "yMdEQHhmsSa";

    static NumberPattern parse(String pattern) {
        PatternText.rejectSpreadsheetName(pattern);
        PatternText.Flat flat = PatternText.flatten(pattern);
        checkHasPlaceholder(flat, pattern);
        NumberPattern result = scan(flat, pattern);
        PatternText.requireJava("DecimalFormat", pattern,
                () -> new DecimalFormat(pattern,
                        DecimalFormatSymbols.getInstance(Locale.ROOT)));
        return result;
    }

    private static void checkHasPlaceholder(
            PatternText.Flat flat, String pattern) {
        boolean dateTimeLetter = false;
        for (int i = 0; i < flat.text.length(); i++) {
            if (flat.literal[i]) continue;
            char c = flat.text.charAt(i);
            if (c == '0' || c == '#') return;
            if (DATE_TIME_LETTERS.indexOf(c) >= 0) dateTimeLetter = true;
        }
        if (dateTimeLetter) {
            throw reject("No digit placeholder; this looks like a "
                    + "date/time pattern, use DataFormat.dateTime", pattern);
        }
        throw reject((pattern.isEmpty() ? "Empty pattern" : "No digit placeholder")
                + "; use '0' or '#'", pattern);
    }

    // Java's DecimalFormat.format(double) keeps at most this many digits.
    private static final int MAX_INTEGER_ZEROS = 309;
    private static final int MAX_FRACTION_ZEROS = 340;

    // One forward scan: PREFIX, INTEGER, optional FRACTION, SUFFIX.
    private static NumberPattern scan(PatternText.Flat flat, String pattern) {
        String text = flat.text;
        Phase phase = Phase.PREFIX;
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();
        int zeros = 0;
        int fractionDigits = 0;
        int sinceComma = 0;
        int firstHash = -1;
        int lastComma = -1;
        int dotAt = -1;
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
                        if (c == '#' && firstHash < 0) firstHash = at;
                        if (c == '0' && ++zeros > MAX_INTEGER_ZEROS) {
                            throw tooMany("integer", at, MAX_INTEGER_ZEROS,
                                    pattern);
                        }
                        sinceComma++;
                    } else if (phase == Phase.FRACTION) {
                        // ODF 1.2: number:decimal-places is a fixed count.
                        if (c == '#') {
                            throw reject("'#' after '.' at index " + at
                                    + "; ODF decimal places are fixed, use "
                                    + "0.00", pattern);
                        }
                        if (++fractionDigits > MAX_FRACTION_ZEROS) {
                            throw tooMany("fraction", at, MAX_FRACTION_ZEROS,
                                    pattern);
                        }
                    } else {
                        throw digitAfterText(c, at, pattern);
                    }
                    continue;
                }
                if (c == ',') {
                    if (phase != Phase.INTEGER || sinceComma == 0) {
                        throw PatternText.rejectAt("Misplaced ',' at index " + at
                                + "; grouping must sit between integer digit "
                                + "placeholders, or quote it as ','", at,
                                pattern);
                    }
                    lastComma = at;
                    sinceComma = 0;
                    continue;
                }
                if (c == '.') {
                    boolean strayInPrefix = phase == Phase.PREFIX
                            && prefix.length() > 0
                            && !digitPlaceholderAt(flat, i + 1);
                    if (!strayInPrefix && (phase == Phase.PREFIX
                            || phase == Phase.INTEGER)) {
                        phase = Phase.FRACTION;
                        dotAt = at;
                        continue;
                    }
                    if (phase == Phase.FRACTION || strayInPrefix
                            || placeholderAfter(flat, i) < 0) {
                        throw PatternText.rejectAt((phase == Phase.FRACTION
                                ? "Second '.'" : "Unquoted '.'") + " at index "
                                + at + (phase == Phase.FRACTION ? ""
                                        : " after literal text")
                                + "; quote it as '.'", at, pattern);
                    }
                    throw reject("'.' at index " + at + " after literal "
                            + "text; put text after the number, e.g. "
                            + "0.00' kr.'", pattern);
                }
                if (c == '%') {
                    if (percent) {
                        throw PatternText.rejectAt("Second '%' at index " + at
                                + "; only one is allowed, quote it as '%'",
                                at, pattern);
                    }
                    percent = true;
                } else {
                    if (c == '-' && phase != Phase.PREFIX) {
                        // 000-00-0000: the later digits are the real problem.
                        int later = placeholderAfter(flat, i);
                        if (later >= 0) {
                            throw digitAfterText(text.charAt(later),
                                    flat.src[later], pattern);
                        }
                    }
                    checkLiteralChar(c, at, pattern);
                }
            }
            if (phase == Phase.PREFIX) {
                prefix.append(c);
            } else {
                phase = Phase.SUFFIX;
                suffix.append(c);
            }
        }
        // ODF 1.2: number:grouping has no group size; 3 matches Java.
        if (lastComma >= 0 && sinceComma != 3) {
            throw reject("Grouping ',' at index " + lastComma + " needs "
                    + "exactly 3 digit placeholders after it; use #,##0",
                    pattern);
        }
        if (dotAt >= 0 && fractionDigits == 0) {
            throw reject("Decimal point '.' at index " + dotAt + " has no "
                    + "'0' after it; write e.g. 0.00", pattern);
        }
        if (zeros == 0 && fractionDigits == 0) {
            throw reject("Only '#' (first at index " + firstHash + "); "
                    + "spreadsheets show nothing for zero, Java shows 0; use "
                    + "0 or #,##0", pattern);
        }
        return new NumberPattern(prefix.toString(), suffix.toString(), zeros,
                fractionDigits, lastComma >= 0, percent);
    }

    private static boolean digitPlaceholderAt(PatternText.Flat flat, int k) {
        return k < flat.text.length() && !flat.literal[k]
                && (flat.text.charAt(k) == '0' || flat.text.charAt(k) == '#');
    }

    private static int placeholderAfter(PatternText.Flat flat, int from) {
        for (int k = from; k < flat.text.length(); k++) {
            char c = flat.text.charAt(k);
            if (!flat.literal[k] && (c == '0' || c == '#')) return k;
        }
        return -1;
    }

    private static IllegalArgumentException digitAfterText(
            char c, int at, String pattern) {
        return reject("Digit placeholder '" + c + "' at index " + at
                + " after literal text; only one run of digits is supported "
                + "(store codes like 000-00-0000 as text with "
                + "DataFormat.TEXT)", pattern);
    }

    private static IllegalArgumentException tooMany(
            String part, int at, int limit, String pattern) {
        return reject("More than " + limit + " '0' in the " + part + " part "
                + "at index " + at + "; Java keeps at most " + limit
                + " digits there", pattern);
    }

    // Unquoted characters that DecimalFormat treats specially or that mean
    // something else in spreadsheet formats. Other letters are plain text.
    private static void checkLiteralChar(char c, int at, String pattern) {
        String tail = unquotedTail(c);
        if (tail != null) {
            throw PatternText.unquoted(at, tail, pattern);
        }
    }

    // Message tail for a character that must be quoted; null if it is literal.
    private static String unquotedTail(char c) {
        switch (c) {
            case ';':
                return "; a separate negative section is not supported, "
                        + "remove ';' and what follows (the minus sign is "
                        + "automatic)";
            case 'E':
                // ODF 1.2 cannot match Java's exponent rendering.
                return " (scientific notation is not supported); quote it as "
                        + "'E' to print it";
            case '?':
                return PatternText.spreadsheetTail("? (fractions)", c);
            case '[': case ']':
                return PatternText.spreadsheetTail(
                        "like [Red] or [<100] (colours, conditions)", c);
            case '"':
                return " (Java quotes literal text with ', not \"); write "
                        + "e.g. 0.00' kg'";
            case '\\':
                return PatternText.spreadsheetTail("\\ (escape; Java has "
                        + "none)", c);
            case '_':
                return PatternText.spreadsheetTail("_x (spacing)", c);
            case '*':
                return PatternText.spreadsheetTail("*x (fill)", c);
            case '-': // DecimalFormat maps a bare '-' to the locale minus
                return "; the minus sign is automatic, remove it (quote it "
                        + "as '-' only to print it)";
            case '\u2030':
                return "; per mille is not supported, use 0.0% "
                        + "(or quote it as '\u2030' to print it)";
            case '\u00A4':
                return "; write the currency symbol itself, e.g. $#,##0.00 or "
                        + "\u20AC0.00";
            default:
                return null; // letters and the rest are literal, as in Java
        }
    }
}
