// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

public class NumberPatternTest {

    // Compact one-line rendering of a parsed pattern, used by the tables.
    static String describe(NumberPattern n) {
        return "int=" + n.minIntegerDigits + " dec=" + n.decimalPlaces
                + (n.grouping ? " grp" : "") + (n.percentage ? " pct" : "")
                + " pre='" + n.prefix + "' suf='" + n.suffix + "'";
    }

    private static String num(int dec, String pre, String suf) {
        return "int=1 dec=" + dec + " pre='" + pre + "' suf='" + suf + "'";
    }

    // {pattern, description}
    @DataProvider(name = "accepted")
    public static Object[][] accepted() {
        return new Object[][] {
            {"0", num(0, "", "")},
            {"0.00", num(2, "", "")},
            // ODF 1.2 number:decimal-places has no maximum; 15 digits is a reader limit.
            {"0.00000000000000000000", num(20, "", "")},
            {"#,##0.00", "int=1 dec=2 grp pre='' suf=''"},
            {"#,###,##0", "int=1 dec=0 grp pre='' suf=''"},
            {"#.0", "int=0 dec=1 pre='' suf=''"},
            {"#.00", "int=0 dec=2 pre='' suf=''"},
            {"000.0", "int=3 dec=1 pre='' suf=''"},
            {"$#,##0.00", "int=1 dec=2 grp pre='$' suf=''"},
            {"0.00 kg", num(2, "", " kg")},
            {"0.00 'µs'", num(2, "", " µs")},
            {"0.00' m'", num(2, "", " m")},
            {"0 'days'", num(0, "", " days")},
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
            {"0.00 W", num(2, "", " W")},
            // Changed vs #108: letters are literal text, as in DecimalFormat.
            {"0.00 m", num(2, "", " m")},
            {"0.00 s", num(2, "", " s")},
            {"0 h", num(0, "", " h")},
            {"0 days", num(0, "", " days")},
            {"0.00 ms", num(2, "", " ms")},
            {"0.00 µs", num(2, "", " µs")},
            {"0.00 A", num(2, "", " A")},
            {"YYYY0", num(0, "YYYY", "")},
            {"0 'kg' a", num(0, "", " kg a")},
            {"DD 0", num(0, "DD ", "")},
            {"x0.0y", num(1, "x", "y")},
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
            {"", "No digit placeholder"},
            {"   ", "No digit placeholder"},
            {"'Total'", "No digit placeholder"},
            {"''", "No digit placeholder"},
            {"$", "No digit placeholder"},
            {"%", "No digit placeholder"},
            {"!!!", "No digit placeholder"},
            {"@", "No digit placeholder"},
            {"#", "Integer part has no '0'"},
            {"#,###", "Integer part has no '0'"},
            {"#%", "Integer part has no '0'"},
            {"$#", "Use 0 or #,##0"},
            {"0;-0", "negative subpatterns"},
            {"#,##0.00;(#,##0.00)", "negative subpatterns"},
            {"0.0#", "use 0.00"},
            {"#.##", "use 0.00"},
            {"#,#0", "exactly 3 digit placeholders"},
            {"#,####0", "exactly 3 digit placeholders"},
            {"0#", "'#' after '0'"},
            {"0.0.0", "second or misplaced decimal point"},
            {"0.00 0", "Digit placeholder '0' at index 5"},
            {"0.", "'.' must be followed"},
            {",0", "',' at index 0"},
            {"0.00E00", "Unquoted 'E' at index 4"},
            {"0.00 Euro", "Unquoted 'E' at index 5"},
            {"# ?/?", "Unquoted '?' at index 2"},
            {"0 ?/4", "Unquoted '?' at index 2"},
            {"0.00?", "'?'"},
            {"0.0‰", "Unquoted '‰' at index 3"},
            {"¤0.00", "Unquoted '¤' at index 0"},
            {"-0.00", "Unquoted '-' at index 0"},
            {"0.0%%", "Second '%' at index 4"},
            {"%0%", "Second '%' at index 2"},
            {"''''0", "ambiguous"},
            {"0.00 'kg", "Unterminated"},
            {"[Red]0.00", "colours and conditions"},
            {"0.00[Red]", "'['"},
            {"0.00]", "']'"},
            {"0.00\" kg\"", "Java quotes literal text"},
            {"0.00\\ kg", "backslash"},
            {"0.00_)", "spacing"},
            {"* #,##0", "fill"},
            {"General", "use null"},
            {"Standard", "use null"},
            // Kind mismatch: date/time letters and no digit placeholder.
            {"yyyy-MM-dd", "use DataFormat.dateTime"},
            {"HH:mm", "this looks like a date/time pattern"},
            {"EEEE", "use DataFormat.dateTime"},
            {"hh:mm a", "use DataFormat.dateTime"},
            {"YYYY-MM-DD", "No digit placeholder"},
        };
    }

    @Test(dataProvider = "rejected")
    public void rejectsPattern(String pattern, String fragment) {
        try {
            NumberPattern.parse(pattern);
            fail("Expected rejection of [" + pattern + "]");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
            assertTrue(e.getMessage(), e.getMessage().contains(pattern));
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullIsRejected() {
        NumberPattern.parse(null);
    }

    @Test
    public void currencyFields() {
        NumberPattern n = NumberPattern.parse("$#,##0.00");
        assertEquals("$", n.prefix);
        assertEquals("", n.suffix);
        assertEquals(1, n.minIntegerDigits);
        assertEquals(2, n.decimalPlaces);
        assertTrue(n.grouping);
        assertFalse(n.percentage);
    }
}
