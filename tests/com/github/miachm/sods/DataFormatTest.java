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

import static org.testng.Assert.*;

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
            // toString is a valid Java literal: quote and backslash are escaped
            {DataFormat.dateTime("yyyy'\"\\'"), DataFormat.Kind.DATE_TIME, "yyyy'\"\\'",
                "DataFormat.dateTime(\"yyyy'\\\"\\\\'\")"},
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
        for (Runnable r : new Runnable[] {() -> DataFormat.dateTime(null), () -> DataFormat.number(null)}) {
            assertEquals(thrown(NullPointerException.class, r).getMessage(), "pattern can not be null");
        }
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
        assertFalse(DataFormat.TEXT.equals(null));
        assertFalse(DataFormat.TEXT.equals("@"));
        // "0 a" is valid in both kinds: only the kind tells them apart
        assertNotEquals(DataFormat.number("0 a"), DataFormat.dateTime("0 a"));
    }

    @DataProvider(name = "legacy")
    public static Object[][] legacy() {
        return new Object[][] {
            {null, null}, {"@", DataFormat.TEXT}, {"YYYY-MM-DD", DataFormat.ISO_DATE},
        };
    }

    @Test(dataProvider = "legacy")
    public void legacyStringIsTheSameSlotAsTypedFormat(String legacy, DataFormat typed) {
        Style viaString = withLegacy(legacy);
        Style viaFormat = withFormat(typed);
        assertEquals(viaString.getDataFormat(), typed);
        assertEquals(viaFormat.getDataStyle(), legacy);
        assertEquals(viaString, viaFormat);
        assertEquals(viaString.hashCode(), viaFormat.hashCode());
    }

    @Test
    public void styleSlotReplacesAndOtherFormatsHaveNoLegacyString() {
        Style style = withFormat(DataFormat.TEXT);
        style.setDataFormat(MONEY);
        assertEquals(style.getDataFormat(), MONEY);
        assertNull(style.getDataStyle());
        style.setDataFormat(DataFormat.ISO_DATE);
        style.setDataFormat(DMY);
        assertNull(style.getDataStyle());
    }

    @Test
    public void legacySetterRejectsOtherStringsAndNullClears() {
        Style style = withFormat(MONEY);
        IllegalArgumentException e = thrown(IllegalArgumentException.class, () -> style.setDataStyle("bad"));
        assertEquals(e.getMessage(), "At the moment, the only supported date styles are null, '@', "
                + "and 'YYYY-MM-DD', but not 'bad'");
        assertEquals(style.getDataFormat(), MONEY);
        style.setDataStyle(null);
        assertNull(style.getDataFormat());
    }

    @Test
    public void styleEqualityAndCloneSeeTheFormat() throws CloneNotSupportedException {
        Style a = withFormat(DMY);
        Style b = withFormat(DataFormat.dateTime("MM/dd/yyyy"));
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
        cell.setStyle(withFormat(DMY));
        cell.setValue("text");
        assertEquals(cell.getStyle().getDataFormat(), DMY);
        // A LocalDate does not replace a typed format
        cell.setValue(LocalDate.of(2026, 10, 5));
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
        return new Object[][] {
            {"YYYY-MM-DD", DataFormat.ISO_DATE, "datestyle"}, {"@", DataFormat.TEXT, "textstyle"},
        };
    }

    @Test(dataProvider = "writerPairs")
    public void typedFormatWritesSameContentAsLegacyString(String legacy, DataFormat typed, String styleName)
            throws IOException {
        String content = contentXml(withFormat(typed));
        assertEquals(content, contentXml(withLegacy(legacy)));
        assertTrue(content.contains("data-style-name=\"" + styleName + "\""), content);
    }

    // The JDK's StAX writer may print an empty element as <x/> or <x></x>.
    private static String emptyElementsAsSelfClosed(String xml) {
        return xml.replaceAll("(<number:[a-z-]+[^<>/]*)></number:[a-z-]+>", "$1/>");
    }

    @Test
    public void predefinedDataStylesAreWrittenInFull() throws IOException {
        String content = emptyElementsAsSelfClosed(contentXml(withFormat(DataFormat.ISO_DATE)));
        assertTrue(content.contains("<number:text-style style:name=\"textstyle\">"
                + "<number:text-content/></number:text-style>"), content);
        assertTrue(content.contains("<number:date-style style:name=\"datestyle\">"
                + "<number:year number:style=\"long\"/><number:text>-</number:text>"
                + "<number:month number:style=\"long\"/><number:text>-</number:text>"
                + "<number:day number:style=\"long\"/></number:date-style>"), content);
    }

    @Test
    public void cssExposesTheLegacyString() {
        assertEquals(withFormat(DataFormat.ISO_DATE).getCssStyles().get("data-style"), "YYYY-MM-DD");
        assertNull(withFormat(MONEY).getCssStyles().get("data-style"));
    }

    @Test
    public void unwrittenFormatAddsNoDataStyleName() throws IOException {
        assertFalse(contentXml(withFormat(MONEY)).contains("data-style-name"));
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

    private static Style withFormat(DataFormat format) {
        Style style = new Style();
        style.setDataFormat(format);
        return style;
    }

    private static Style withLegacy(String dataStyle) {
        Style style = new Style();
        style.setDataStyle(dataStyle);
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
