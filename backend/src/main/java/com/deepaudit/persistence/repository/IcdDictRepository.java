package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.IcdDict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface IcdDictRepository extends JpaRepository<IcdDict, Long> {

    long countByCategory(String category);

    /**
     * Idempotent insert keyed on UNIQUE(code, category, version) -- on conflict
     * the name and embedding_text are refreshed (字典名/同义词修订时的常见情况).
     * {@code name_embedding} is left untouched: vector regeneration is a
     * separate offline task driven by IcdDictEmbeddingService.
     *
     * <p>Native query because JPA cannot express PostgreSQL ON CONFLICT.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO icd_dict (code, name, category, version, embedding_text, created_at)
        VALUES (:code, :name, :category, :version, :embeddingText, NOW())
        ON CONFLICT (code, category, version)
        DO UPDATE SET name = EXCLUDED.name,
                      embedding_text = EXCLUDED.embedding_text
        """, nativeQuery = true)
    void upsert(@Param("code") String code,
                @Param("name") String name,
                @Param("category") String category,
                @Param("version") String version,
                @Param("embeddingText") String embeddingText);

    /**
     * Pending rows for IcdDictEmbeddingService.reembedMissing(). Returns
     * full entities (we need id + embeddingText together; loading both via
     * a projection saves nothing for ~300 rows but matters at full scale).
     * For 30 000-row dictionaries consider switching to a streaming query
     * or a {@code Slice} with explicit pagination.
     */
    List<IcdDict> findByNameEmbeddingIsNullOrderByIdAsc();

    long countByNameEmbeddingIsNull();

    /**
     * Wipe every row's vector — used by reembedAll() before re-running the
     * embedding pipeline (e.g. after a model or dimension change).
     * @return rows actually cleared
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE icd_dict SET name_embedding = NULL WHERE name_embedding IS NOT NULL",
           nativeQuery = true)
    int clearAllEmbeddings();
}
