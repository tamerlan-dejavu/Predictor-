package kz.devchonki.predictor.parser;

import kz.devchonki.predictor.model.TraceEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TraceParser")
class TraceParserTest {

    private TraceParser parser;

    @BeforeEach
    void setUp() {
        parser = new TraceParser();
    }

    @Test
    @DisplayName("Simple line: '0x400000 1' maps to TraceEntry(0x400000L, true)")
    void test_parseSimpleLine() {
        List<TraceEntry> entries = parser.parseFromString("0x400000 1");
        assertEquals(1, entries.size());
        assertEquals(new TraceEntry(0x400000L, true), entries.get(0));
    }

    @Test
    @DisplayName("Not-taken line: '0x400004 0' maps to TraceEntry(0x400004L, false)")
    void test_parseNotTaken() {
        List<TraceEntry> entries = parser.parseFromString("0x400004 0");
        assertEquals(1, entries.size());
        assertEquals(new TraceEntry(0x400004L, false), entries.get(0));
    }

    @Test
    @DisplayName("Comment lines starting with '#' are skipped")
    void test_skipComments() {
        String content = """
                # trace header
                0x400000 1
                # mid comment
                0x400004 0
                """;
        List<TraceEntry> entries = parser.parseFromString(content);
        assertEquals(2, entries.size());
        assertEquals(0x400000L, entries.get(0).pc());
        assertTrue(entries.get(0).taken());
        assertEquals(0x400004L, entries.get(1).pc());
        assertFalse(entries.get(1).taken());
    }

    @Test
    @DisplayName("Blank lines are skipped")
    void test_skipEmptyLines() {
        String content = """

                0x400000 1


                0x400004 0

                """;
        List<TraceEntry> entries = parser.parseFromString(content);
        assertEquals(2, entries.size());
    }

    @Test
    @DisplayName("Three non-comment lines produce three TraceEntry values in order")
    void test_parseMultipleLines() {
        String content = """
                0x100 1
                0x200 0
                0x300 1
                """;
        List<TraceEntry> entries = parser.parseFromString(content);
        assertEquals(3, entries.size());
        assertEquals(new TraceEntry(0x100L, true), entries.get(0));
        assertEquals(new TraceEntry(0x200L, false), entries.get(1));
        assertEquals(new TraceEntry(0x300L, true), entries.get(2));
    }

    @Test
    @DisplayName("Malformed line 'garbage' throws IllegalArgumentException")
    void test_invalidLine_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFromString("garbage"));
    }

    @Test
    @DisplayName("Hex PC without 0x prefix parses correctly")
    void test_hexWithoutPrefix() {
        List<TraceEntry> entries = parser.parseFromString("400000 1");
        assertEquals(1, entries.size());
        assertEquals(0x400000L, entries.get(0).pc());
        assertTrue(entries.get(0).taken());
    }

    @Test
    @DisplayName("parseFromString multi-line content yields two entries")
    void test_parseFromString() {
        List<TraceEntry> entries = parser.parseFromString("0x100 1\n0x200 0");
        assertEquals(2, entries.size());
        assertEquals(new TraceEntry(0x100L, true), entries.get(0));
        assertEquals(new TraceEntry(0x200L, false), entries.get(1));
    }

    @ParameterizedTest(name = "invalid hex token ''{0}'' throws")
    @ValueSource(strings = {"0xZZ 1", "0x400000", "0x400000 maybe"})
    @DisplayName("Various invalid inputs throw IllegalArgumentException")
    void test_invalidInputs_parameterized(String line) {
        assertThrows(IllegalArgumentException.class, () -> parser.parseFromString(line));
    }
}
