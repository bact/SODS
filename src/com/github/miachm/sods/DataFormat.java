// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import java.util.Objects;

/**
 * An immutable data format: how a cell's content is interpreted and shown,
 * such as plain text, a date or a number. Use it with
 * {@link Style#setDataFormat(DataFormat)}:
 *
 * <pre>{@code
 * Style s = new Style();
 * s.setDataFormat(DataFormat.dateTime("dd/MM/yyyy"));
 * range.setStyle(s);
 * }</pre>
 *
 * <p>This version writes only {@link #TEXT} and {@link #ISO_DATE} to the ODS
 * file; see {@link Style#setDataFormat(DataFormat)}.
 *
 * <p>There are three kinds ({@link Kind}). The caller always chooses the
 * kind, so a pattern is never guessed, as with Java's own
 * {@link java.time.format.DateTimeFormatter} and {@link java.text.DecimalFormat}:
 *
 * <ul>
 *     <li>{@link #TEXT}: the content is plain text (pattern {@code @}).</li>
 *     <li>{@link #dateTime(String)}: a {@code DateTimeFormatter} pattern.</li>
 *     <li>{@link #number(String)}: a {@code DecimalFormat} pattern.</li>
 * </ul>
 *
 * <p>Only a strict subset of each Java syntax is accepted. Every accepted
 * pattern is valid Java with the same meaning, and it can be stored exactly
 * as an ODF 1.2 data style. Anything else throws an
 * {@link IllegalArgumentException} that explains the problem, with the index
 * where there is one, and a fix. Patterns mean what they mean in Java with
 * {@code Locale.US} and the ISO calendar; no default locale or time zone is
 * used. Month and weekday names are English and the decimal separator is
 * {@code '.'}; office software may show names in its own language.
 *
 * <p>Some values still render differently in office software, whatever the
 * pattern:
 *
 * <ul>
 *     <li>ties: office software rounds half up, Java half even (pattern
 *         {@code 0} on 2.5: office software {@code 3}, Java {@code 2})</li>
 *     <li>fractions of a second: office software rounds half up, without
 *         carrying into the second; Java truncates (pattern {@code ss.SSS} on
 *         09.1235: office software {@code 09.124}, Java {@code 09.123})</li>
 *     <li>digits: office software shows at most 15 significant digits; Java
 *         shows all of them</li>
 *     <li>a negative value that rounds to zero: office software shows no
 *         sign; Java shows {@code -0}</li>
 *     <li>dates before 1582-10-15: office software uses the Julian calendar;
 *         Java the ISO calendar</li>
 * </ul>
 *
 * <p>Date/time patterns ({@link #dateTime(String)}), accepted:
 *
 * <ul>
 *     <li>{@code yyyy-MM-dd} gives {@code 2026-10-05}</li>
 *     <li>{@code dd/MM/yyyy} gives {@code 05/10/2026}</li>
 *     <li>{@code d MMMM yyyy} gives {@code 5 October 2026}</li>
 *     <li>{@code EEEE, d MMM yy} gives {@code Monday, 5 Oct 26}</li>
 *     <li>{@code HH:mm:ss} gives {@code 14:07:09}</li>
 *     <li>{@code hh:mm a} gives {@code 02:07 PM}</li>
 *     <li>{@code HH:mm:ss.SSS} gives {@code 14:07:09.123}</li>
 *     <li>{@code yyyy-MM-dd'T'HH:mm} gives {@code 2026-10-05T14:07}</li>
 * </ul>
 *
 * <p>Letters allowed: {@code y} ({@code yy}, {@code yyyy}), {@code M}
 * (1 to 4), {@code d} (1 to 2), {@code E} (1 to 4), {@code QQQ},
 * {@code QQQQ}, {@code H}, {@code h} (needs {@code a}), {@code m}, {@code s},
 * {@code S} (1 to 9, only right after a seconds field and {@code '.'}) and
 * {@code a}.
 * Each field may appear once. Text in single quotes is literal and
 * {@code ''} is a literal quote. Unquoted, only space, {@code - : / . , ( )},
 * digits and non-ASCII text are literal; other text, including {@code T},
 * must be quoted. Digits are literal: {@code mm:ss.0} prints {@code .0}; use
 * {@code ss.S} for tenths.
 *
 * <p>Date/time, rejected, use instead:
 *
 * <ul>
 *     <li>{@code hh:mm} (12-hour without AM/PM): {@code HH:mm} or {@code hh:mm a}</li>
 *     <li>{@code HH:mm a} (24-hour with AM/PM): {@code hh:mm a}</li>
 *     <li>{@code YYYY} (week-based year): {@code yyyy}; {@code u}: {@code yyyy}</li>
 *     <li>{@code mm} alone (a lone minute is read as a month): {@code HH:mm} or {@code mm:ss}</li>
 *     <li>{@code yyyy-mm-dd} (spreadsheet style): {@code yyyy-MM-dd}</li>
 *     <li>time zones ({@code z Z X x O V}), {@code [h]} and {@code [~buddhist]}
 *         (spreadsheet codes): remove them</li>
 *     <li>{@code #,##0.00} (a number pattern): {@link #number(String)}</li>
 * </ul>
 *
 * <p>Number patterns ({@link #number(String)}), accepted:
 *
 * <ul>
 *     <li>{@code 0.00} gives {@code 1234.50}</li>
 *     <li>{@code #,##0.00} gives {@code 1,234.50}</li>
 *     <li>{@code 000} gives {@code 005} for 5</li>
 *     <li>{@code 0.0%} gives {@code 50.0%} for 0.5</li>
 *     <li>{@code $#,##0.00} gives {@code $1,234.50}</li>
 *     <li>{@code 0.00 m} gives {@code 1234.50 m} (as in Java, unquoted letters
 *         other than {@code E} are literal text)</li>
 * </ul>
 *
 * <p>Symbols: {@code 0} is a mandatory digit, {@code #} an optional integer
 * digit (before any {@code 0}), {@code ,} grouping (exactly three digits after
 * the last comma), {@code .} the decimal point (only {@code 0} after it) and
 * {@code %} a percentage, once, before or after the number. A pattern needs
 * at least one {@code 0} or {@code #}, in a single run.
 *
 * <p>Number, rejected, use instead:
 *
 * <ul>
 *     <li>{@code 0.0#} (optional fraction digits): {@code 0.00}</li>
 *     <li>{@code 0;-0} (separate negative section): {@code 0}; the minus is automatic</li>
 *     <li>{@code -0.00}: remove the sign, the minus is automatic (a quoted
 *         {@code '-'} prints on every value); a {@code +} is literal text</li>
 *     <li>{@code 000-00-0000} (digits after literal text): one run of digits only,
 *         or store such codes as text with {@link #TEXT}</li>
 *     <li>more than 309 integer or 340 decimal zeros (Java's limits): fewer zeros</li>
 *     <li>{@code #} or {@code #,###} (spreadsheets show zero as empty): {@code 0} or {@code #,##0}</li>
 *     <li>{@code yyyy-MM-dd} (a date pattern): {@link #dateTime(String)}</li>
 *     <li>{@code _}, {@code *}, {@code ?}, {@code [}, {@code ]}, {@code \}
 *         (spreadsheet codes): remove them</li>
 *     <li>{@code E}, {@code "...."}: quote the text</li>
 *     <li>{@code ‰} (per mille): {@code %}, or quote it to print it</li>
 *     <li>{@code ¤}: write the currency symbol itself</li>
 * </ul>
 *
 * <p>Both kinds, rejected:
 *
 * <ul>
 *     <li>{@code General}, {@code Standard} (spreadsheet names) and {@code @}:
 *         {@code null} for the default format, {@link #TEXT} for text</li>
 *     <li>four or more apostrophes in a row outside quotes: use {@code ''}
 *         for a quote</li>
 *     <li>characters XML cannot keep, such as control characters, including
 *         tab and line breaks: remove them</li>
 * </ul>
 */
