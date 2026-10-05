// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import static com.github.miachm.sods.PatternText.reject;

/**
 * A number pattern in {@code java.text.DecimalFormat} syntax, restricted to
 * a strict subset: every accepted pattern is a valid Java pattern, renders
 * the same text as Java (with {@code Locale.US}), and can be stored exactly
 * as an ODF 1.2 {@code number:number-style} or
 * {@code number:percentage-style}. Anything else throws
 * {@link IllegalArgumentException} naming the offending character, its index
 * and a fix. The caller chooses the kind (number or date/time), so the string
 * is never guessed.
 *
 * <p>As in {@code DecimalFormat}, unquoted letters are literal text
 * ({@code "0.00 m"}, {@code "0 days"}), except {@code E} (exponent syntax).
 * Text in {@code '...'} is literal and {@code ''} is a literal single quote.
 *
 * <table border="1">
 * <caption>Examples for 1234.5 and 0.5</caption>
 * <tr><th>Symbol</th><th>Meaning</th><th>Example</th></tr>
 * <tr><td>{@code 0}</td><td>mandatory digit</td><td>{@code 000.0} &rarr; {@code 1234.5}, {@code 000.5}</td></tr>
 * <tr><td>{@code #}</td><td>optional integer digit, before any {@code 0}</td><td>{@code #.00} &rarr; {@code 1234.50}, {@code .50}</td></tr>
 * <tr><td>{@code ,}</td><td>grouping; exactly 3 digits after the last comma</td><td>{@code #,##0.00} &rarr; {@code 1,234.50}</td></tr>
 * <tr><td>{@code .}</td><td>decimal point; only {@code 0} after it</td><td>{@code 0.00} &rarr; {@code 1234.50}</td></tr>
 * <tr><td>{@code %}</td><td>percentage (value &times; 100); once, in the prefix or suffix</td><td>{@code 0.0%} &rarr; {@code 50.0%}</td></tr>
 * <tr><td>other text</td><td>literal prefix or suffix</td><td>{@code $#,##0.00} &rarr; {@code $1,234.50}; {@code 0.00 m} &rarr; {@code 1234.50 m}</td></tr>
 * </table>
 *
 * <p>Rejected, because ODF 1.2 or spreadsheet formats differ from Java:
 * optional fraction digits ({@code 0.0#}), {@code E}, {@code ;}, {@code ‰},
 * {@code ¤}, a bare {@code -}, grouping other than 3, and the spreadsheet
 * syntax {@code ? [ ] " \ _ *}, {@code General} and {@code Standard}.
 * Quote a rejected character to print it.
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
        if (pattern == null) {
            throw new IllegalArgumentException(
                    "Number pattern cannot be null");
        }
        PatternText.rejectSpreadsheetName(pattern);
        PatternText.Flat flat = PatternText.flatten(pattern);
        checkHasPlaceholder(flat, pattern);
        NumberPattern result = scan(flat, pattern);
        // Safety net (P1): whatever we accepted must be valid Java.
        try {
            new DecimalFormat(pattern,
                    DecimalFormatSymbols.getInstance(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Not a valid DecimalFormat pattern: " + e.getMessage()
                    + ": " + pattern, e);
        }
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
            throw reject("No digit placeholder ('0' or '#'); this looks "
                    + "like a date/time pattern, use DataFormat.dateTime",
                    pattern);
        }
        throw reject("No digit placeholder: a number pattern needs "
                + "'0' or '#'", pattern);
    }

    // One forward scan: PREFIX, INTEGER, optional FRACTION, SUFFIX.
    private static NumberPattern scan(PatternText.Flat flat, String pattern) {
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
                } else {
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
        // ODF 1.2: number:grouping has no group size; 3 matches Java (P2).
        if (grouping && sinceComma != 3) {
            throw reject("Grouping needs exactly 3 digit placeholders after "
                    + "the last ',' (e.g. #,##0)", pattern);
        }
        if (dot && fractionDigits == 0) {
            throw reject("'.' must be followed by at least one '0'", pattern);
        }
        if (zeros == 0 && fractionDigits == 0) {
            throw reject("Integer part has no '0' and there is no fraction; "
                    + "spreadsheets show nothing for zero while Java shows "
                    + "0. Use 0 or #,##0", pattern);
        }
        return new NumberPattern(prefix.toString(), suffix.toString(), zeros,
                fractionDigits, grouping, percent);
    }

    // Unquoted characters that DecimalFormat treats specially or that mean
    // something else in spreadsheet formats. Other letters are plain text.
    private static void checkLiteralChar(char c, int at, String pattern) {
        switch (c) {
            case ';':
                throw reject("';' at index " + at + ": negative "
                        + "subpatterns are not supported", pattern);
            case '-':
                // DecimalFormat maps a bare '-' to the locale minus.
                throw reject("Unquoted '-' at index " + at
                        + "; quote it as '-'", pattern);
            case 'E':
                // ODF 1.2 cannot match Java's exponent rendering (P2).
                throw reject("Unquoted 'E' at index " + at
                        + " (scientific notation is not supported); "
                        + "quote it, e.g. 0.00 'EUR'", pattern);
            case '?':
                throw reject("Unquoted '?' at index " + at
                        + ": fraction formats are not supported; "
                        + "quote it as '?' to print it", pattern);
            case '[': case ']':
                throw reject(PatternText.bracketProblem(c, at, false),
                        pattern);
            case '"':
                throw reject("Unquoted '\"' at index " + at
                        + ": Java quotes literal text with ', not \"; "
                        + "write e.g. 0.00' kg'", pattern);
            case '\\':
                throw reject("Unquoted '\\' at index " + at
                        + ": Java has no backslash escape; quote the "
                        + "text instead, e.g. 0.00' kg'", pattern);
            case '_':
                throw reject("Unquoted '_' at index " + at
                        + ": spreadsheet spacing (_x) is not supported; "
                        + "quote it as '_'", pattern);
            case '*':
                throw reject("Unquoted '*' at index " + at
                        + ": spreadsheet fill (*x) is not supported; "
                        + "quote it as '*'", pattern);
            case '‰': case '¤':
                throw reject("Unquoted '" + c + "' at index " + at
                        + " is not supported; quote it as '" + c + "'",
                        pattern);
            default:
                break; // letters and the rest are literal, as in Java
        }
    }
}
