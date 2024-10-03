package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.weblab.rplace.weblab.rplace.entities.Pixel;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface PixelDao extends JpaRepository<Pixel, Integer> {

	Pixel findByXAndY(int x, int y);

	List<Pixel> findAllByOrderByXAscYAsc();

	@Transactional
	@Modifying
	@Query("UPDATE Pixel p SET p.color = :newColor WHERE p.x >= :startX AND p.x <= :endX AND p.y >= :startY AND p.y <= :endY")
	void updateColors(String newColor, int startX, int endX, int startY, int endY);

	@Transactional
	@Modifying
	@Query("""
    UPDATE Pixel p 
    SET p.color = (
        CASE
            WHEN (
                SELECT COUNT(pl)
                FROM PixelLog pl
                WHERE pl.pixel.id = p.id
            ) > 1 THEN (
                SELECT pl2.newColor
                FROM PixelLog pl2
                WHERE pl2.pixel.id = p.id
                AND pl2.placedAt < (
                    SELECT MAX(pl3.placedAt)
                    FROM PixelLog pl3
                    WHERE pl3.pixel.id = p.id
                )
                ORDER BY pl2.placedAt DESC
                LIMIT 1
            )
            ELSE 'ffffff'
        END
    )
    WHERE p.x >= :startX 
    AND p.x <= :endX 
    AND p.y >= :startY 
    AND p.y <= :endY
""")
	void bringBackPreviousPixels(int startX, int endX, int startY, int endY);

	List<Pixel> findAllByXBetweenAndYBetween(int startX, int endX, int startY, int endY);


}
