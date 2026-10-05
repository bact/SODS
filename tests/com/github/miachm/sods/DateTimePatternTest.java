// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.AssertJUnit.*;

public class DateTimePatternTest {

    // Compact one-line rendering of a parsed pattern, used by the tables.
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
            // Changed vs #108: digits 0-9 are bare literals (as in Java).
            {"HH:mm:00", "HOUR24/2 ':' MINUTE/2 ':00'"},
            {"yyyy-MM-01", "YEAR/4 '-' MONTH/2 '-01'"},
            {"MM/01/yyyy", "MONTH/2 '/01/' YEAR/4"},
            {"yyyy 0", "YEAR/4 ' 0'"},
            {"HH:mm:59", "HOUR24/2 ':' MINUTE/2 ':59'"},
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
            {"", "No date/time letters"},
            {"   ", "No date/time letters"},
            {"'Total'", "No date/time letters"},
            {"yyyy-MM-dd'oops", "Unterminated"},
            {"YYYY-MM-dd", "Unsupported letter 'Y' at index 0"},
            {"YYYY-MM-dd", "week-based year"},
            {"yyyy-MM-DD", "Unsupported letter 'D' at index 8"},
            {"yyyy-MM-DD", "day of year"},
            {"yyyy A", "Unsupported letter 'A' at index 5"},
            {"h:mm AM/PM", "for AM/PM use a"},
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
            {"WW", "week of year"},
            {"G yyyy", "Unsupported letter 'G'"},
            {"e", "Unsupported letter 'e'"},
            {"k", "Unsupported letter 'k'"},
            {"N", "use EEE"},
            {"NN", "use EEE"},
            {"NNNN, MMMM d, yyyy", "use EEE"},
            {"yyyy-MM-ddTHH:mm", "Unquoted 'T' at index 10; quote it as 'T'"},
            {"yyyy@MM", "Unquoted '@' at index 4"},
            {"yyyy\tMM", "Unquoted '\t' at index 4"},
            {"yyyy😀MM", "at index 4"},
            {"yyyy?MM", "Unquoted '?' at index 4"},
            {"hh:mm", "without a paired 'a'"},
            {"hh:mm:ss", "spreadsheet hh is 24-hour"},
            {"HH:mm a", "'H' (24-hour clock) together with"},
            {"hh:HH a", "mixes both"},
            {"HH:mm:SSS", "Fraction letter 'S' at index 6"},
            {"ss.SSSSSSSSSS", "use 1 to 9 S"},
            {"HH:mm HH", "HOUR24 appears more than once"},
            {"MMM MM", "MONTH appears more than once"},
            {"mm", "Minutes 'm' must follow"},
            {"dd mm", "Minutes 'm' must follow"},
            {"yyyy-mm-dd", "use M or MM"},
            {"mmm d, yyyy", "use MMM"},
            {"d-mmm-yy", "use MMM"},
            {"dddd", "use EEE"},
            {"MM/DD/YYYY", "Unsupported letter 'D' at index 3"},
            {"''''HH", "ambiguous"},
            {"[h]:mm", "elapsed time"},
            {"[~buddhist]yyyy", "calendar modifiers"},
            {"yyyy]", "Unquoted ']' at index 4"},
            {"General", "use null"},
            {"Standard", "use null"},
            // Kind mismatch: number syntax in a date/time pattern.
            {"#,##0.00", "Unquoted '#' at index 0 is not allowed"},
            {"#,##0.00", "use DataFormat.number"},
            {"yyyy #", "use DataFormat.number"},
            {"0.0%", "Unquoted '%' at index 3"},
            {"0.00", "use DataFormat.number"},
            {"0", "use DataFormat.number"},
            {"0.00 'kg'", "use DataFormat.number"},
            {"$", "Unquoted '$' at index 0"},
            {"!!!", "Unquoted '!' at index 0"},
        };
    }

    @Test(dataProvider = "rejected")
    public void rejectsPattern(String pattern, String fragment) {
        try {
            DateTimePattern.parse(pattern);
            fail("Expected rejection of [" + pattern + "]");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
            assertTrue(e.getMessage(), e.getMessage().contains(pattern));
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void nullIsRejected() {
        DateTimePattern.parse(null);
    }

    @Test
    public void isoDateFieldSequence() {
        List<DateTimePattern.Field> f =
                DateTimePattern.parse("yyyy-MM-dd").fields;
        assertEquals(5, f.size());
        assertEquals(DateTimePattern.FieldType.YEAR, f.get(0).type);
        assertEquals(4, f.get(0).length);
        assertEquals("-", f.get(1).text);
        assertEquals(DateTimePattern.FieldType.MONTH, f.get(2).type);
        assertEquals(DateTimePattern.FieldType.DAY, f.get(4).type);
        assertTrue(DateTimePattern.parse("yyyy-MM-dd").hasDateFields());
        assertFalse(DateTimePattern.parse("HH:mm").hasDateFields());
    }

    @Test
    public void fractionSecondsMergeIntoSecond() {
        List<DateTimePattern.Field> f = DateTimePattern.parse("ss.SSS").fields;
        assertEquals(1, f.size());
        assertEquals(DateTimePattern.FieldType.SECOND, f.get(0).type);
        assertEquals(2, f.get(0).length);
        assertEquals(3, f.get(0).decimalPlaces);
    }
}
