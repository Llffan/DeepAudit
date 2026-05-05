package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.MedicalRecordDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicalRecordDiagnosisRepository
        extends JpaRepository<MedicalRecordDiagnosis, Long> {

    /**
     * Returns all diagnoses for a record. Ordering: main first, then other rows
     * by their seq_no — diag_type='main' compares less than 'other' lexically.
     */
    List<MedicalRecordDiagnosis> findByRecordIdOrderByDiagTypeAscSeqNoAsc(Long recordId);

    /**
     * Bulk-delete all diagnosis rows for a record. Used by the save flow before
     * re-inserting the new set (idempotent replace, easier than diff/upsert).
     */
    @Modifying
    @Query("delete from MedicalRecordDiagnosis d where d.recordId = :recordId")
    void deleteByRecordId(@Param("recordId") Long recordId);

    long countByRecordIdAndDiagType(Long recordId, String diagType);
}
