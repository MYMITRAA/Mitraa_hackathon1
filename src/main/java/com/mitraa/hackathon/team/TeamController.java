package com.mitraa.hackathon.team;

import com.mitraa.hackathon.registration.ParticipationType;
import com.mitraa.hackathon.registration.Registration;
import com.mitraa.hackathon.registration.RegistrationRepository;
import com.mitraa.hackathon.user.User;
import com.mitraa.hackathon.user.UserRepository;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final TeamRepository teamRepository;

    public TeamController(
        UserRepository userRepository,
        RegistrationRepository registrationRepository,
        TeamRepository teamRepository
    ) {
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
        this.teamRepository = teamRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyTeam(Authentication authentication) {

        Registration registration = findRegistration(authentication);

        Team team = teamRepository
            .findByRegistration(registration)
            .orElse(null);

        if (team == null) {
            return ResponseEntity.ok(
                Map.of(
                    "configured", false,
                    "requiredMembers", 3,
                    "maximumMembers", 5
                )
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("configured", true);
        response.put("teamName", team.getName());
        response.put("teamCode", team.getCode());
        response.put("status", team.getStatus());
        response.put("memberCount", team.getMembers().size() + 1);

        response.put(
            "members",
            team.getMembers()
                .stream()
                .map(member -> Map.of(
                    "name", member.getFullName(),
                    "email", member.getEmail(),
                    "country", member.getCountry()
                ))
                .toList()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createTeam(
        Authentication authentication,
        @Valid @RequestBody TeamRequest request
    ) {

        Registration registration = findRegistration(authentication);

        if (registration.getParticipationType() != ParticipationType.TEAM) {
            return ResponseEntity
                .badRequest()
                .body(Map.of(
                    "message",
                    "Only team registrations can create a team."
                ));
        }

        if (teamRepository.findByRegistration(registration).isPresent()) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "message",
                    "A team already exists for this registration."
                ));
        }

        /*
         * TeamRequest contains 2–4 additional members.
         * The registered participant is the leader.
         * Therefore, total team size becomes 3–5 members.
         */
        int totalMemberCount = request.members().size() + 1;

        if (totalMemberCount < 3 || totalMemberCount > 5) {
            return ResponseEntity
                .badRequest()
                .body(Map.of(
                    "message",
                    "A team must contain 3–5 total members, including the leader."
                ));
        }

        User leader = registration.getUser();

        Team team = new Team();

        team.setRegistration(registration);

        /*
         * Important fix:
         * The database requires teams.leader_user_id.
         */
        team.setLeaderUser(leader);

        team.setName(request.teamName().trim());
        team.setCode(generateUniqueTeamCode());
        team.setStatus("LOCKED");
        team.setLockedAt(Instant.now());

        for (TeamRequest.Member memberRequest : request.members()) {

            validateMember(memberRequest);

            TeamMember member = new TeamMember();

            member.setFullName(
                memberRequest.fullName().trim()
            );

            member.setEmail(
                memberRequest.email()
                    .trim()
                    .toLowerCase(Locale.ROOT)
            );

            member.setDateOfBirth(
                memberRequest.dateOfBirth()
            );

            member.setCountry(
                memberRequest.country().trim()
            );

            member.setGuardianConsent(
                memberRequest.guardianConsent()
            );

            team.addMember(member);
        }

        Team savedTeam = teamRepository.save(team);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(Map.of(
                "teamCode", savedTeam.getCode(),
                "memberCount", savedTeam.getMembers().size() + 1,
                "message", "Team created successfully."
            ));
    }

    private void validateMember(
        TeamRequest.Member memberRequest
    ) {

        int age = Period.between(
            memberRequest.dateOfBirth(),
            LocalDate.now(Clock.systemUTC())
        ).getYears();

        if (age < 10 || age > 35) {
            throw new IllegalArgumentException(
                "Every team member must be aged 10–35."
            );
        }

        if (age < 18 && !memberRequest.guardianConsent()) {
            throw new IllegalArgumentException(
                "Guardian consent is required for every team member under 18."
            );
        }
    }

    private Registration findRegistration(
        Authentication authentication
    ) {

        if (authentication == null ||
            authentication.getName() == null) {

            throw new IllegalArgumentException(
                "Authenticated participant was not found."
            );
        }

        User user = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Authenticated user was not found."
                )
            );

        return registrationRepository
            .findByUser(user)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Registration was not found."
                )
            );
    }

    private String generateUniqueTeamCode() {

        String teamCode;

        do {
            teamCode =
                "TEAM-" +
                UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);

        } while (teamRepository.existsByCode(teamCode));

        return teamCode;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(
        IllegalArgumentException exception
    ) {

        return ResponseEntity
            .badRequest()
            .body(Map.of(
                "message", exception.getMessage()
            ));
    }
}