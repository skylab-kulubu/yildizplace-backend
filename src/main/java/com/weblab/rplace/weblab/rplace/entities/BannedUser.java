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
@Table(name = "banned_users")
public class BannedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @OneToOne
    @JoinColumn(name = "banned_user_id", referencedColumnName = "id")
    private User bannedUser;

    @Column(name="reason")
    private String reason;

    @Column(name="banned_at")
    private Date bannedAt;

    @ManyToOne
    @JoinColumn(name = "banned_by_id", referencedColumnName = "id")
    private User bannedBy;


}

