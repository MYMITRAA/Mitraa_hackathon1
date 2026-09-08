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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /*
     * =========================================================
     * GET CURRENT PARTICIPANT TEAM
     * =========================================================
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyTeam(
        Authentication authentication
    ) {

        Registration registration =
            findRegistration(authentication);

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

        Map<String, Object> response =
            new LinkedHashMap<>();

        response.put("configured", true);
        response.put("teamName", team.getName());
        response.put("teamCode", team.getCode());
        response.put("status", team.getStatus());

        /*
         * team.getMembers() contains additional members.
         * +1 represents the registered team leader.
         */
        response.put(
            "memberCount",
            team.getMembers().size() + 1
        );

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

    /*
     * =========================================================
     * CREATE TEAM
     * =========================================================
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> createTeam(
        Authentication authentication,
        @Valid @RequestBody TeamRequest request
    ) {

        Registration registration =
            findRegistration(authentication);

        /*
         * Only participants who registered using TEAM
         * participation type can create a squad.
         */
        if (
            registration.getParticipationType()
                != ParticipationType.TEAM
        ) {

            return ResponseEntity
                .badRequest()
                .body(
                    Map.of(
                        "message",
                        "Only team registrations can create a team."
                    )
                );
        }

        /*
         * One registration can create only one team.
         */
        if (
            teamRepository
                .findByRegistration(registration)
                .isPresent()
        ) {

            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                    Map.of(
                        "message",
                        "A team already exists for this registration."
                    )
                );
        }

        /*
         * Registered participant = Team Leader.
         *
         * Request must therefore contain:
         *
         * 2 additional members -> total 3
         * 3 additional members -> total 4
         * 4 additional members -> total 5
         */
        int totalMemberCount =
            request.members().size() + 1;

        if (
            totalMemberCount < 3 ||
            totalMemberCount > 5
        ) {

            return ResponseEntity
                .badRequest()
                .body(
                    Map.of(
                        "message",
                        "A team must contain 3–5 total members, including the leader."
                    )
                );
        }

        User leader = registration.getUser();

        /*
         * =====================================================
         * DUPLICATE EMAIL VALIDATION
         * =====================================================
         *
         * HashSet allows us to detect duplicate email addresses
         * before saving anything to the database.
         *
         * Comparison is case-insensitive because every email is
         * normalized to lowercase.
         */
        Set<String> memberEmails = new HashSet<>();

        String leaderEmail =
            normalizeEmail(leader.getEmail());

        for (
            TeamRequest.Member memberRequest :
            request.members()
        ) {

            validateMember(memberRequest);

            String memberEmail =
                normalizeEmail(
                    memberRequest.email()
                );

            /*
             * Prevent team leader from adding themselves
             * again as a normal team member.
             */
            if (memberEmail.equals(leaderEmail)) {

                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                        Map.of(
                            "code",
                            "LEADER_EMAIL_DUPLICATE",

                            "message",
                            "The team leader is already included in the team and cannot be added again."
                        )
                    );
            }

            /*
             * Prevent same member email from appearing
             * multiple times inside the submitted team.
             */
            if (!memberEmails.add(memberEmail)) {

                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                        Map.of(
                            "code",
                            "DUPLICATE_TEAM_MEMBER",

                            "message",
                            "The email address " +
                                memberEmail +
                                " is already added to this team."
                        )
                    );
            }
        }

        /*
         * =====================================================
         * CREATE TEAM
         * =====================================================
         */

        Team team = new Team();

        team.setRegistration(registration);

        /*
         * Database requires teams.leader_user_id.
         */
        team.setLeaderUser(leader);

        team.setName(
            request.teamName().trim()
        );

        team.setCode(
            generateUniqueTeamCode()
        );

        team.setStatus("LOCKED");

        team.setLockedAt(
            Instant.now()
        );

        /*
         * =====================================================
         * ADD MEMBERS
         * =====================================================
         */
        for (
            TeamRequest.Member memberRequest :
            request.members()
        ) {

            TeamMember member =
                new TeamMember();

            member.setFullName(
                memberRequest
                    .fullName()
                    .trim()
            );

            member.setEmail(
                normalizeEmail(
                    memberRequest.email()
                )
            );

            member.setDateOfBirth(
                memberRequest.dateOfBirth()
            );

            member.setCountry(
                memberRequest
                    .country()
                    .trim()
            );

            member.setGuardianConsent(
                memberRequest.guardianConsent()
            );

            team.addMember(member);
        }

        /*
         * =====================================================
         * SAVE TEAM
         * =====================================================
         */
        Team savedTeam =
            teamRepository.save(team);

        /*
         * =====================================================
         * SUCCESS RESPONSE
         * =====================================================
         */
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                Map.of(
                    "teamCode",
                    savedTeam.getCode(),

                    "memberCount",
                    savedTeam
                        .getMembers()
                        .size() + 1,

                    "message",
                    "Team created successfully."
                )
            );
    }

    /*
     * =========================================================
     * MEMBER VALIDATION
     * =========================================================
     */
    private void validateMember(
        TeamRequest.Member memberRequest
    ) {

        if (memberRequest == null) {

            throw new IllegalArgumentException(
                "Team member information is required."
            );
        }

        /*
         * Validate member name.
         */
        if (
            memberRequest.fullName() == null ||
            memberRequest
                .fullName()
                .trim()
                .isEmpty()
        ) {

            throw new IllegalArgumentException(
                "Every team member must have a name."
            );
        }

        /*
         * Validate email.
         */
        if (
            memberRequest.email() == null ||
            memberRequest
                .email()
                .trim()
                .isEmpty()
        ) {

            throw new IllegalArgumentException(
                "Every team member must have an email address."
            );
        }

        /*
         * Validate DOB.
         */
        if (
            memberRequest.dateOfBirth() == null
        ) {

            throw new IllegalArgumentException(
                "Date of birth is required for every team member."
            );
        }

        /*
         * Validate country.
         */
        if (
            memberRequest.country() == null ||
            memberRequest
                .country()
                .trim()
                .isEmpty()
        ) {

            throw new IllegalArgumentException(
                "Country is required for every team member."
            );
        }

        /*
         * Calculate age using UTC.
         */
        int age =
            Period.between(
                memberRequest.dateOfBirth(),
                LocalDate.now(
                    Clock.systemUTC()
                )
            ).getYears();

        /*
         * Hackathon eligibility:
         * 10 through 35 years.
         */
        if (
            age < 10 ||
            age > 35
        ) {

            throw new IllegalArgumentException(
                "Every team member must be aged 10–35."
            );
        }

        /*
         * Guardian consent required for minors.
         */
        if (
            age < 18 &&
            !memberRequest.guardianConsent()
        ) {

            throw new IllegalArgumentException(
                "Guardian consent is required for every team member under 18."
            );
        }
    }

    /*
     * =========================================================
     * NORMALIZE EMAIL
     * =========================================================
     */
    private String normalizeEmail(
        String email
    ) {

        if (email == null) {
            return "";
        }

        return email
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    /*
     * =========================================================
     * FIND AUTHENTICATED REGISTRATION
     * =========================================================
     */
    private Registration findRegistration(
        Authentication authentication
    ) {

        if (
            authentication == null ||
            authentication.getName() == null
        ) {

            throw new IllegalArgumentException(
                "Authenticated participant was not found."
            );
        }

        User user =
            userRepository
                .findByEmailIgnoreCase(
                    authentication.getName()
                )
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Authenticated user was not found."
                        )
                );

        return registrationRepository
            .findByUser(user)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Registration was not found."
                    )
            );
    }

    /*
     * =========================================================
     * GENERATE UNIQUE TEAM CODE
     * =========================================================
     */
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

        } while (
            teamRepository
                .existsByCode(teamCode)
        );

        return teamCode;
    }

    /*
     * =========================================================
     * HANDLE VALIDATION ERRORS
     * =========================================================
     */
    @ExceptionHandler(
        IllegalArgumentException.class
    )
    public ResponseEntity<?> handleIllegalArgument(
        IllegalArgumentException exception
    ) {

        return ResponseEntity
            .badRequest()
            .body(
                Map.of(
                    "code",
                    "VALIDATION_ERROR",

                    "message",
                    exception.getMessage()
                )
            );
    }
}