package org.keycloak.client.admin.cli.v2;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;
import com.fasterxml.jackson.databind.JsonNode;

import static org.keycloak.client.cli.util.OutputUtil.MAPPER;

public final class CliJsonOutputHighlighter {

    public static final String ESC = "\033[";
    public static final String RESET = ESC + "0m";
    public static final String COLOR_KEY = ESC + "34m";
    public static final String COLOR_STRING = ESC + "32m";
    public static final String COLOR_NUMBER = ESC + "36m";
    public static final String COLOR_BOOLEAN = ESC + "33m";
    public static final String COLOR_NULL = ESC + "31m";

    private static final String INDENT = "  ";
    private static final String OPEN_BRACE = "{";
    private static final String CLOSE_BRACE = "}";
    private static final String OPEN_BRACKET = "[";
    private static final String CLOSE_BRACKET = "]";
    private static final String COLON = ":";
    private static final String COMMA = ",";
    private static final String NULL = "null";
    private static final String SPACE = " ";
    private static final int CONTEXT_OBJECT = 1;
    private static final int CONTEXT_ARRAY = 2;
    private static final String[] OBJECT_COLORS = { ESC + "37m", ESC + "35m", ESC + "90m" };
    private static final String[] ARRAY_COLORS = { ESC + "93m", ESC + "95m", ESC + "96m" };

    public static final int OBJECT_DEPTH_CYCLE = OBJECT_COLORS.length;
    public static final int ARRAY_DEPTH_CYCLE = ARRAY_COLORS.length;

    private CliJsonOutputHighlighter() {
    }

    public static String objectColorAtDepth(int depth) {
        return OBJECT_COLORS[depth % OBJECT_COLORS.length];
    }

    public static String arrayColorAtDepth(int depth) {
        return ARRAY_COLORS[depth % ARRAY_COLORS.length];
    }

    public static String highlight(JsonNode tree, boolean compressed) throws IOException {
        StringWriter dummyWriter = new StringWriter();
        try (JsonGenerator stateGenerator = MAPPER.getFactory().createGenerator(dummyWriter);
             ColoringGenerator colorGen = new ColoringGenerator(stateGenerator, compressed)) {
            MAPPER.writeTree(colorGen, tree);
            return colorGen.getColoredOutput();
        }
    }

