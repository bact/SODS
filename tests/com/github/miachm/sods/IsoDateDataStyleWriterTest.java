// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Scanner;
import java.util.zip.ZipInputStream;

import static org.testng.AssertJUnit.assertTrue;

public class IsoDateDataStyleWriterTest {

    @Test
    public void isoDateStyleZeroPadsDay() throws IOException {
        Sheet sheet = new Sheet("A", 1, 1);
        sheet.getRange(0, 0).setValue(LocalDate.of(2026, 10, 5));
        SpreadSheet spreadSheet = new SpreadSheet();
        spreadSheet.appendSheet(sheet);
        ByteArrayOutputStream ods = new ByteArrayOutputStream();
        spreadSheet.save(ods);

        // A short day renders 2026-10-5; ISO 8601 needs 2026-10-05
        String content = readEntry(ods.toByteArray(), "content.xml");
        assertTrue(content, content.contains("<number:day number:style=\"long\""));
    }

    private static String readEntry(byte[] ods, String name) throws IOException {
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ods));
        while (!zip.getNextEntry().getName().equals(name)) {
            // skip
        }
        return new Scanner(zip, "UTF-8").useDelimiter("\\A").next();
    }
}
