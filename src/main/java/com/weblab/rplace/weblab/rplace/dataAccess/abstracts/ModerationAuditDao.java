package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import com.weblab.rplace.weblab.rplace.entities.ModerationAuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModerationAuditDao extends JpaRepository<ModerationAuditEntry, Integer> {

    List<ModerationAuditEntry> findAllByOrderByIdDesc();
}
