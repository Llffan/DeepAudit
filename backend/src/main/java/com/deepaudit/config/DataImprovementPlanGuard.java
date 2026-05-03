package com.deepaudit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * C-regulatory-002 / REQ-NFR-data-residency:
 * On startup, validate that DashScope console "数据改进计划" toggle is OFF
 * (so API requests are not used for training).  We cannot read the cloud
 * console programmatically; we rely on the operator passing
 * DASHSCOPE_DATA_IMPROVEMENT=on|off as an env var to declare what they
 * actually configured.  If they declare it on while our policy says it
 * must be off, log WARN.  Does NOT auto-disable.
 */
@Configuration
public class DataImprovementPlanGuard {

    private static final Logger log =
        LoggerFactory.getLogger(DataImprovementPlanGuard.class);

    @Bean
    ApplicationRunner dataImprovementPlanGuardRunner(
            @Value("${dashscope.data-improvement.allowed:false}") boolean allowed,
            @Value("${dashscope.data-improvement.detected-state:off}") String detectedState) {
        return args -> {
            boolean detectedOn = "on".equalsIgnoreCase(detectedState.trim());
            if (detectedOn && !allowed) {
                log.warn("DashScope data improvement plan is reportedly ON " +
                         "but app policy requires OFF (C-regulatory-002 / " +
                         "REQ-NFR-data-residency). Disable the console toggle " +
                         "before any production demo. detectedState={} allowed={}",
                         detectedState, allowed);
            } else {
                log.info("DashScope data improvement plan check OK. " +
                         "detectedState={} allowed={}", detectedState, allowed);
            }
        };
    }
}
