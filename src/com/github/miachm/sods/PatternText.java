// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

/**
 * Helpers shared by {@link DateTimePattern} and {@link NumberPattern}.
 *
 * <p>Both parsers follow the same principles: an accepted pattern is valid
 * Java (P1), can be recorded precisely in ODF 1.2 (P2), reads the same to a
 * Java developer (P3), and a rejected one fails fast, naming the character,
 * its index and a fix (P4).
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
     * one literal apostrophe, text inside quotes is literal.
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

    /** P4: every message ends with the original pattern. */
    static IllegalArgumentException reject(String problem, String pattern) {
        return new IllegalArgumentException(problem + ": " + pattern);
    }

    static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Rejects the spreadsheet format names {@code General} and {@code Standard}. */
    static void rejectSpreadsheetName(String pattern) {
        if ("General".equals(pattern) || "Standard".equals(pattern)) {
            throw reject("'" + pattern + "' is a spreadsheet format name, "
                    + "not a pattern; use null for the default format",
                    pattern);
        }
    }

    /** Message for an unquoted {@code [} or {@code ]} (spreadsheet syntax). */
    static String bracketProblem(char c, int index, boolean dateTime) {
        return "Unquoted '" + c + "' at index " + index
                + (dateTime
                ? " is not allowed in a date/time pattern (spreadsheet "
                + "elapsed time like [h] and calendar modifiers like "
                + "[~buddhist] are not supported)"
                : ": spreadsheet colours and conditions like [Red] or "
                + "[<100] are not supported")
                + "; quote it as '" + c + "'";
    }
}
