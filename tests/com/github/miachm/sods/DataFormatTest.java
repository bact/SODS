// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

public class DataFormatTest {

    private static final DataFormat DMY = DataFormat.dateTime("dd/MM/yyyy");
    private static final DataFormat MONEY = DataFormat.number("#,##0.00");

    @DataProvider(name = "factories")
    public static Object[][] factories() {
        return new Object[][] {
            {DataFormat.TEXT, DataFormat.Kind.TEXT, "@", "DataFormat.TEXT"},
            {DataFormat.ISO_DATE, DataFormat.Kind.DATE_TIME, "yyyy-MM-dd", "DataFormat.dateTime(\"yyyy-MM-dd\")"},
            {DMY, DataFormat.Kind.DATE_TIME, "dd/MM/yyyy", "DataFormat.dateTime(\"dd/MM/yyyy\")"},
            {MONEY, DataFormat.Kind.NUMBER, "#,##0.00", "DataFormat.number(\"#,##0.00\")"},
        };
    }

    @Test(dataProvider = "factories")
    public void kindPatternAndToString(DataFormat f, DataFormat.Kind kind, String pattern, String text) {
        assertEquals(f.getKind(), kind);
        assertEquals(f.getPattern(), pattern);
        assertEquals(f.toString(), text);
    }

    @Test
    public void invalidAndNullArguments() {
        thrown(IllegalArgumentException.class, () -> DataFormat.dateTime("YYYY"));
        thrown(IllegalArgumentException.class, () -> DataFormat.number("0.0#"));
        thrown(NullPointerException.class, () -> DataFormat.dateTime(null));
        thrown(NullPointerException.class, () -> DataFormat.number(null));
    }

    @Test
    public void equalsAndHashCode() {
        assertEquals(DataFormat.dateTime("yyyy-MM-dd"), DataFormat.ISO_DATE);
        assertEquals(DataFormat.dateTime("yyyy-MM-dd").hashCode(), DataFormat.ISO_DATE.hashCode());
        assertEquals(DataFormat.number("0.00"), DataFormat.number("0.00"));
        assertEquals(DataFormat.number("0.00").hashCode(), DataFormat.number("0.00").hashCode());
        assertNotEquals(DMY, DataFormat.ISO_DATE);
        assertNotEquals(DataFormat.number("0.00"), DataFormat.number("0.000"));
        assertNotEquals(DataFormat.TEXT, DataFormat.ISO_DATE);
        assertNotEquals(DataFormat.TEXT, null);
        assertNotEquals(DataFormat.TEXT, "@");
    }

    @DataProvider(name = "legacy")
    public static Object[][] legacy() {
        return new Object[][] {
            {null, null}, {"@", DataFormat.TEXT}, {"YYYY-MM-DD", DataFormat.ISO_DATE},
        };
    }

    @Test(dataProvider = "legacy")
    public void legacyStringIsTheSameSlotAsTypedFormat(String legacy, DataFormat typed) {
        Style viaString = new Style();
        viaString.setDataStyle(legacy);
        Style viaFormat = new Style();
        viaFormat.setDataFormat(typed);

        assertEquals(viaString.getDataFormat(), typed);
        assertEquals(viaFormat.getDataStyle(), legacy);
        assertEquals(viaString, viaFormat);
        assertEquals(viaString.hashCode(), viaFormat.hashCode());
    }

    @Test
    public void styleSlotReplacesAndOtherFormatsHaveNoLegacyString() {
        Style style = new Style();
        style.setDataFormat(DataFormat.TEXT);
        style.setDataFormat(MONEY);
        assertEquals(style.getDataFormat(), MONEY);
        assertNull(style.getDataStyle());

        style.setDataStyle("YYYY-MM-DD");
        assertEquals(style.getDataFormat(), DataFormat.ISO_DATE);
        style.setDataFormat(DMY);
        assertNull(style.getDataStyle());
        style.setDataStyle(null);
        assertNull(style.getDataFormat());
    }

    @Test
    public void legacySetterStillRejectsOtherStrings() {
        Style style = new Style();
        style.setDataFormat(MONEY);
        IllegalArgumentException e = thrown(IllegalArgumentException.class, () -> style.setDataStyle("bad"));
        assertEquals(e.getMessage(), "At the moment, the only supported date styles are null, '@', "
                + "and 'YYYY-MM-DD', but not 'bad'");
        assertEquals(style.getDataFormat(), MONEY);
    }

    @Test
    public void styleEqualityAndCloneSeeTheFormat() throws CloneNotSupportedException {
        Style a = new Style();
        Style b = new Style();
        a.setDataFormat(DMY);
        b.setDataFormat(DataFormat.dateTime("MM/dd/yyyy"));
        assertNotEquals(a, b);
        assertNotEquals(a, new Style());
        b.setDataFormat(DataFormat.dateTime("dd/MM/yyyy"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(((Style) a.clone()).getDataFormat(), DMY);
    }

    @Test
    public void cellKeepsTypedFormatAroundSetValue() {
        Range cell = new Sheet("A", 1, 1).getRange(0, 0);
        Style style = new Style();
        style.setDataFormat(DMY);
        cell.setStyle(style);

        cell.setValue(LocalDate.of(2026, 10, 5));
        assertEquals(cell.getStyle().getDataFormat(), DMY);
        cell.setValue("text");
        assertEquals(cell.getStyle().getDataFormat(), DMY);

        // Without a typed format, ISO_DATE is still auto-applied and auto-cleared
        Range plain = new Sheet("B", 1, 1).getRange(0, 0);
        plain.setValue(LocalDate.of(2026, 10, 5));
        assertEquals(plain.getStyle().getDataFormat(), DataFormat.ISO_DATE);
        plain.setValue(1);
        assertNull(plain.getStyle().getDataFormat());
    }

    @DataProvider(name = "writerPairs")
    public static Object[][] writerPairs() {
        return new Object[][] {{"YYYY-MM-DD", DataFormat.ISO_DATE}, {"@", DataFormat.TEXT}};
    }

    @Test(dataProvider = "writerPairs")
    public void typedFormatWritesSameContentAsLegacyString(String legacy, DataFormat typed) throws IOException {
        assertEquals(contentXml(styleOnly(typed)), contentXml(styleOnly(legacy)));
    }

    @Test
    public void unwrittenFormatAddsNoDataStyleName() throws IOException {
        String content = contentXml(styleOnly(MONEY));
        assertFalse(content.contains("data-style-name"), content);
    }

    private static <T extends Throwable> T thrown(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError("Unexpected " + t, t);
        }
        fail("Expected " + type.getSimpleName());
        return null;
    }

    private static Style styleOnly(Object format) {
        Style style = new Style();
        if (format instanceof DataFormat) style.setDataFormat((DataFormat) format);
        else style.setDataStyle((String) format);
        return style;
    }

    private static String contentXml(Style style) throws IOException {
        Sheet sheet = new Sheet("A", 1, 1);
        Range cell = sheet.getRange(0, 0);
        cell.setValue("x");
        cell.setStyle(style);
        SpreadSheet book = new SpreadSheet();
        book.appendSheet(sheet);
        ByteArrayOutputStream ods = new ByteArrayOutputStream();
        book.save(ods);
        return readEntry(ods.toByteArray(), "content.xml");
    }

    private static String readEntry(byte[] ods, String name) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ods))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.getName().equals(name)) {
                    return new Scanner(zip, "UTF-8").useDelimiter("\\A").next();
                }
            }
        }
        throw new IOException(name + " not found in the saved file");
    }
}
