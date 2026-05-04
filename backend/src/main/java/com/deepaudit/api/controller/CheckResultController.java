package com.deepaudit.api.controller;

import com.deepaudit.api.dto.CheckSummaryResponse;
import com.deepaudit.service.CheckService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T4.2 read endpoint (plan §7.6).
 *
 * <pre>
 *   GET /api/check-results/{recordId}   -&gt; latest persisted check_result rows for the record
 * </pre>
 *
 * <p>Pure read — never re-runs the engine. Use
 * {@code POST /api/medical-records/{id}/check} (T4.1) to recompute.
 */
@RestController
@RequestMapping("/check-results")
public class CheckResultController {

    private final CheckService checkService;

    public CheckResultController(CheckService checkService) {
        this.checkService = checkService;
    }

    @GetMapping("/{recordId}")
    public CheckSummaryResponse getLatest(@PathVariable Long recordId) {
        return checkService.latestResults(recordId);
    }
}
