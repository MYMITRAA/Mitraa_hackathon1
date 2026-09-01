package com.mitraa.hackathon.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="users", uniqueConstraints=@UniqueConstraint(name="uk_users_email", columnNames="email"))
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=120) private String fullName;
    @Column(nullable=false, length=190) private String email;
    @Column(nullable=false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private Role role = Role.PARTICIPANT;
    @Column(nullable=false) private boolean enabled = true;
    private Instant emailVerifiedAt;
    @Column(nullable=false, updatable=false) private Instant createdAt = Instant.now();
    public Long getId(){return id;} public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
    public Role getRole(){return role;} public void setRole(Role v){role=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} public Instant getCreatedAt(){return createdAt;}
    public Instant getEmailVerifiedAt(){return emailVerifiedAt;} public void setEmailVerifiedAt(Instant v){emailVerifiedAt=v;}
}
