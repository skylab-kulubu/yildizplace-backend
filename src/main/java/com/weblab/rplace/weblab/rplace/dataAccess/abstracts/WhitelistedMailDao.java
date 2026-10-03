package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;


import com.weblab.rplace.weblab.rplace.entities.WhitelistedMail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// No delete: entries are revoked (ADR 0042).
@Repository
public interface WhitelistedMailDao extends JpaRepository<WhitelistedMail, Integer>{

    boolean existsByMailIgnoreCaseAndRevokedAtIsNull(String mail);

    boolean existsByMailIgnoreCaseAndRevokedAtIsNullAndIdNot(String mail, int id);

    boolean existsByMailIgnoreCase(String mail);

    List<WhitelistedMail> findAllByRevokedAtIsNullOrderByIdAsc();

    List<WhitelistedMail> findAllByRevokedAtIsNotNullOrderByIdAsc();

    List<WhitelistedMail> findAllByOrderByIdAsc();
}
