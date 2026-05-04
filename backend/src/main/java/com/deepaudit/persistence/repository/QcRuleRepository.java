package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.QcRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QcRuleRepository extends JpaRepository<QcRule, Long> {

    Optional<QcRule> findByCode(String code);

    List<QcRule> findAllByEnabledTrue();
}
