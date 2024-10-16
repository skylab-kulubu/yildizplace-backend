package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import com.weblab.rplace.weblab.rplace.entities.FinalPixel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinalPixelDao extends JpaRepository<FinalPixel, Integer> {

    Optional<FinalPixel> findByXAndY(int x, int y);

}
