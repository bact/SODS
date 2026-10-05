// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.AssertJUnit.*;

public class DataStylePatternTest {

    // Compact one-line rendering of a parsed pattern, used by the tables.
    static String describe(DataStylePattern p) {
        if (p.kind == DataStylePattern.Kind.TEXT) return "text";
        if (p.kind == DataStylePattern.Kind.NUMBER) {
            DataStylePattern.NumberSpec n = p.numberSpec;
            return "num int=" + n.minIntegerDigits + " dec=" + n.decimalPlaces
                    + (n.grouping ? " grp" : "") + (n.percentage ? " pct" : "")
                    + " pre='" + n.prefix + "' suf='" + n.suffix + "'";
        }
        StringBuilder sb = new StringBuilder();
        for (DataStylePattern.DateTimeField f : p.dateTimeFields) {
            if (sb.length() > 0) sb.append(' ');
            if (f.type == DataStylePattern.DateTimeFieldType.TEXT) {
                sb.append('\'').append(f.text).append('\'');
            } else {
                sb.append(f.type).append('/').append(f.length);
                if (f.decimalPlaces > 0) sb.append('.').append(f.decimalPlaces);
            }
        }
        return sb.toString();
    }

    private static final String ISO = "YEAR/4 '-' MONTH/2 '-' DAY/2";
    private static final String NUM = "num int=1 dec=";

    // {pattern, kind, description}
    @DataProvider(name = "accepted")
    public static Object[][] accepted() {
        return new Object[][] {
            {"yyyy-MM-dd", "DATE_TIME", ISO},
            {"dd/MM/yy", "DATE_TIME", "DAY/2 '/' MONTH/2 '/' YEAR/2"},
            {"d MMM yyyy", "DATE_TIME", "DAY/1 ' ' MONTH/3 ' ' YEAR/4"},
            {"EEEE", "DATE_TIME", "DAY_OF_WEEK/4"},
            {"MMMM d, yyyy", "DATE_TIME", "MONTH/4 ' ' DAY/1 ', ' YEAR/4"},
            {"yyyy-MM-dd'T'HH:mm:ss.SSS", "DATE_TIME",
                ISO + " 'T' HOUR24/2 ':' MINUTE/2 ':' SECOND/2.3"},
            {"HH:mm", "DATE_TIME", "HOUR24/2 ':' MINUTE/2"},
            {"h:mm a", "DATE_TIME", "HOUR12/1 ':' MINUTE/2 ' ' AMPM/1"},
            {"hh:mm:ss a", "DATE_TIME",
                "HOUR12/2 ':' MINUTE/2 ':' SECOND/2 ' ' AMPM/1"},
            {"mm:ss", "DATE_TIME", "MINUTE/2 ':' SECOND/2"},
            {"ss.S", "DATE_TIME", "SECOND/2.1"},
            {"QQQ yyyy", "DATE_TIME", "QUARTER/3 ' ' YEAR/4"},
            {"QQQQ", "DATE_TIME", "QUARTER/4"},
            {"HH 'Uhr'", "DATE_TIME", "HOUR24/2 ' Uhr'"},
            {"'it''s' HH", "DATE_TIME", "'it's ' HOUR24/2"},
            {"HH''mm", "DATE_TIME", "HOUR24/2 ''' MINUTE/2"},
            {"@", "TEXT", "text"},
            {"YYYY-MM-DD", "DATE_TIME", ISO},
            {"0", "NUMBER", NUM + "0 pre='' suf=''"},
            {"0.00", "NUMBER", NUM + "2 pre='' suf=''"},
            // ODF 1.2 number:decimal-places has no maximum; 15 digits is a reader limit.
            {"0.00000000000000000000", "NUMBER", NUM + "20 pre='' suf=''"},
            {"#,##0.00", "NUMBER", NUM + "2 grp pre='' suf=''"},
            {"#,###,##0", "NUMBER", NUM + "0 grp pre='' suf=''"},
            {"#.00", "NUMBER", "num int=0 dec=2 pre='' suf=''"},
            {"000.0", "NUMBER", "num int=3 dec=1 pre='' suf=''"},
            {"$#,##0.00", "NUMBER", NUM + "2 grp pre='$' suf=''"},
            {"0.00 kg", "NUMBER", NUM + "2 pre='' suf=' kg'"},
            {"0.00 'µs'", "NUMBER", NUM + "2 pre='' suf=' µs'"},
            {"0.00' m'", "NUMBER", NUM + "2 pre='' suf=' m'"},
            {"0 'days'", "NUMBER", NUM + "0 pre='' suf=' days'"},
            {"HH:mm:'00'", "DATE_TIME", "HOUR24/2 ':' MINUTE/2 ':00'"},
            {"HH'h00'", "DATE_TIME", "HOUR24/2 'h00'"},
            {"0.00 W", "NUMBER", NUM + "2 pre='' suf=' W'"},
            {"0.0%", "NUMBER", NUM + "1 pct pre='' suf='%'"},
            {"%0.0", "NUMBER", NUM + "1 pct pre='%' suf=''"},
            {"0.0 %", "NUMBER", NUM + "1 pct pre='' suf=' %'"},
            {"'Rate: '0.0%", "NUMBER", NUM + "1 pct pre='Rate: ' suf='%'"},
            {"'-'0.00", "NUMBER", NUM + "2 pre='-' suf=''"},
            {"0.00 'EUR'", "NUMBER", NUM + "2 pre='' suf=' EUR'"},
            {"'yyyy'0", "NUMBER", NUM + "0 pre='yyyy' suf=''"},
            {"0 '?'", "NUMBER", NUM + "0 pre='' suf=' ?'"},
            {"0.00 '?/4'", "NUMBER", NUM + "2 pre='' suf=' ?/4'"},
            {"0.00' kg'", "NUMBER", NUM + "2 pre='' suf=' kg'"},
            {"'[Red]'0.00", "NUMBER", NUM + "2 pre='[Red]' suf=''"},
            {"0.00'_'", "NUMBER", NUM + "2 pre='' suf='_'"},
            {"'*'0", "NUMBER", NUM + "0 pre='*' suf=''"},
        };
    }

