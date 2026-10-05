// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.AssertJUnit.*;

public class DateTimePatternTest {

    static String describe(DateTimePattern p) {
        StringBuilder sb = new StringBuilder();
        for (DateTimePattern.Field f : p.fields) {
            if (sb.length() > 0) sb.append(' ');
            if (f.type == DateTimePattern.FieldType.TEXT) {
                sb.append('\'').append(f.text).append('\'');
            } else {
                sb.append(f.type).append('/').append(f.length);
                if (f.decimalPlaces > 0) sb.append('.').append(f.decimalPlaces);
            }
        }
        return sb.toString();
    }

    private static final String ISO = "YEAR/4 '-' MONTH/2 '-' DAY/2";

    // {pattern, description}
    @DataProvider(name = "accepted")
    public static Object[][] accepted() {
        return new Object[][] {
            {"yyyy-MM-dd", ISO},
            {"dd/MM/yy", "DAY/2 '/' MONTH/2 '/' YEAR/2"},
            {"d MMM yyyy", "DAY/1 ' ' MONTH/3 ' ' YEAR/4"},
            {"EEEE", "DAY_OF_WEEK/4"},
            {"MMMM d, yyyy", "MONTH/4 ' ' DAY/1 ', ' YEAR/4"},
            {"yyyy-MM-dd'T'HH:mm:ss.SSS",
                ISO + " 'T' HOUR24/2 ':' MINUTE/2 ':' SECOND/2.3"},
            {"HH:mm", "HOUR24/2 ':' MINUTE/2"},
            {"h:mm a", "HOUR12/1 ':' MINUTE/2 ' ' AMPM/1"},
            {"hh:mm:ss a", "HOUR12/2 ':' MINUTE/2 ':' SECOND/2 ' ' AMPM/1"},
            {"mm:ss", "MINUTE/2 ':' SECOND/2"},
            {"ss.S", "SECOND/2.1"},
            {"QQQ yyyy", "QUARTER/3 ' ' YEAR/4"},
            {"QQQQ", "QUARTER/4"},
            {"HH 'Uhr'", "HOUR24/2 ' Uhr'"},
            {"'it''s' HH", "'it's ' HOUR24/2"},
            {"HH''mm", "HOUR24/2 ''' MINUTE/2"},
            {"HH:mm:'00'", "HOUR24/2 ':' MINUTE/2 ':00'"},
            {"HH'h00'", "HOUR24/2 'h00'"},
            // Digits 0-9 are bare literals, as in Java.
            {"HH:mm:00", "HOUR24/2 ':' MINUTE/2 ':00'"},
            {"yyyy-MM-01", "YEAR/4 '-' MONTH/2 '-01'"},
            {"MM/01/yyyy", "MONTH/2 '/01/' YEAR/4"},
            {"yyyy 0", "YEAR/4 ' 0'"},
            {"HH:mm:59", "HOUR24/2 ':' MINUTE/2 ':59'"},
            // Four apostrophes inside quotes read the same in Java and ODF.
            {"'a''''b' HH", "'a''b ' HOUR24/2"},
            {"HH '😀'", "HOUR24/2 ' 😀'"},
            {"ss.SSSSSSSSS", "SECOND/2.9"},
            {"HH'H'", "HOUR24/2 'H'"},
            {"ss.'S'", "SECOND/2 '.S'"},
            {"'''x' HH", "''x ' HOUR24/2"},
            // Bare literals: ( ) and non-ASCII text; quoting changes nothing.
            {"yyyy年MM月dd日", "YEAR/4 '年' MONTH/2 '月' DAY/2 '日'"},
            {"yyyy'年'MM'月'dd'日'", "YEAR/4 '年' MONTH/2 '月' DAY/2 '日'"},
            {"dd/MM/yyyy (EEEE)", "DAY/2 '/' MONTH/2 '/' YEAR/4 ' (' DAY_OF_WEEK/4 ')'"},
            {"HH 😀", "HOUR24/2 ' 😀'"},
            {"mm:ss.0", "MINUTE/2 ':' SECOND/2 '.0'"},
            {"HH\u0080\u00B5", "HOUR24/2 '\u0080\u00B5'"},
        };
    }

    @Test(dataProvider = "accepted")
    public void acceptsPattern(String pattern, String expected) {
        assertEquals(expected, describe(DateTimePattern.parse(pattern)));
    }