public final class DataFormat {

    /** The kind of a {@link DataFormat}. */
    public enum Kind {
        /** Plain text. */
        TEXT,
        /** A date and/or time pattern. */
        DATE_TIME,
        /** A number pattern. */
        NUMBER
    }

    /**
     * Plain text: the content is never interpreted. The pattern is
     * {@code @}, the same as {@code Style.setDataStyle("@")}.
     */
    public static final DataFormat TEXT = new DataFormat(Kind.TEXT, "@");

    /**
     * The ISO 8601 date, {@code dateTime("yyyy-MM-dd")}, the same as
     * {@code Style.setDataStyle("YYYY-MM-DD")}. It is the format applied
     * automatically to a cell that is given a {@link java.time.LocalDate}.
     * The output equals {@link java.time.format.DateTimeFormatter#ISO_LOCAL_DATE}
     * for year 1 and later. For year 0 and earlier they differ: {@code yyyy}
     * is the year of era, so year 0 prints {@code 0001}.
     */
    public static final DataFormat ISO_DATE = dateTime("yyyy-MM-dd");

    private final Kind kind;
    private final String pattern;

    private DataFormat(Kind kind, String pattern) {
        this.kind = kind;
        this.pattern = pattern;
    }

    /**
     * Creates a date/time format from a {@code DateTimeFormatter} pattern.
     * See the class description for the accepted syntax.
     *
     * @param pattern the pattern, such as {@code yyyy-MM-dd} or {@code HH:mm}
     * @return the format
     * @throws NullPointerException if {@code pattern} is {@code null}
     * @throws IllegalArgumentException if the pattern is not accepted; the
     *         message explains the problem and a fix
     */
    public static DataFormat dateTime(String pattern) {
        Objects.requireNonNull(pattern, "pattern can not be null");
        DateTimePattern.parse(pattern);
        return new DataFormat(Kind.DATE_TIME, pattern);
    }

    /**
     * Creates a number format from a {@code DecimalFormat} pattern.
     * See the class description for the accepted syntax.
     *
     * @param pattern the pattern, such as {@code 0.00} or {@code #,##0.00}
     * @return the format
     * @throws NullPointerException if {@code pattern} is {@code null}
     * @throws IllegalArgumentException if the pattern is not accepted; the
     *         message explains the problem and a fix
     */
    public static DataFormat number(String pattern) {
        Objects.requireNonNull(pattern, "pattern can not be null");
        NumberPattern.parse(pattern);
        return new DataFormat(Kind.NUMBER, pattern);
    }

    /**
     * Returns the kind of this format.
     *
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Returns the pattern as it was given, or {@code @} for {@link #TEXT}.
     *
     * @return the pattern
     */
    public String getPattern() {
        return pattern;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFormat other = (DataFormat) o;
        return kind == other.kind && pattern.equals(other.pattern);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + pattern.hashCode();
    }

    /**
     * Returns a Java-like expression, such as
     * {@code DataFormat.dateTime("yyyy-MM-dd")}.
     *
     * @return the description
     */
    @Override
    public String toString() {
        switch (kind) {
            case TEXT:
                return "DataFormat.TEXT";
            case DATE_TIME:
                return "DataFormat.dateTime(" + quoted(pattern) + ")";
            default:
                return "DataFormat.number(" + quoted(pattern) + ")";
        }
    }

    // A valid Java string literal for the pattern.
    private static String quoted(String pattern) {
        return '"' + pattern.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
