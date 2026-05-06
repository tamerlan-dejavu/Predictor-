package kz.devchonki.predictor.model;

/**
 * A single decoded line from a branch-trace file.
 *
 * @param pc    program counter (instruction address) of the branch
 * @param taken whether the branch was actually taken at runtime
 */
public record TraceEntry(long pc, boolean taken) {}
