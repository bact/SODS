import com.github.miachm.sods.DataFormat;
import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import com.github.miachm.sods.Style;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

/* This sample sets data formats on cells.
   DataFormat.dateTime() takes a java.time.format.DateTimeFormatter pattern and
   DataFormat.number() a java.text.DecimalFormat pattern. Only a subset that
   ODF can store exactly is accepted; anything else is rejected with a fix.
   At the moment, only TEXT and ISO_DATE are written to the file.
 */
public class DataFormats {
    public static void main(String[] args) throws IOException {
        Sheet sheet = new Sheet("Formats", 2, 2);

        // A LocalDate gets DataFormat.ISO_DATE (yyyy-MM-dd) automatically
        sheet.getRange(0, 0).setValue(LocalDate.of(2026, 10, 5));

        // DataFormat.TEXT keeps "00123" as text
        Range zip = sheet.getRange(1, 0);
        Style text = new Style();
        text.setDataFormat(DataFormat.TEXT);
        zip.setStyle(text);
        zip.setValue("00123");

        System.out.println(DataFormat.dateTime("dd/MM/yyyy"));
        System.out.println(DataFormat.number("#,##0.00"));
        try {
            DataFormat.dateTime("hh:mm"); // 12-hour clock without 'a' is rejected
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

        SpreadSheet spread = new SpreadSheet();
        spread.appendSheet(sheet);
        spread.save(new File("DataFormats.ods"));
    }
}
