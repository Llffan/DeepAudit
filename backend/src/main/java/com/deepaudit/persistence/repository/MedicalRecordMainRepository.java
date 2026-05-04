package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.MedicalRecordMain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MedicalRecordMainRepository extends JpaRepository<MedicalRecordMain, Long> {

    Optional<MedicalRecordMain> findBySourceHospitalAndRecordNo(String sourceHospital, String recordNo);

    /**
     * Paged list for the records list page. Both filters are optional —
     * pass {@code null} (or empty for keyword) to skip. The {@code @SQLRestriction}
     * on the entity already excludes soft-deleted rows.
     *
     * <p>Keyword does case-insensitive substring match across record_no /
     * name / main_diagnosis_name — covers "找老张"、"找 ICD I10"、"找 BA1234"
     * three common operator habits without forcing them into a dropdown.
     */
    @Query("""
        select m from MedicalRecordMain m
        where (:status is null or m.status = :status)
          and (:keyword is null or :keyword = ''
               or lower(m.recordNo) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(m.name, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(m.mainDiagnosisName, '')) like lower(concat('%', :keyword, '%')))
        """)
    Page<MedicalRecordMain> search(@Param("status") String status,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);
}