    @Test(dataProvider = "accepted")
    public void acceptsPattern(String pattern, String kind, String expected) {
        DataStylePattern p = DataStylePattern.parse(pattern);
        assertEquals(kind, p.kind.name());
        assertEquals(expected, describe(p));
    }

    // {pattern, message fragment}
    @DataProvider(name = "rejected")
    public static Object[][] rejected() {
        return new Object[][] {
            {"", "Unrecognized data style pattern"},
            {"   ", "Unrecognized data style pattern"},
            {"'Total'", "Unrecognized data style pattern"},
            {"''", "Unrecognized data style pattern"},
            {"yyyy-MM-dd'oops", "Unterminated"},
            {"YYYY-MM-dd", "Unsupported letter 'Y' at index 0"},
            {"yyyy-MM-DD", "Unsupported letter 'D' at index 8"},
            {"0.00 A", "Unsupported letter 'A' at index 5"},
            {"YYYY-MM-01", "Unsupported letter 'Y'"},
            {"y-M-d", "use yy or yyyy"},
            {"yyy", "use yy or yyyy"},
            {"yyyyy", "use yy or yyyy"},
            {"ddd", "use EEE"},
            {"HHH", "use H or HH"},
            {"aa", "use a"},
            {"MMMMM", "use M, MM, MMM or MMMM"},
            {"EEEEE", "use E, EE, EEE or EEEE"},
            {"Q yyyy", "spreadsheet Q"},
            {"QQ", "spreadsheet QQ"},
            {"QQQQQ", "use QQQ or QQQQ"},
            {"q yyyy", "Unsupported letter 'q'"},
            {"w yyyy", "Unsupported letter 'w'"},
            {"W", "Unsupported letter 'W'"},
            {"G yyyy", "Unsupported letter 'G'"},
            {"e", "Unsupported letter 'e'"},
            {"k", "Unsupported letter 'k'"},
            {"yyyy-MM-ddTHH:mm", "Unsupported letter 'T' at index 10"},
            {"yyyy@MM", "Unquoted '@' at index 4"},
            {"yyyy\tMM", "Unquoted '\t' at index 4"},
            {"yyyy😀MM", "at index 4"},
            {"hh:mm", "without a paired 'a'"},
            {"HH:mm a", "'H' (24-hour clock) together with"},
            {"hh:HH a", "mixes both"},
            {"HH:mm:SSS", "Fraction letter 'S' at index 6"},
            {"ss.SSSSSSSSSS", "use 1 to 9 S"},
            {"HH:mm HH", "HOUR24 appears more than once"},
            {"MMM MM", "MONTH appears more than once"},
            {"mm", "Minutes 'm' must follow"},
            {"dd mm", "Minutes 'm' must follow"},
            {"0;-0", "negative subpatterns"},
            {"#,##0.00;(#,##0.00)", "negative subpatterns"},
            {"0.0#", "use 0.00"},
            {"#.##", "use 0.00"},
            {"#,#0", "exactly 3 digit placeholders"},
            {"#,####0", "exactly 3 digit placeholders"},
            {"0#", "'#' after '0'"},
            {"0.0.0", "second or misplaced decimal point"},
            {"0.00 0", "Digit placeholder '0' at index 5"},
            {"0.00E00", "Unquoted 'E' at index 4"},
            {"0.00 Euro", "Unquoted 'E' at index 5"},
            {"HH:mm:00", "ambiguous"},
            {"HH'h'00", "ambiguous"},
            {"MM/01/yyyy", "ambiguous"},
            {"yyyy 0", "ambiguous"},
            {"0.00 m", "ambiguous"},
            {"0.00 s", "ambiguous"},
            {"0 h", "ambiguous"},
            {"0 days", "ambiguous"},
            {"0.00 ms", "ambiguous"},
            {"0.00 µs", "ambiguous"},
            {"0 'kg' a", "ambiguous"},
            {"# ?/?", "Unquoted '?' at index 2"},
            {"0 ?/4", "Unquoted '?' at index 2"},
            {"0.00?", "'?'"},
            {"yyyy?MM", "Unquoted '?' at index 4"},
            {"0.0‰", "Unquoted '‰' at index 3"},
            {"¤0.00", "Unquoted '¤' at index 0"},
            {"-0.00", "Unquoted '-' at index 0"},
            {"0.0%%", "Second '%' at index 4"},
            {"%0%", "Second '%' at index 2"},
            {"$", "Unrecognized data style pattern"},
            {"%", "Unrecognized data style pattern"},
            {"!!!", "Unrecognized data style pattern"},
            {"''''HH", "ambiguous"},
            {"[Red]0.00", "colours and conditions"},
            {"0.00[Red]", "'['"},
            {"0.00\" kg\"", "Java quotes literal text"},
            {"0.00\\ kg", "backslash"},
            {"0.00_)", "spacing"},
            {"* #,##0", "fill"},
            {"[h]:mm", "elapsed time"},
            {"mmm d, yyyy", "use MMM"},
            {"d-mmm-yy", "use MMM"},
            {"dddd", "use EEE"},
            {"hh:mm:ss", "spreadsheet hh is 24-hour"},
            {"yyyy-mm-dd", "use M or MM"},
            {"MM/DD/YYYY", "day of month"},
            {"YYYY-MM-dd", "week-based year"},
            {"h:mm AM/PM", "use a"},
            {"General", "use null"},
            {"NN", "use EEE"},
            {"NNNN, MMMM d, yyyy", "use EEE"},
            {"WW", "week of year"},
            {"Standard", "use null"},
            {"[~buddhist]yyyy", "calendar modifiers"},
        };
    }