    private static String escapeJsonString(String text) throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = MAPPER.getFactory().createGenerator(sw)) {
            gen.writeString(text);
            gen.flush();
            return sw.toString();
        }
    }

    private static class ColoringGenerator extends JsonGeneratorDelegate {

        private final StringBuilder sb = new StringBuilder();
        private final boolean compressed;
        private int objectDepth = -1;
        private int arrayDepth = -1;
        private boolean needsComma = false;
        private final int[] contextStack = new int[128];
        private int stackDepth = 0;

        private ColoringGenerator(JsonGenerator delegate, boolean compressed) {
            super(delegate, false);
            this.compressed = compressed;
        }

        private String getColoredOutput() {
            return sb.toString();
        }

        @Override
        public void writeStartObject() throws IOException {
            startObject();
            super.writeStartObject();
        }

        @Override
        public void writeStartObject(Object forValue) throws IOException {
            startObject();
            super.writeStartObject(forValue);
        }

        @Override
        public void writeStartObject(Object forValue, int size) throws IOException {
            startObject();
            super.writeStartObject(forValue, size);
        }

        @Override
        public void writeEndObject() throws IOException {
            if (!compressed) {
                newlineIndent(stackDepth - 1);
            }
            sb.append(objectColorAtDepth(objectDepth)).append(CLOSE_BRACE).append(RESET);
            objectDepth--;
            stackDepth--;
            needsComma = true;
            super.writeEndObject();
        }

        @Override
        public void writeStartArray() throws IOException {
            startArray();
            super.writeStartArray();
        }

        @Override
        public void writeStartArray(int size) throws IOException {
            startArray();
            super.writeStartArray(size);
        }

        @Override
        public void writeStartArray(Object forValue) throws IOException {
            startArray();
            super.writeStartArray(forValue);
        }

        @Override
        public void writeStartArray(Object forValue, int size) throws IOException {
            startArray();
            super.writeStartArray(forValue, size);
        }

        @Override
        public void writeEndArray() throws IOException {
            if (!compressed) {
                newlineIndent(stackDepth - 1);
            }
            sb.append(arrayColorAtDepth(arrayDepth)).append(CLOSE_BRACKET).append(RESET);
            arrayDepth--;
            stackDepth--;
            needsComma = true;
            super.writeEndArray();
        }

        @Override
        public void writeFieldName(String name) throws IOException {
            if (needsComma) {
                sb.append(objectColorAtDepth(objectDepth)).append(COMMA).append(RESET);
            }
            if (!compressed) {
                newlineIndent(stackDepth);
            }
            sb.append(COLOR_KEY).append(escapeJsonString(name)).append(RESET);
            sb.append(objectColorAtDepth(objectDepth)).append(COLON).append(RESET);
            if (!compressed) {
                sb.append(SPACE);
            }
            needsComma = false;
            super.writeFieldName(name);
        }

        @Override
        public void writeFieldName(SerializableString name) throws IOException {
            writeFieldName(name.getValue());
        }

        @Override
        public void writeString(String text) throws IOException {
            appendValue(COLOR_STRING, escapeJsonString(text));
            super.writeString(text);
        }

        @Override
        public void writeNumber(int v) throws IOException {
            appendValue(COLOR_NUMBER, String.valueOf(v));
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(long v) throws IOException {
            appendValue(COLOR_NUMBER, String.valueOf(v));
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(float v) throws IOException {
            appendValue(COLOR_NUMBER, String.valueOf(v));
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(double v) throws IOException {
            appendValue(COLOR_NUMBER, String.valueOf(v));
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(BigDecimal v) throws IOException {
            appendValue(COLOR_NUMBER, v.toString());
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(BigInteger v) throws IOException {
            appendValue(COLOR_NUMBER, v.toString());
            super.writeNumber(v);
        }

        @Override
        public void writeBoolean(boolean state) throws IOException {
            appendValue(COLOR_BOOLEAN, String.valueOf(state));
            super.writeBoolean(state);
        }

        @Override
        public void writeNull() throws IOException {
            appendValue(COLOR_NULL, NULL);
            super.writeNull();
        }

        private void startObject() {
            if (needsComma) {
                appendComma();
            }
            objectDepth++;
            stackDepth++;
            contextStack[stackDepth] = CONTEXT_OBJECT;
            sb.append(objectColorAtDepth(objectDepth)).append(OPEN_BRACE).append(RESET);
            needsComma = false;
        }

        private void startArray() {
            if (needsComma) {
                appendComma();
            }
            arrayDepth++;
            stackDepth++;
            contextStack[stackDepth] = CONTEXT_ARRAY;
            sb.append(arrayColorAtDepth(arrayDepth)).append(OPEN_BRACKET).append(RESET);
            needsComma = false;
        }

        private void appendComma() {
            if (contextStack[stackDepth] == CONTEXT_OBJECT) {
                sb.append(objectColorAtDepth(objectDepth)).append(COMMA).append(RESET);
            } else {
                sb.append(arrayColorAtDepth(arrayDepth)).append(COMMA).append(RESET);
            }
        }

        private void appendValue(String color, String text) {
            if (needsComma) {
                appendComma();
            }
            if (!compressed && contextStack[stackDepth] == CONTEXT_ARRAY) {
                newlineIndent(stackDepth);
            }
            sb.append(color).append(text).append(RESET);
            needsComma = true;
        }

        private void newlineIndent(int level) {
            sb.append(System.lineSeparator());
            sb.append(INDENT.repeat(Math.max(0, level)));
        }
    }
}