    // {pattern, message fragment}
    @DataProvider(name = "rejected")
    public static Object[][] rejected() {
        return new Object[][] {
            {"", "Empty pattern; use letters like y"},
            {"'Total'", "No date/time letters"},
            {"'0'", "No date/time letters; use letters"},
            {"yyyy-MM-dd'oops", "Unterminated quote opened at index 10"},
            {"YYYY-MM-dd", "Unsupported letter 'Y' at index 0 (Java 'Y' is week-based year"},
            {"yyyy-MM-DD", "Unsupported letter 'D' at index 8 (Java 'D' is day of year"},
            {"yyyy A", "Unsupported letter 'A' at index 5"},
            {"h:mm AM/PM", "for AM/PM use a"},
            {"yyy", "Letter run 'yyy' at index 0 is not allowed; use yy or yyyy"},
            {"yyyyy", "use yy or yyyy"},
            {"ddd", "use EEE"},
            {"HHH", "use H or HH"},
            {"aa", "use a"},
            {"MMMMM", "use M, MM, MMM or MMMM"},
            {"EEEEE", "use E, EE, EEE or EEEE"},
            {"QQ", "spreadsheet QQ"},
            {"QQQQQ", "use QQQ or QQQQ"},
            {"q yyyy", "Unsupported letter 'q' at index 0 (use QQQ or QQQQ"},
            {"w yyyy", "Unsupported letter 'w' at index 0 (week of year"},
            {"W", "Unsupported letter 'W' at index 0 (Java 'W' is week of month; spreadsheet WW (week of year) is not supported either"},
            {"WW", "Java 'W' is week of month; spreadsheet WW (week of year) is not supported either"},
            {"G yyyy", "Unsupported letter 'G' at index 0 (era is"},
            {"e", "Unsupported letter 'e' at index 0 (not representable"},
            {"uuuu-MM-dd", "Unsupported letter 'u' at index 0 (Java 'u' is the proleptic year"},
            {"LLL yyyy", "Unsupported letter 'L' at index 0 (Java 'L' is the standalone month"},
            {"HH:mm z", "'z' at index 6 (time zones cannot be stored in ODF 1.2); remove it"},
            {"HH:mm Z", "'Z' at index 6 (time zones"},
            {"HH:mm X", "'X' at index 6 (time zones"},
            {"HH:mm x", "'x' at index 6 (time zones"},
            {"HH:mm O", "'O' at index 6 (time zones"},
            {"HH:mm V", "'V' at index 6 (time zones"},
            {"k", "Unsupported letter 'k' at index 0 (Java 'k' is hour 1-24 and 'K' hour 0-11; use H, or h with a"},
            {"K", "use H, or h with a"},
            {"N", "Java 'N' is nano-of-day; for a weekday name use EEE"},
            {"yyyy-MM-ddTHH:mm", "Unquoted 'T' at index 10; quote it as 'T'"},
            {"yyyy@MM", "Unquoted '@' at index 4; quote it as '@'"},
            // Next to a quote, "quote it as" would write '' (a literal apostrophe).
            {"HH'h'%mm", "Unquoted '%' at index 5; this looks like a number pattern, use DataFormat.number or put it inside the adjacent quotes"},
            {"HH%'h'mm", "Unquoted '%' at index 2; this looks like a number pattern, use DataFormat.number or put it inside the adjacent quotes"},
            {"HH'h'kmm", "Unsupported letter 'k' at index 5 (Java 'k' is hour 1-24 and 'K' hour 0-11; use H, or h with a); put it inside the adjacent quotes to print it literally"},
            {"HH%''mm", "Unquoted '%' at index 2; this looks like a number pattern, use DataFormat.number or quote it as '%'"},
            {"HH''%mm", "Unquoted '%' at index 4; this looks like a number pattern, use DataFormat.number or quote it as '%'"},
            {"HH\u007Fmm", "Unquoted '\\u007F' at index 2; quote it as '\\u007F'"},
            {"hh:mm", "'h' at index 0 is the 12-hour clock and needs 'a' (hh:mm a)"},
            {"HH:mm a", "'H' (24-hour) with 'a' at index 6; use 'h'"},
            {"hh:HH a", "Mixing 'H' and 'h' at index 3; use one of them"},
            {"HH:hh a", "Mixing 'H' and 'h' at index 3"},
            {"a HH:mm", "'H' (24-hour) with 'a' at index 0"},
            {"HH:mm.SSS", "Fraction letter 'S' at index 6"},
            {"ss'.'SSS", "Fraction letter 'S' at index 5"},
            {"ss.SSSSSSSSSS", "use S to SSSSSSSSS"},
            {"hhh a", "Letter run 'hhh' at index 0 is not allowed; use h or hh"},
            {"sss", "Letter run 'sss' at index 0 is not allowed; use s or ss"},
            {"HH:mm HH", "Duplicate field 'HH' at index 6; ODF allows each field once, remove one"},
            {"HH:mm mm", "Duplicate field 'mm' at index 6; ODF allows each field once, remove one"},
            // The first mm is a spreadsheet month: the hint is MM, not "remove one".
            {"dd/mm/yyyy hh:mm", "Duplicate field 'mm' at index 14; ODF allows each field once (spreadsheet mm next to a date is a month"},
            {"MMM MM", "Duplicate field 'MM' at index 4; ODF allows each field once, remove one"},
            {"mm", "Minute 'm' at index 0 is read as a month by LibreOffice"},
            // The neighbour search skips text, so the day is before the minute.
            {"HH dd mm", "Minute 'm' at index 6"},
            {"mmm d, yyyy", "use MMM"},
            {"''''HH", "Ambiguous run of 4 apostrophes at index 0"},
            {"[h]:mm", "Unquoted '[' at index 0; spreadsheet code like [h] (elapsed time)"},
            {"yyyy]", "Unquoted ']' at index 4; spreadsheet code like [h]"},
            {"yyyy{", "Unquoted '{' at index 4; '{' and '}' are reserved, remove it"},
            {"yyyy}", "Unquoted '}' at index 4; '{' and '}' are reserved, remove it"},
            {"General", "use null"},
            {"Standard", "use null"},
            {"@", "'@' is the text format; use DataFormat.TEXT"},
            // Characters XML 1.0 cannot store, quoted or not; tab, LF and CR do not survive normalisation either.
            {"HH'\u0001'", "Character \\u0001 at index 3"},
            {"HH\u0001mm", "Character \\u0001 at index 2"},
            {"HH'\uD800'", "Character \\uD800 at index 3"},
            {"HH￾mm", "Character \\uFFFE at index 2"},
            {"HH￿mm", "Character \\uFFFF at index 2"},
            {"HH\u001Fmm", "Character \\u001F at index 2"},
            {"HH\tmm", "Character \\u0009 at index 2"},
            {"HH\nmm", "Character \\u000A at index 2"},
            {"HH\rmm", "Character \\u000D at index 2"},
            // Kind mismatch: number syntax in a date/time pattern.
            {"#,##0.00", "Unquoted '#' at index 0; this looks like a number pattern, use DataFormat.number"},
            {"yyyy #", "use DataFormat.number"},
            {"0.0%", "Unquoted '%' at index 3; this looks like a number pattern"},
            {"0", "use DataFormat.number"},
        };
    }

