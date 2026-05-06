package kz.devchonki.predictor.parser;

import kz.devchonki.predictor.model.TraceEntry;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses branch-trace files into a list of {@link TraceEntry} objects.
 *
 * <p>Expected line format (whitespace-separated):
 * <pre>
 *   &lt;hex-address&gt;  &lt;T|N&gt;
 * </pre>
 * Example: {@code 0x400120  T}
 *
 * <p>Lines starting with {@code #} and blank lines are ignored.
 *
 * <p>TODO: add support for additional trace formats (PIN, gem5, …).
 */
@Service
public class TraceParser {

    /**
     * Parses a trace file at the given path.
     *
     * @param path path to the trace file
     * @return ordered list of branch events
     * @throws IOException if the file cannot be read
     */
    public List<TraceEntry> parse(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return parse(is);
        }
    }

    /**
     * Parses a trace from an already-open stream (useful for classpath resources).
     *
     * @param is input stream; caller is responsible for closing it
     * @return ordered list of branch events
     * @throws IOException on read errors
     */
    public List<TraceEntry> parse(InputStream is) throws IOException {
        List<TraceEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                entries.add(parseLine(line));
            }
        }
        return entries;
    }

    private TraceEntry parseLine(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed trace line: " + line);
        }
        long pc = Long.decode(parts[0]);
        boolean taken = switch (parts[1].toUpperCase()) {
            case "T", "1", "TAKEN" -> true;
            case "N", "0", "NOT-TAKEN", "NT" -> false;
            default -> throw new IllegalArgumentException(
                    "Unknown outcome token: " + parts[1]);
        };
        return new TraceEntry(pc, taken);
    }
}
