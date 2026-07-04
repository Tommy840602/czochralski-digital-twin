package com.twin.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, unique = true, length = 128)
    private String email;

    @Column(length = 32)
    private String phone;

    /** BCrypt 雜湊；第三方登入帳號可為 null */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    /** LOCAL / GITHUB / GOOGLE / AZURE */
    @Column(nullable = false, length = 16)
    private String provider = "LOCAL";

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}