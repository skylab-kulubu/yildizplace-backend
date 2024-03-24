package com.weblab.rplace.weblab.rplace.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name="pixel_logs")
public class PixelLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne()
    @JoinColumn(name = "pixel_id")
    private Pixel pixel;

    //@Column(name = "old_color")
    //private String oldColor;

    @Column(name = "new_color")
    private String newColor;

    @Column(name = "placer_ip")
    private String placerIp;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "placed_at")
    private Date placedAt;



}
