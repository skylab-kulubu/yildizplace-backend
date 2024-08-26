package com.weblab.rplace.weblab.rplace.dataAccess.abstracts;

import com.weblab.rplace.weblab.rplace.entities.PixelLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface PixelLogDao extends JpaRepository<PixelLog, Integer> {

    List<PixelLog> findAllByPixelIdOrderByPlacedAtDesc(int pixelId);

    List<PixelLog> findAllByPlacerIpOrderByPlacedAtDesc(String placerIp);

    List<PixelLog> findAllByPlacedAtBetweenOrderByPlacedAt(Date startDate, Date endDate);

    List<PixelLog> findAllByUserSchoolMail(String schoolMail);


    /*
    @Transactional
    @Query("INSERT INTO PixelLog (new_color, placed_at, placer_ip, pixel_id) SELECT color, CURRENT_TIMESTAMP, :ipAddress, id FROM Pixel WHERE x BETWEEN :startX AND :endX AND y BETWEEN :startY AND :endY;")
    void insertLogs(int StartX, int endX, int startY, int endY, String ipAddress);

     */

}
