package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import com.weblab.rplace.weblab.rplace.entities.BannedIp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BannedIpDao extends JpaRepository<BannedIp, Integer>{

    BannedIp findByIp(String ip);


}
