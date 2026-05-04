package com.deepaudit.persistence.repository;

import com.deepaudit.persistence.entity.IcdDict;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IcdDictRepository extends JpaRepository<IcdDict, Long> {
}
