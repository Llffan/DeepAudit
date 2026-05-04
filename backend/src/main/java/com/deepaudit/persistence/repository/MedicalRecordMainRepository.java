package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.MedicalRecordMain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicalRecordMainRepository extends JpaRepository<MedicalRecordMain, Long> {

    Optional<MedicalRecordMain> findBySourceHospitalAndRecordNo(String sourceHospital, String recordNo);
}