    @Test(dataProvider = "rejected")
    public void rejectsPattern(String pattern, String fragment) {
        assertRejected(DateTimePattern::parse, pattern, fragment);
    }

    @Test(dataProvider = "rejected")
    public void indexIsInTheOriginalPattern(String pattern, String fragment) {
        assertIndexIsInTheOriginalPattern(DateTimePattern::parse, pattern, fragment);
    }

    /** Asserts that {@code parse} rejects the pattern with a message naming the fragment and the pattern. */
    static void assertRejected(Consumer<String> parse, String pattern, String fragment) {
        try {
            parse.accept(pattern);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment) && e.getMessage().contains(pattern));
            return;
        }
        fail("Expected rejection of [" + pattern + "]");
    }

    /** Quoting "'x'" in front shifts every reported index by 3: they point into the original pattern. */
    static void assertIndexIsInTheOriginalPattern(Consumer<String> parse, String pattern, String fragment) {
        Matcher m = Pattern.compile("at index (\\d+)").matcher(fragment);
        if (pattern.indexOf('\'') < 0 && m.find()) {
            assertRejected(parse, "'x'" + pattern, "at index " + (Integer.parseInt(m.group(1)) + 3));
        }
    }

    // Only space - : / . , ( ) digits and non-ASCII are bare; other ASCII punctuation needs quotes.
    @Test
    public void otherPunctuationMustBeQuoted() {
        for (char c = 0x21; c < 0x7F; c++) {
            if (Character.isLetterOrDigit(c) || c == '\'' || " -:/.,()".indexOf(c) >= 0) continue;
            assertRejected(DateTimePattern::parse, "HH" + c + "mm", "Unquoted '" + c + "' at index 2");
        }
    }
}
