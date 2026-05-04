package com.deepaudit.api.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/rules/dry-run}.
 *
 * <p>{@code status} discriminates four outcomes:
 * <ul>
 *   <li>{@code "satisfied"} -- DSL valid + evaluator returned true (no violation).
 *   <li>{@code "hit"}       -- DSL valid + evaluator returned false (rule fires).
 *   <li>{@code "invalid"}   -- DSL failed structural validation (T2.2);
 *                              {@code validationErrors} populated.
 *   <li>{@code "error"}     -- evaluator threw at runtime (e.g. type
 *                              mismatch beyond what T2.2 catches);
 *                              {@code evaluationError} populated.
 * </ul>
 */
public record DryRunResponse(
    String status,
    Boolean satisfied,
    List<String> validationErrors,
    String evaluationError
) {
    /**
     * Renamed away from {@code satisfied()} because Java record rules
     * forbid declaring any method (even a static factory) whose name
     * matches a component when the return type differs from the
     * canonical accessor. The on-the-wire {@code status} string is
     * still {@code "satisfied"}.
     */
    public static DryRunResponse pass() {
        return new DryRunResponse("satisfied", Boolean.TRUE, List.of(), null);
    }

    public static DryRunResponse hit() {
        return new DryRunResponse("hit", Boolean.FALSE, List.of(), null);
    }

    public static DryRunResponse invalid(List<String> errors) {
        return new DryRunResponse("invalid", null, List.copyOf(errors), null);
    }

    public static DryRunResponse error(String message) {
        return new DryRunResponse("error", null, List.of(), message);
    }
}
