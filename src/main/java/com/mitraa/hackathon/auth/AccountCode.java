package com.mitraa.hackathon.auth;
import com.mitraa.hackathon.user.User;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="account_codes")
public class AccountCode {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="user_id") private User user;
 @Column(nullable=false,length=30) private String purpose;
 @Column(nullable=false) private String codeHash;
 @Column(nullable=false) private Instant expiresAt;
 private Instant consumedAt;
 @Column(nullable=false) private int attempts;
 @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
 public Long getId(){return id;} public User getUser(){return user;} public void setUser(User v){user=v;} public String getPurpose(){return purpose;} public void setPurpose(String v){purpose=v;} public String getCodeHash(){return codeHash;} public void setCodeHash(String v){codeHash=v;} public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;} public Instant getConsumedAt(){return consumedAt;} public void setConsumedAt(Instant v){consumedAt=v;} public int getAttempts(){return attempts;} public void setAttempts(int v){attempts=v;} public Instant getCreatedAt(){return createdAt;}
}
