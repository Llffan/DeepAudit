package com.deepaudit.engine;

import com.deepaudit.persistence.entity.QcOperatorTemplate;
import com.deepaudit.persistence.repository.QcOperatorTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory cache of enabled custom operator templates.
 *
 * <p>Loaded from {@code qc_operator_template} at startup via {@link #reload()}.
 * Every write through {@link com.deepaudit.api.controller.OperatorTemplateController}
 * calls {@link #reload()} so changes take effect immediately without restart.
 *
 * <p>Thread-safe: the volatile reference swap makes the full map visible
 * to all threads atomically.
 */
@Component
public class CustomOperatorRegistry {

    private static final Logger log = LoggerFactory.getLogger(CustomOperatorRegistry.class);

    private final QcOperatorTemplateRepository repository;

    /** volatile so readers always see a fully-constructed map after reload(). */
    private volatile Map<String, QcOperatorTemplate> cache = Map.of();

    public CustomOperatorRegistry(QcOperatorTemplateRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void reload() {
        List<QcOperatorTemplate> enabled = repository.findAllByEnabledTrueOrderByCodeAsc();
        Map<String, QcOperatorTemplate> fresh = enabled.stream()
            .collect(Collectors.toMap(QcOperatorTemplate::getCode, t -> t));
        this.cache = new ConcurrentHashMap<>(fresh);
        log.info("CustomOperatorRegistry reloaded: {} operator(s) active", fresh.size());
    }

    /** Returns the body DSL for a custom operator, or {@code null} if not found/disabled. */
    public JsonNode getBodyDsl(String code) {
        QcOperatorTemplate t = cache.get(code);
        return t == null ? null : t.getBodyDsl();
    }

    /** Returns the template, or {@code null}. */
    public QcOperatorTemplate get(String code) {
        return cache.get(code);
    }

    public boolean isKnown(String code) {
        return cache.containsKey(code);
    }

    public Collection<QcOperatorTemplate> all() {
        return cache.values();
    }
}
