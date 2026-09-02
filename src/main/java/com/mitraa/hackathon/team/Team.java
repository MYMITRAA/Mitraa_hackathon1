package com.mitraa.hackathon.team;

import com.mitraa.hackathon.registration.Registration;
import com.mitraa.hackathon.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(
        optional = false,
        fetch = FetchType.LAZY
    )
    @JoinColumn(
        name = "registration_id",
        nullable = false,
        unique = true
    )
    private Registration registration;

    /*
     * Fix for:
     * Field 'leader_user_id' doesn't have a default value
     */
    @ManyToOne(
        optional = false,
        fetch = FetchType.LAZY
    )
    @JoinColumn(
        name = "leader_user_id",
        nullable = false
    )
    private User leaderUser;

    @Column(
        nullable = false,
        length = 100
    )
    private String name;

    @Column(
        nullable = false,
        unique = true,
        length = 24
    )
    private String code;

    @Column(
        nullable = false,
        length = 24
    )
    private String status = "LOCKED";

    @Column(nullable = false)
    private Instant lockedAt = Instant.now();

    @OneToMany(
        mappedBy = "team",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<TeamMember> members = new ArrayList<>();

    @Column(
        nullable = false,
        updatable = false
    )
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Registration getRegistration() {
        return registration;
    }

    public void setRegistration(Registration registration) {
        this.registration = registration;
    }

    public User getLeaderUser() {
        return leaderUser;
    }

    public void setLeaderUser(User leaderUser) {
        this.leaderUser = leaderUser;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public List<TeamMember> getMembers() {
        return members;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void addMember(TeamMember member) {
        members.add(member);
        member.setTeam(this);
    }
}