// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import java.util.Locale;

/**
 * Helpers shared by {@link DateTimePattern} and {@link NumberPattern}.
 * A rejection message states the problem, the index where there is one, and
 * a fix, then {@code ": "} and the pattern.
 */
final class PatternText {

    private PatternText() {
    }

    /**
     * The pattern with quoting resolved. {@code literal[i]} marks characters
     * that were quoted; {@code src[i]} is the index of {@code text[i]} in the
     * original pattern.
     */
    static final class Flat {
        final String text;
        final boolean[] literal;
        final int[] src;

        Flat(String text, boolean[] literal, int[] src) {
            this.text = text;
            this.literal = literal;
            this.src = src;
        }

        /** End (exclusive) of the run of one unquoted letter starting at start. */
        int runEnd(int start) {
            int j = start;
            while (j < text.length() && !literal[j]
                    && text.charAt(j) == text.charAt(start)) {
                j++;
            }
            return j;
        }
    }

    /**
     * Resolves {@code '...'} quoting the way Java does: {@code ''} is always
     * one literal apostrophe, text inside quotes is literal. Rejects
     * characters that cannot survive in an XML 1.0 text node, quoted or not.
     */
    static Flat flatten(String pattern) {
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
                // Outside quotes, DateTimeFormatter and DecimalFormat read
                // 4 or more apostrophes differently.
                if (run >= 4 && !inQuote) {
                    throw reject("Ambiguous run of " + run + " apostrophes at "
                            + "index " + i + "; write '' for each literal "
                            + "apostrophe", pattern);
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
                boolean pair = Character.isHighSurrogate(c) && i + 1 < n
                        && Character.isLowSurrogate(pattern.charAt(i + 1));
                if (!pair && isInvalidInXml(c)) {
                    throw reject(String.format(Locale.ROOT, "Character \\u%04X at index %d "
                            + "cannot be stored in an ODS file (XML 1.0); "
                            + "remove it", (int) c, i), pattern);
                }
                for (int k = 0; k < (pair ? 2 : 1); k++) {
                    src[text.length()] = i + k;
                    literal[text.length()] = inQuote;
                    text.append(pattern.charAt(i + k));
                }
                i += pair ? 2 : 1;
            }
        }
        if (inQuote) {
            throw reject("Unterminated quote opened at index " + quoteStart
                    + "; close it, or write '' for a quote", pattern);
        }
        return new Flat(text.toString(), literal, src);
    }

    // Control characters (tab, LF and CR too: XML normalises them unless
    // escaped), U+FFFE/U+FFFF and lone surrogates.
    private static boolean isInvalidInXml(char c) {
        return c < ' ' || Character.isSurrogate(c) || c == '\uFFFE' || c == '\uFFFF';
    }

    /** The message is {@code problem}, {@code ": "} and the pattern. */
    static IllegalArgumentException reject(String problem, String pattern) {
        return new IllegalArgumentException(problem + ": " + pattern);
    }

    /**
     * Rejects the unquoted character at {@code index} of the original
     * pattern; {@code tail} follows the index, e.g. {@code "; quote it as
     * 'x'"}.
     */
    static IllegalArgumentException unquoted(
            int index, String tail, String pattern) {
        return rejectAt("Unquoted '" + pattern.charAt(index) + "' at index "
                + index + tail, index, pattern);
    }

    /**
     * Like {@link #reject}, for a problem with the character at {@code
     * index}. Where the message says {@code quote it as 'c'} but a quote
     * touches the character, that would write {@code ''} (a literal
     * apostrophe), so it says to put it inside the adjacent quotes instead.
     * A control character is shown as {@code \}{@code uXXXX}.
     */
    static IllegalArgumentException rejectAt(
            String problem, int index, String pattern) {
        char c = pattern.charAt(index);
        // An odd run of apostrophes next to it ends with a quote; in an even
        // run they are all '' pairs.
        if (apostrophes(pattern, index, -1) % 2 == 1
                || apostrophes(pattern, index + 1, 1) % 2 == 1) {
            problem = problem.replace("quote it as '" + c + "'",
                    "put it inside the adjacent quotes");
        }
        if (Character.isISOControl(c)) {
            problem = problem.replace(String.valueOf(c),
                    String.format(Locale.ROOT, "\\u%04X", (int) c));
        }
        return reject(problem, pattern);
    }

    // Length of the run of apostrophes that starts at from (step 1) or ends
    // just before it (step -1).
    private static int apostrophes(String pattern, int from, int step) {
        int k = 0;
        for (int i = step > 0 ? from : from - 1;
                i >= 0 && i < pattern.length() && pattern.charAt(i) == '\'';
                i += step) {
            k++;
        }
        return k;
    }

    /**
     * Runs the JDK's own parser on an accepted pattern. A failure means our
     * rules missed something, so the message asks for a bug report.
     */
    static void requireJava(String javaType, String pattern, Runnable parse) {
        try {
            parse.run();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a valid " + javaType
                    + " pattern (" + e.getMessage() + "); please report this: "
                    + pattern, e);
        }
    }

    static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Rejects the spreadsheet format names {@code General} and {@code
     * Standard}, and {@code @} (the text format).
     */
    static void rejectSpreadsheetName(String pattern) {
        if ("General".equals(pattern) || "Standard".equals(pattern)) {
            throw reject("'" + pattern + "' is a spreadsheet format name; "
                    + "use null for the default format", pattern);
        }
        if ("@".equals(pattern)) {
            throw reject("'@' is the text format; use DataFormat.TEXT", pattern);
        }
    }

    /** Tail for an unquoted spreadsheet code {@code what}: remove it. */
    static String spreadsheetTail(String what, char c) {
        return "; spreadsheet code " + what + " is not supported; remove it "
                + "(quote it as '" + c + "' only to print it)";
    }
}
