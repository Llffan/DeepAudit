package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.IcdDict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IcdDictRepository extends JpaRepository<IcdDict, Long> {

    long countByCategory(String category);

    /**
     * Idempotent insert keyed on UNIQUE(code, category, version) -- on conflict
     * the name is refreshed (字典名修订时的常见情况). {@code name_embedding} is
     * left untouched: vector regeneration is a separate offline task.
     *
     * <p>Native query because JPA cannot express PostgreSQL ON CONFLICT.
     */
    @Modifying
    @Query(value = """
        INSERT INTO icd_dict (code, name, category, version, created_at)
        VALUES (:code, :name, :category, :version, NOW())
        ON CONFLICT (code, category, version)
        DO UPDATE SET name = EXCLUDED.name
        """, nativeQuery = true)
    void upsert(@Param("code") String code,
                @Param("name") String name,
                @Param("category") String category,
                @Param("version") String version);
}
