package kz.devchonki.predictor.parser;

import kz.devchonki.predictor.model.TraceEntry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses branch-trace files into a list of {@link TraceEntry} objects.
 *
 * <p>Supported line format (whitespace-separated):
 * <pre>
 *   &lt;hex-address&gt;  &lt;1|0&gt;
 * </pre>
 * Example: {@code 0x400120  1}
 *
 * <p>Lines starting with {@code #} and blank lines are skipped.
 */
@Service
public class TraceParser {

    /**
     * Parses a trace file at the given path.
     *
     * @param traceFile path to the trace file
     * @return ordered list of branch events
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if any data line is malformed
     */
    public List<TraceEntry> parse(Path traceFile) throws IOException {
        List<String> lines = Files.readAllLines(traceFile);
        return parseLines(lines, traceFile.toString());
    }

    /**
     * Parses a trace from a raw string — useful for unit tests (no file needed).
     *
     * @param content multi-line trace content
     * @return ordered list of branch events
     * @throws IllegalArgumentException if any data line is malformed
     */
    public List<TraceEntry> parseFromString(String content) {
        List<String> lines = List.of(content.split("\r?\n", -1));
        return parseLines(lines, "<string>");
    }

    // ── internals ────────────────────────────────────────────────────────────

    private List<TraceEntry> parseLines(List<String> lines, String source) {
        List<TraceEntry> entries = new ArrayList<>(lines.size());
        int lineNo = 0;
        for (String raw : lines) {
            lineNo++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            entries.add(parseLine(line, source, lineNo));
        }
        return entries;
    }

    private TraceEntry parseLine(String line, String source, int lineNo) {
        // strip UTF-8 BOM that PowerShell / some editors prepend to the first line
        if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
            line = line.substring(1);
        }
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    String.format("[%s:%d] Expected '<hex-pc> <0|1>', got: '%s'",
                            source, lineNo, line));
        }

        long pc;
        try {
            String hexPart = parts[0].startsWith("0x") || parts[0].startsWith("0X")
                    ? parts[0].substring(2)
                    : parts[0];
            pc = Long.parseLong(hexPart, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("[%s:%d] Invalid hex PC '%s'", source, lineNo, parts[0]), e);
        }

        String takenToken = parts[1].trim();
        boolean taken = switch (takenToken) {
            case "1", "T", "t", "taken", "TAKEN" -> true;
            case "0", "N", "n", "not-taken", "NOT-TAKEN", "NT", "nt" -> false;
            default -> throw new IllegalArgumentException(
                    String.format("[%s:%d] Unknown outcome token '%s'", source, lineNo, takenToken));
        };

        return new TraceEntry(pc, taken);
    }
}
