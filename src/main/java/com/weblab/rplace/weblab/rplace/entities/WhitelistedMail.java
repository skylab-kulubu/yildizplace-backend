package com.weblab.rplace.weblab.rplace.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An address on the whitelist. Entries are revoked and restored, never deleted
 * (ADR 0042): a revoked entry keeps its row, with when and by whom, and stops
 * counting at once.
 */
@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "whitelisted_mails")
public class WhitelistedMail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "mail")
    private String mail;

    // Null while the entry counts.
    @Column(name = "revoked_at")
    private Instant revokedAt;

    // The Place user who revoked it.
    @Column(name = "revoked_by_id")
    private Integer revokedById;
}
