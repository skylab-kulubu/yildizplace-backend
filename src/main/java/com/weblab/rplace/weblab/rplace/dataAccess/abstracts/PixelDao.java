package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.weblab.rplace.weblab.rplace.entities.Pixel;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

@Repository
public interface PixelDao extends JpaRepository<Pixel, Integer> {

	Pixel findByXAndY(int x, int y);

	List<Pixel> findAllByOrderByXAscYAsc();

	@Transactional
	@Modifying
	@Query("UPDATE Pixel p SET p.color = :newColor WHERE p.x >= :startX AND p.x <= :endX AND p.y >= :startY AND p.y <= :endY")
	void updateColors(String newColor, int startX, int endX, int startY, int endY);

}
