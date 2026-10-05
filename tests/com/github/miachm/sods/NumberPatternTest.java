// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

public class NumberPatternTest {

    static String describe(NumberPattern n) {
        return "int=" + n.minIntegerDigits + " dec=" + n.decimalPlaces
                + (n.grouping ? " grp" : "") + (n.percentage ? " pct" : "")
                + " pre='" + n.prefix + "' suf='" + n.suffix + "'";
    }

    private static String num(int dec, String pre, String suf) {
        return "int=1 dec=" + dec + " pre='" + pre + "' suf='" + suf + "'";
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }

    // {pattern, description}
    @DataProvider(name = "accepted")
    public static Object[][] accepted() {
        return new Object[][] {
            {"0", num(0, "", "")},
            {"0.00", num(2, "", "")},
            // ODF 1.2 number:decimal-places has no maximum; 15 digits is a reader limit.
            {"0.00000000000000000000", num(20, "", "")},
            // Java's limits: 309 integer digits and 340 fraction digits.
            {repeat("0", 309), "int=309 dec=0 pre='' suf=''"},
            {"0." + repeat("0", 340), num(340, "", "")},
            {"#,##0.00", "int=1 dec=2 grp pre='' suf=''"},
            {"#,###,##0", "int=1 dec=0 grp pre='' suf=''"},
            {"#.0", "int=0 dec=1 pre='' suf=''"},
            {"#.00", "int=0 dec=2 pre='' suf=''"},
            {".00", "int=0 dec=2 pre='' suf=''"},
            {"000.0", "int=3 dec=1 pre='' suf=''"},
            {"$.00", "int=0 dec=2 pre='$' suf=''"},
            {"$#,##0.00", "int=1 dec=2 grp pre='$' suf=''"},
            {"0.00 kg", num(2, "", " kg")},
            {"0.00 'µs'", num(2, "", " µs")},
            {"0.00' m'", num(2, "", " m")},
            {"0 'days'", num(0, "", " days")},
            {"kr'.' 0", num(0, "kr. ", "")},
            {"'kr.' 0", num(0, "kr. ", "")},
            {"0 kr'.'", num(0, "", " kr.")},
            {"0.0%", "int=1 dec=1 pct pre='' suf='%'"},
            {"%0.0", "int=1 dec=1 pct pre='%' suf=''"},
            {"0.0 %", "int=1 dec=1 pct pre='' suf=' %'"},
            {"'Rate: '0.0%", "int=1 dec=1 pct pre='Rate: ' suf='%'"},
            {"'-'0.00", num(2, "-", "")},
            {"0.00 'EUR'", num(2, "", " EUR")},
            {"'yyyy'0", num(0, "yyyy", "")},
            {"0 '?'", num(0, "", " ?")},
            {"0.00 '?/4'", num(2, "", " ?/4")},
            {"'[Red]'0.00", num(2, "[Red]", "")},
            {"0.00'_'", num(2, "", "_")},
            {"'*'0", num(0, "*", "")},
            {"0😀", num(0, "", "😀")},
            // Letters are literal text, as in DecimalFormat.
            {"0.00 m", num(2, "", " m")},
            {"0 days", num(0, "", " days")},
            {"0.00 µs", num(2, "", " µs")},
            {"0.00 A", num(2, "", " A")},
            {"YYYY0", num(0, "YYYY", "")},
            {"0 'kg' a", num(0, "", " kg a")},
            {"x0.0y", num(1, "x", "y")},
            // Four apostrophes inside quotes read the same in Java and ODF.
            {"0 'a''''b'", num(0, "", " a''b")},
            // Other punctuation is literal, as in Java.
            {"0.00 (+/@)", num(2, "", " (+/@)")},
        };
    }

    @Test(dataProvider = "accepted")
    public void acceptsPattern(String pattern, String expected) {
        assertEquals(expected, describe(NumberPattern.parse(pattern)));
    }

