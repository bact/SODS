// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Output and parsing must not depend on the JVM default locale or charset.
 *
 * These tests never change the default locale. Run the suite under a hostile
 * default to exercise them, e.g.
 * {@code mvn test "-DargLine=-Duser.language=tr -Duser.country=TR"}.
 * Under tr-TR, {@code "I".toLowerCase()} is the dotless {@code "ı"}.
 */
public class LocaleIndependenceTest {

    private static final String MIMETYPE =
            "application/vnd.oasis.opendocument.spreadsheet";

    @Test
    public void verticalAlignIsWrittenAsLowercaseOdfToken() throws IOException {
        byte[] ods = save(sheetWithVerticalAlign(Style.VERTICAL_TEXT_ALIGMENT.Middle));
        String xml = readEntry(ods, "content.xml") + readEntry(ods, "styles.xml");

        assertTrue(xml.contains("style:vertical-align=\"middle\""), xml);
    }

    @Test
    public void verticalAlignIsParsedCaseInsensitively() throws IOException {
        byte[] ods = save(sheetWithVerticalAlign(Style.VERTICAL_TEXT_ALIGMENT.Middle));
        // "MIDDLE" contains an uppercase I, which a tr-TR default would
        // lowercase to a dotless i and fail to match "middle".
        byte[] upper = replaceInEntry(ods, "content.xml",
                "style:vertical-align=\"middle\"",
                "style:vertical-align=\"MIDDLE\"");

        SpreadSheet loaded = new SpreadSheet(new ByteArrayInputStream(upper));
        Style style = loaded.getSheet(0).getRange(0, 0).getStyle();

        assertEquals(style.getVerticalTextAligment(), Style.VERTICAL_TEXT_ALIGMENT.Middle);
    }

    @Test
    public void encryptionAlgorithmNamesMatchRegardlessOfCase() {
        assertTrue(metadata("BLOWFISH CFB", "PBKDF2").isBlowfishCfb());
        assertTrue(metadata("AES256-CBC", "PBKDF2").isAesCbc());
        assertTrue(metadata("AES256-GCM", "PBKDF2").isAesGcm());
        assertTrue(metadata("AES256-GCM", "URN:ARGON2ID").isArgon2id());
        assertTrue(metadata("AES256-CBC", "HTTP://EXAMPLE/PBKDF2").isPbkdf2());
    }

    @Test
    public void mimetypeEntryIsAsciiAndFirst() throws IOException {
        byte[] ods = save(sheetWithVerticalAlign(Style.VERTICAL_TEXT_ALIGMENT.Top));
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ods));
        ZipEntry first = zip.getNextEntry();

        assertEquals(first.getName(), "mimetype");
        assertEquals(readAll(zip), MIMETYPE.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    public void currencyAndPercentageValuesArePlainXsdNumbers() throws IOException {
        Sheet sheet = new Sheet("A", 1, 2);
        sheet.getRange(0, 0).setValue(
                new OfficeCurrency(Currency.getInstance("EUR"), 1234567.123456789));
        sheet.getRange(0, 1).setValue(new OfficePercentage(1234.5));
        SpreadSheet spread = new SpreadSheet();
        spread.appendSheet(sheet);
        byte[] ods = save(spread);
        String content = readEntry(ods, "content.xml");

        assertTrue(content.contains("office:value=\"1234567.123456789\""), content);
        assertTrue(content.contains("office:value=\"1234.5\""), content);
        assertFalse(content.contains("office:value=\"1,"), content);

        Sheet loaded = new SpreadSheet(new ByteArrayInputStream(ods)).getSheet(0);
        assertEquals(((OfficeCurrency) loaded.getRange(0, 0).getValue()).getValue(),
                1234567.123456789);
        assertEquals(((OfficePercentage) loaded.getRange(0, 1).getValue()).getValue(),
                1234.5);
    }

    @Test
    public void xsdDoubleFormatting() {
        assertEquals(OfficeValueType.toXsdDouble(30.0), "30");
        assertEquals(OfficeValueType.toXsdDouble(0.3), "0.3");
        assertEquals(OfficeValueType.toXsdDouble(-1234.5), "-1234.5");
        assertEquals(OfficeValueType.toXsdDouble(1e20), "100000000000000000000");
        assertEquals(OfficeValueType.toXsdDouble(0.0), "0");
    }

    private static OdfEncryptionMetadata metadata(String algorithm, String kdf) {
        OdfEncryptionMetadata.Builder builder = new OdfEncryptionMetadata.Builder();
        builder.algorithmName = algorithm;
        builder.keyDerivationName = kdf;
        return builder.build();
    }

    private static SpreadSheet sheetWithVerticalAlign(Style.VERTICAL_TEXT_ALIGMENT align) {
        Style style = new Style();
        style.setVerticalTextAligment(align);
        Sheet sheet = new Sheet("A", 1, 1);
        sheet.getRange(0, 0).setValue("x");
        sheet.getRange(0, 0).setStyle(style);
        SpreadSheet spread = new SpreadSheet();
        spread.appendSheet(sheet);
        return spread;
    }

    private static byte[] save(SpreadSheet spread) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        spread.save(out);
        return out.toByteArray();
    }

    private static String readEntry(byte[] ods, String name) throws IOException {
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ods));
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.getName().equals(name)) {
                return new String(readAll(zip), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Entry not found: " + name);
    }

    private static byte[] replaceInEntry(byte[] ods, String name, String from, String to)
            throws IOException {
        ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(ods));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        ZipOutputStream out = new ZipOutputStream(result);
        ZipEntry entry;
        boolean replaced = false;
        while ((entry = in.getNextEntry()) != null) {
            byte[] data = readAll(in);
            if (entry.getName().equals(name)) {
                String text = new String(data, StandardCharsets.UTF_8);
                replaced = text.contains(from);
                data = text.replace(from, to).getBytes(StandardCharsets.UTF_8);
            }
            ZipEntry copy = new ZipEntry(entry.getName());
            if (entry.getMethod() == ZipEntry.STORED) {
                CRC32 crc = new CRC32();
                crc.update(data);
                copy.setMethod(ZipEntry.STORED);
                copy.setSize(data.length);
                copy.setCompressedSize(data.length);
                copy.setCrc(crc.getValue());
            }
            out.putNextEntry(copy);
            out.write(data);
            out.closeEntry();
        }
        out.close();
        assertTrue(replaced, "'" + from + "' not found in " + name);
        return result.toByteArray();
    }

    private static byte[] readAll(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