    @Test(dataProvider = "rejected")
    public void rejectsPattern(String pattern, String fragment) {
        try {
            DataStylePattern.parse(pattern);
            fail("Expected rejection of [" + pattern + "]");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
            assertTrue(e.getMessage(), e.getMessage().contains(pattern));
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullIsRejected() {
        DataStylePattern.parse(null);
    }

    @Test
    public void isoDateFieldSequence() {
        List<DataStylePattern.DateTimeField> f =
                DataStylePattern.parse("yyyy-MM-dd").dateTimeFields;
        assertEquals(5, f.size());
        assertEquals(DataStylePattern.DateTimeFieldType.YEAR, f.get(0).type);
        assertEquals(4, f.get(0).length);
        assertEquals("-", f.get(1).text);
        assertEquals(DataStylePattern.DateTimeFieldType.MONTH, f.get(2).type);
        assertEquals(DataStylePattern.DateTimeFieldType.DAY, f.get(4).type);
        assertTrue(DataStylePattern.parse("yyyy-MM-dd").hasDateFields());
        assertFalse(DataStylePattern.parse("HH:mm").hasDateFields());
    }

    @Test
    public void fractionSecondsMergeIntoSecond() {
        List<DataStylePattern.DateTimeField> f =
                DataStylePattern.parse("ss.SSS").dateTimeFields;
        assertEquals(1, f.size());
        assertEquals(DataStylePattern.DateTimeFieldType.SECOND, f.get(0).type);
        assertEquals(2, f.get(0).length);
        assertEquals(3, f.get(0).decimalPlaces);
    }

    @Test
    public void currencyNumberSpec() {
        DataStylePattern.NumberSpec n =
                DataStylePattern.parse("$#,##0.00").numberSpec;
        assertEquals("$", n.prefix);
        assertEquals("", n.suffix);
        assertEquals(1, n.minIntegerDigits);
        assertEquals(2, n.decimalPlaces);
        assertTrue(n.grouping);
        assertFalse(n.percentage);
    }

    @Test
    public void legacyIsoDateEqualsCanonical() {
        assertEquals(describe(DataStylePattern.parse("yyyy-MM-dd")),
                describe(DataStylePattern.parse("YYYY-MM-DD")));
    }
}