    // {pattern, message fragment}
    @DataProvider(name = "rejected")
    public static Object[][] rejected() {
        return new Object[][] {
            {"", "Empty pattern; use '0' or '#'"},
            {"%", "No digit placeholder"},
            // Quoted digits are text, so no date/time hint.
            {"'0'", "No digit placeholder; use '0' or '#'"},
            {"#,###", "Only '#' (first at index 0); spreadsheets show nothing for zero, Java shows 0; use 0 or #,##0"},
            {"0;-0", "Unquoted ';' at index 1; a separate negative section is not supported"},
            {"0.0#", "'#' after '.' at index 3; ODF decimal places are fixed, use 0.00"},
            {"#,#0", "Grouping ',' at index 1 needs exactly 3 digit placeholders"},
            {"#,####0", "Grouping ',' at index 1 needs exactly 3 digit placeholders"},
            {"0 ,", "Misplaced ',' at index 2"},
            {"#,,##0", "Misplaced ',' at index 2"},
            {",0", "Misplaced ',' at index 0"},
            {"0#", "'#' after '0' at index 1"},
            {"0.0.0", "Second '.' at index 3; quote it as '.'"},
            {"kr. 0", "Unquoted '.' at index 2 after literal text; quote it as '.'"},
            {"No. 0", "Unquoted '.' at index 2 after literal text; quote it as '.'"},
            {"Fr. 0.00", "Unquoted '.' at index 2 after literal text; quote it as '.'"},
            {"0 kr.", "Unquoted '.' at index 4 after literal text; quote it as '.'"},
            {"0.00 m.", "Unquoted '.' at index 6 after literal text; quote it as '.'"},
            {"0%.", "Unquoted '.' at index 2 after literal text; quote it as '.'"},
            {"0 'x'.", "Unquoted '.' at index 5 after literal text; put it inside the adjacent quotes"},
            {"kr.'0'0", "Unquoted '.' at index 2 after literal text; put it inside the adjacent quotes"},
            {"kr.#0", "'#' after '.' at index 3"},
            {"0 kr.0", "'.' at index 4 after literal text; put text after the number, e.g. 0.00' kr.'"},
            {"0'x'.00", "'.' at index 4 after literal text; put text after the number"},
            {"000-00-0000", "Digit placeholder '0' at index 4 after literal text; only one run of digits is supported (store codes like 000-00-0000 as text with DataFormat.TEXT)"},
            {"0.00 x 10", "Digit placeholder '0' at index 8 after literal text; only one run of digits is supported"},
            {"0-#", "Digit placeholder '#' at index 2 after literal text"},
            {"0-'0'", "Unquoted '-' at index 1; the minus sign is automatic"},
            {"0.", "Decimal point '.' at index 1 has no '0' after it"},
            {"$0.", "Decimal point '.' at index 2 has no '0' after it"},
            {"0.00E00", "Unquoted 'E' at index 4"},
            {"# ?/?", "Unquoted '?' at index 2; spreadsheet code ? (fractions)"},
            {"0.0‰", "Unquoted '‰' at index 3; per mille is not supported, use 0.0% (or quote it as '‰' to print it)"},
            {"¤0.00", "Unquoted '¤' at index 0; write the currency symbol itself, e.g. $#,##0.00 or €0.00"},
            {"-0.00", "Unquoted '-' at index 0; the minus sign is automatic, remove it"},
            {"0.0%%", "Second '%' at index 4; only one is allowed, quote it as '%'"},
            // Next to a quote, "quote it as" would write '' (a literal apostrophe).
            {"0%'x'%", "Second '%' at index 5; only one is allowed, put it inside the adjacent quotes"},
            {"0 'x',", "Misplaced ',' at index 5; grouping must sit between integer digit placeholders, or put it inside the adjacent quotes"},
            {"'a'0.0.'x'", "Second '.' at index 6; put it inside the adjacent quotes"},
            {"0%%'x'", "Second '%' at index 2; only one is allowed, put it inside the adjacent quotes"},
            {"0.00 'kg", "Unterminated quote opened at index 5"},
            {"[Red]0.00", "Unquoted '[' at index 0; spreadsheet code like [Red] or [<100]"},
            {"0.00]", "Unquoted ']' at index 4; spreadsheet code like [Red]"},
            {"0.00\" kg\"", "Java quotes literal text"},
            {"0.00\\ kg", "Unquoted '\\' at index 4; spreadsheet code \\ (escape; Java has none)"},
            {"0.00_)", "Unquoted '_' at index 4; spreadsheet code _x (spacing)"},
            {"* #,##0", "Unquoted '*' at index 0; spreadsheet code *x (fill)"},
            {"General", "use null"},
            {"Standard", "use null"},
            {"@", "'@' is the text format; use DataFormat.TEXT"},
            // Java formats at most 309 integer and 340 fraction digits.
            {repeat("0", 310), "More than 309 '0' in the integer part at index 309"},
            {"0." + repeat("0", 341), "More than 340 '0' in the fraction part at index 342"},
            // Characters XML 1.0 cannot store, quoted or not; tab, LF and CR do not survive normalisation either.
            {"0'\u0001'", "Character \\u0001 at index 2"},
            {"0\u0001", "Character \\u0001 at index 1"},
            {"0\uD800", "Character \\uD800 at index 1"},
            {"0￾", "Character \\uFFFE at index 1"},
            {"0￿", "Character \\uFFFF at index 1"},
            {"0\t", "Character \\u0009 at index 1"},
            {"0\n", "Character \\u000A at index 1"},
            {"0\r", "Character \\u000D at index 1"},
            // Kind mismatch: date/time letters and no digit placeholder.
            {"yyyy-MM-dd", "use DataFormat.dateTime"},
            {"YYYY-MM-DD", "this looks like a date/time pattern"},
        };
    }

    @Test(dataProvider = "rejected")
    public void rejectsPattern(String pattern, String fragment) {
        DateTimePatternTest.assertRejected(NumberPattern::parse, pattern, fragment);
    }

    @Test(dataProvider = "rejected")
    public void indexIsInTheOriginalPattern(String pattern, String fragment) {
        DateTimePatternTest.assertIndexIsInTheOriginalPattern(NumberPattern::parse, pattern, fragment);
    }

    // Not in the table: the index tests put 'x' in front, and a '.' after
    // literal text is rejected differently from a leading one.
    @Test
    public void leadingDotWithoutPlaceholderIsNotLiteralText() {
        DateTimePatternTest.assertRejected(NumberPattern::parse, ". 0",
                "Digit placeholder '0' at index 2 after literal text");
    }

    // Every date/time letter alone gets the "use DataFormat.dateTime" hint.
    @Test
    public void everyDateTimeLetterGetsTheHint() {
        for (char c : "yMdEQHhmsSa".toCharArray()) {
            DateTimePatternTest.assertRejected(NumberPattern::parse, String.valueOf(c), "use DataFormat.dateTime");
        }
    }
}
