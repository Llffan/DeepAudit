package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.MedicalRecordExtra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicalRecordExtraRepository extends JpaRepository<MedicalRecordExtra, Long> {

    Optional<MedicalRecordExtra> findByRecordId(Long recordId);
}
