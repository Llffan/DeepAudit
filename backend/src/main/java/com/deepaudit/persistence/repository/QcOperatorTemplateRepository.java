package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.QcOperatorTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QcOperatorTemplateRepository extends JpaRepository<QcOperatorTemplate, Long> {
    List<QcOperatorTemplate> findAllByEnabledTrueOrderByCodeAsc();
    List<QcOperatorTemplate> findAllByOrderByCodeAsc();
    boolean existsByCodeAndIdNot(String code, Long id);
    boolean existsByCode(String code);
}
