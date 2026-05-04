package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    List<CheckResult> findAllByRecordIdOrderByCreatedAtDesc(Long recordId);

    void deleteByRecordId(Long recordId);
}
