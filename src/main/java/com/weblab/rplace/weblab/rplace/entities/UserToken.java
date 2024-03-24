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
@Table(name = "user_tokens")
public class UserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    /*
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;
     */

    @Column(name = "user_id")
    private int userId;

    @Column(name = "token")
    private String token;

    @Column(name = "user_ip")
    private String userIp;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "is_used")
    private boolean isUsed;

    @Column(name = "used_at")
    private Date usedAt;


}
