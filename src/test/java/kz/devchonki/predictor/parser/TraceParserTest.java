package kz.devchonki.predictor.parser;

import kz.devchonki.predictor.model.TraceEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TraceParser")
class TraceParserTest {

    private TraceParser parser;

    @BeforeEach
    void setUp() {
        parser = new TraceParser();
    }

    // ── happy-path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("parses '0x400000 1' as taken")
    void parsesHexTaken() {
        List<TraceEntry> entries = parser.parseFromString("0x400000 1");
        assertEquals(1, entries.size());
        assertEquals(0x400000L, entries.get(0).pc());
        assertTrue(entries.get(0).taken());
    }

    @Test
    @DisplayName("parses '0x400000 0' as not-taken")
    void parsesHexNotTaken() {
        List<TraceEntry> entries = parser.parseFromString("0x400000 0");
        assertEquals(1, entries.size());
        assertFalse(entries.get(0).taken());
    }

    @Test
    @DisplayName("parses address without 0x prefix")
    void parsesAddressWithoutPrefix() {
        List<TraceEntry> entries = parser.parseFromString("400000 1");
        assertEquals(1, entries.size());
        assertEquals(0x400000L, entries.get(0).pc());
    }

    @Test
    @DisplayName("skips comment lines starting with #")
    void skipsComments() {
        String content = """
                # this is a comment
                0x400000 1
                # another comment
                0x400004 0
                """;
        List<TraceEntry> entries = parser.parseFromString(content);
        assertEquals(2, entries.size());
    }

    @Test
    @DisplayName("skips blank lines")
    void skipsBlankLines() {
        String content = "\n\n0x400000 1\n\n0x400004 0\n\n";
        List<TraceEntry> entries = parser.parseFromString(content);
        assertEquals(2, entries.size());
    }

    @Test
    @DisplayName("parses multiple entries in order")
    void parsesMultipleEntriesInOrder() {
        String content = """
                0x100 1
                0x200 0
                0x300 1
                """;
        List<TraceEntry> entries = parser.parseFromString(content);
        assertEquals(3, entries.size());
        assertEquals(0x100L, entries.get(0).pc());
        assertTrue(entries.get(0).taken());
        assertEquals(0x200L, entries.get(1).pc());
        assertFalse(entries.get(1).taken());
        assertEquals(0x300L, entries.get(2).pc());
    }

    @Test
    @DisplayName("accepts 'T' and 'N' tokens")
    void acceptsTAndNTokens() {
        List<TraceEntry> entries = parser.parseFromString("0x400000 T\n0x400004 N");
        assertTrue(entries.get(0).taken());
        assertFalse(entries.get(1).taken());
    }

    @Test
    @DisplayName("returns empty list for blank input")
    void returnsEmptyForBlankInput() {
        assertTrue(parser.parseFromString("   \n  \n").isEmpty());
    }

    // ── error-path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("throws IllegalArgumentException for malformed line (too few tokens)")
    void throwsOnMalformedLine() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFromString("0x400000"));
    }

    @Test
    @DisplayName("throws IllegalArgumentException for invalid hex PC")
    void throwsOnInvalidHexPc() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFromString("0xZZZZZZ 1"));
    }

    @Test
    @DisplayName("throws IllegalArgumentException for unknown outcome token")
    void throwsOnUnknownOutcomeToken() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFromString("0x400000 maybe"));
    }
}
