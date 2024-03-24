package com.weblab.rplace.weblab.rplace.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "banned_ips")
public class BannedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name="ip")
    private String ip;

    @Column(name="reason")
    private String reason;

    @Column(name="banned_at")
    private Date bannedAt;

    @ManyToOne
    @JoinColumn(name = "banned_by_id", referencedColumnName = "id")
    private User bannedBy;


}
