package com.github.miachm.sods;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.Locale;

import static com.github.miachm.sods.OpenDocumentNamespaces.OFFICE;

/**
 * see
 * http://docs.oasis-open.org/office/v1.2/part1/cd04/OpenDocument-v1.2-part1-cd04.html#a_19_387_office_value-type
 */
enum OfficeValueType {

    BOOLEAN("boolean", Boolean.class) {
        @Override
        public Object read(XmlReaderInstance reader) {
            String raw = reader.getAttribValue("office:boolean-value");
            if (raw == null) {
                return null;
            }
            switch (raw) {
                case "true":
                    return true;
                case "false":
                    return false;
                default:
                    return null;
            }
        }

        @Override
        public void write(Object value, XMLStreamWriter writer) throws XMLStreamException {
            if (value instanceof Boolean) {
                writer.writeAttribute(OFFICE, "value-type", getId());
                writer.writeAttribute(OFFICE, "boolean-value", value.toString());
            }
        }
    },
    CURRENCY("currency", OfficeCurrency.class) {
        @Override
        public Object read(XmlReaderInstance reader) {
            String tag = reader.getAttribValue("office:currency");
            Currency currency = null;
			if (tag != null) {
                currency = Currency.getInstance(tag);
			}

            Double value = parseXsdDouble(reader.getAttribValue("office:value"));
            return new OfficeCurrency(currency, value);
        }

        @Override
        public void write(Object value, XMLStreamWriter writer) throws XMLStreamException {
            if (value instanceof OfficeCurrency) {
                writer.writeAttribute(OFFICE, "value-type", getId());

                OfficeCurrency currency = (OfficeCurrency) value;

                if (currency.getValue() != null) {
                    writer.writeAttribute(OFFICE, "value", toXsdDouble(currency.getValue()));
                }

                if (currency.getCurrency() != null)
                    writer.writeAttribute(OFFICE, "currency", currency.getCurrency().getCurrencyCode());
            }
        }
    },
    DATE("date", LocalDateTime.class, LocalDate.class) {
        @Override
        public Object read(XmlReaderInstance reader) {
            String raw = reader.getAttribValue("office:date-value");
            if (raw == null) {
                return null;
            }
            try {
                return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException dateTimeEx) {
                try {
                    return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (DateTimeParseException dateEx) {
                    return null;
                }
            }
        }

        @Override
        public void write(Object value, XMLStreamWriter writer) throws XMLStreamException {
            if (value instanceof LocalDateTime) {
                writer.writeAttribute(OFFICE, "value-type", getId());
                writer.writeAttribute(OFFICE, "date-value", ((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else if (value instanceof LocalDate) {
                writer.writeAttribute(OFFICE, "value-type", getId());
                writer.writeAttribute(OFFICE, "date-value", ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }
    },
    FLOAT("float", Number.class) {
        @Override
        public Object read(XmlReaderInstance reader) {
            String raw = reader.getAttribValue("office:value");
            if (raw == null) {
                return null;
            }

            NumberFormat nf = NumberFormat.getInstance(Locale.US);
            Double value = null;
            try {
                value = nf.parse(reader.getAttribValue("office:value")).doubleValue();
            }
            catch (ParseException e)
            {}
            return value;
        }

        @Override
        public void write(Object value, XMLStreamWriter writer) throws XMLStreamException {
            if (value instanceof Number) {
                writer.writeAttribute(OFFICE, "value-type", getId());
                writer.writeAttribute(OFFICE, "value", value.toString());
            }
        }
    },
    PERCENTAGE("percentage", OfficePercentage.class) {
        @Override
        public Object read(XmlReaderInstance reader) {
            Double value = parseXsdDouble(reader.getAttribValue("office:value"));
            return new OfficePercentage(value);
        }

        @Override
        public void write(Object value, XMLStreamWriter writer) throws XMLStreamException {
            if (value instanceof OfficePercentage) {
                writer.writeAttribute(OFFICE, "value-type", getId());

                OfficePercentage percentage = (OfficePercentage) value;

                if (percentage.getValue() != null) {
                    writer.writeAttribute(OFFICE, "value", toXsdDouble(percentage.getValue()));
                }
            }
        }
    },
    STRING("string", String.class) {
        @Override
        public Object read(XmlReaderInstance reader) {
            return reader.getAttribValue("office:string-value");
        }

        @Override
        public void write(Object value, XMLStreamWriter writer) throws XMLStreamException {
            if (value instanceof String) {
                writer.writeAttribute(OFFICE, "value-type", this.getId());
                writer.writeAttribute(OFFICE, "string-value", value.toString().replace("\n", "&#x0a;"));
            }
        }
    },
    TIME("time", Duration.class) {
        @Override
        public Object read(XmlReaderInstance reader) {
            String raw = reader.getAttribValue("office:time-value");
            if (raw == null) {
                return null;
            }
            try {
                return Duration.parse(raw);
            } catch (DateTimeParseException dateEx) {
                return null;
            }
        }

        @Override
        public void write(Object value, XMLStreamWriter writer) throws XMLStreamException {
            if (value instanceof Duration) {
                writer.writeAttribute(OFFICE, "value-type", getId());
                writer.writeAttribute(OFFICE, "time-value", value.toString());
            }
        }
    },
    VOID("void") {
        @Override
        public Object read(XmlReaderInstance reader) {
            return null;
        }

        @Override
        public void write(Object value, XMLStreamWriter writer) throws XMLStreamException {
            // nothing to do
        }
    };

    private final String id;
    private final Class<?>[] javaTypes;

    private OfficeValueType(String id, Class<?>... javaTypes) {
        this.id = id;
        this.javaTypes = javaTypes;
    }

    public String getId() {
        return id;
    }

    public boolean canHandle(Class<?> type) {
        for (Class<?> javaType : javaTypes) {
            if (javaType.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads the current cell value.
     *
     * @param reader input
     * @return a value if possible, null otherwise
     */
    abstract public Object read(XmlReaderInstance reader);

    /**
     * Writes the current cell value
     *
     * @param value a non-null value
     * @param writer output
     * @throws XMLStreamException
     */
    abstract public void write(Object value, XMLStreamWriter writer) throws XMLStreamException;

    public static OfficeValueType ofReader(XmlReaderInstance reader) {
        String raw = reader.getAttribValue("office:value-type");
        return raw != null ? ofId(raw) : DEFAULT_VALUE;
    }

    public static OfficeValueType ofId(String id) {
        for (OfficeValueType valueType : values()) {
            if (valueType.getId().equals(id)) {
                return valueType;
            }
        }
        return DEFAULT_VALUE;
    }

    public static OfficeValueType ofJavaType(Class<?> type) {
        for (OfficeValueType valueType : values()) {
            if (valueType.canHandle(type)) {
                return valueType;
            }
        }
        return DEFAULT_VALUE;
    }

    private static final OfficeValueType DEFAULT_VALUE = OfficeValueType.STRING;

    /**
     * Formats a number as a locale-independent xsd:double for office:value:
     * no grouping separators, '.' as decimal separator, no precision loss.
     * Non-finite values use the xsd:double literals INF, -INF and NaN.
     */
    static String toXsdDouble(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "INF" : "-INF";
        }
        if (value == 0) {
            return 1 / value < 0 ? "-0" : "0";
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * Parses an office:value written as xsd:double, including INF, -INF and NaN.
     *
     * @return the value, or null if raw is null or not a number
     */
    static Double parseXsdDouble(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw) {
            case "NaN": return Double.NaN;
            case "INF":
            case "+INF": return Double.POSITIVE_INFINITY;
            case "-INF": return Double.NEGATIVE_INFINITY;
        }
        try {
            return NumberFormat.getInstance(Locale.US).parse(raw).doubleValue();
        }
        catch (ParseException e) {
            return null;
        }
    }
}
