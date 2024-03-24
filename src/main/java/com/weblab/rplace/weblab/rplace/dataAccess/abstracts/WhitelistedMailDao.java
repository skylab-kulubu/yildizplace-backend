package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;


import com.weblab.rplace.weblab.rplace.entities.WhitelistedMail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WhitelistedMailDao extends JpaRepository<WhitelistedMail, Integer>{

    boolean existsByMail(String mail);

    void deleteByMail(String mail);

    WhitelistedMail findByMail(String mail);
}
