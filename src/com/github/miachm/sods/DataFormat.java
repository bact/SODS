// SPDX-FileType: SOURCE
// SPDX-License-Identifier: Unlicense

package com.github.miachm.sods;

import java.util.Objects;

/**
 * An immutable data format: how a cell's content is interpreted and shown,
 * such as plain text, a date or a number. Use it with
 * {@link Style#setDataFormat(DataFormat)}.
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
 * {@link IllegalArgumentException} that names the offending character, its
 * index and a fix. Formatting is as with {@code Locale.US} and the ISO
 * (Gregorian) calendar; no default locale or time zone is used.
 *
 * <p>Some values still render differently in office software, whatever the
 * pattern: ties round half up there and half even in Java ({@code 0} on 2.5
 * gives {@code 3}, not {@code 2}), at most 15 significant digits are shown,
 * a negative value that rounds to zero shows no sign, and dates before
 * 1582-10-15 use the Julian calendar.
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
 * {@code S} (only as {@code ss.S} to {@code ss.SSSSSSSSS}) and {@code a}.
 * Each field may appear once. Text in single quotes is literal and
 * {@code ''} is a literal quote. Unquoted, only space and {@code - : / . ,}
 * and digits are literal; other text, including {@code T}, must be quoted.
 *
 * <p>Date/time, rejected, use instead:
 *
 * <ul>
 *     <li>{@code hh:mm} (12-hour without AM/PM): {@code HH:mm} or {@code hh:mm a}</li>
 *     <li>{@code YYYY} (week-based year): {@code yyyy}</li>
 *     <li>{@code mm} alone (a lone minute is read as a month): add an hour or
 *         seconds, as in {@code HH:mm} or {@code mm:ss}</li>
 *     <li>{@code yyyy-mm-dd} (spreadsheet style): {@code yyyy-MM-dd}</li>
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
 * {@code %} a percentage, once. A pattern needs at least one {@code 0} or
 * {@code #}.
 *
 * <p>Number, rejected, use instead:
 *
 * <ul>
 *     <li>{@code 0.0#} (optional fraction digits): {@code 0.00}</li>
 *     <li>{@code 0;-0} (separate negative section) is unsupported; the
 *         negative sign is implicit</li>
 *     <li>{@code #} or {@code #,###} (spreadsheets show zero as empty):
 *         {@code 0} or {@code #,##0}</li>
 *     <li>{@code yyyy-MM-dd} (a date pattern): {@link #dateTime(String)}</li>
 *     <li>{@code E}, {@code ;}, {@code ?}, {@code _}, {@code *}, {@code "...."},
 *         {@code General}: quote the character, or use a plain pattern</li>
 * </ul>
 *
 * <p>This version writes only {@link #TEXT} and {@link #ISO_DATE} to the ODS
 * file; see {@link Style#setDataFormat(DataFormat)}.
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
    public static final DataFormat TEXT = new DataFormat(Kind.TEXT, "@", null, null);

    /**
     * The ISO 8601 date, {@code dateTime("yyyy-MM-dd")}, the same as
     * {@code Style.setDataStyle("YYYY-MM-DD")}. It is the format applied
     * automatically to a cell that is given a {@link java.time.LocalDate}.
     * The output equals {@link java.time.format.DateTimeFormatter#ISO_LOCAL_DATE}
     * for years 1 to 9999. Outside that range they differ: {@code yyyy} is
     * the year of era, so BC years print positive, and ISO prints
     * {@code +10000}.
     */
    public static final DataFormat ISO_DATE = dateTime("yyyy-MM-dd");

    private final Kind kind;
    private final String pattern;
    // Parsed form, kept for the ODS writer; null for the other kinds.
    final DateTimePattern dateTimePattern;
    final NumberPattern numberPattern;

    private DataFormat(Kind kind, String pattern, DateTimePattern dateTimePattern,
                       NumberPattern numberPattern) {
        this.kind = kind;
        this.pattern = pattern;
        this.dateTimePattern = dateTimePattern;
        this.numberPattern = numberPattern;
    }

    /**
     * Creates a date/time format from a {@code DateTimeFormatter} pattern.
     * See the class description for the accepted syntax.
     *
     * @param pattern the pattern, such as {@code yyyy-MM-dd} or {@code HH:mm}
     * @return the format
     * @throws NullPointerException if {@code pattern} is {@code null}
     * @throws IllegalArgumentException if the pattern is not accepted; the
     *         message gives the index and a fix
     */
    public static DataFormat dateTime(String pattern) {
        Objects.requireNonNull(pattern, "pattern can not be null");
        return new DataFormat(Kind.DATE_TIME, pattern, DateTimePattern.parse(pattern), null);
    }

    /**
     * Creates a number format from a {@code DecimalFormat} pattern.
     * See the class description for the accepted syntax.
     *
     * @param pattern the pattern, such as {@code 0.00} or {@code #,##0.00}
     * @return the format
     * @throws NullPointerException if {@code pattern} is {@code null}
     * @throws IllegalArgumentException if the pattern is not accepted; the
     *         message gives the index and a fix
     */
    public static DataFormat number(String pattern) {
        Objects.requireNonNull(pattern, "pattern can not be null");
        return new DataFormat(Kind.NUMBER, pattern, null, NumberPattern.parse(pattern));
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
                return "DataFormat.dateTime(\"" + pattern + "\")";
            default:
                return "DataFormat.number(\"" + pattern + "\")";
        }
    }
}
