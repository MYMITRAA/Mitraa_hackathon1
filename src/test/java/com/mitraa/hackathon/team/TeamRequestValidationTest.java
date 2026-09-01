package com.mitraa.hackathon.team;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private TeamRequest.Member member(String email) {
        return new TeamRequest.Member(
                "Eligible Member",
                email,
                LocalDate.of(2005, 1, 1),
                "India",
                false
        );
    }

    @Test
    void acceptsThreeToFivePeopleIncludingLeader() {
        var minimumTeam = new TeamRequest(
                "Minimum Team",
                List.of(member("two@example.com"), member("three@example.com"))
        );
        var maximumTeam = new TeamRequest(
                "Maximum Team",
                List.of(
                        member("two@example.com"),
                        member("three@example.com"),
                        member("four@example.com"),
                        member("five@example.com")
                )
        );

        assertThat(validator.validate(minimumTeam)).isEmpty();
        assertThat(validator.validate(maximumTeam)).isEmpty();
    }

    @Test
    void rejectsTeamsOutsidePublishedSize() {
        var tooSmall = new TeamRequest("Too Small", List.of(member("two@example.com")));
        var tooLarge = new TeamRequest(
                "Too Large",
                List.of(
                        member("two@example.com"),
                        member("three@example.com"),
                        member("four@example.com"),
                        member("five@example.com"),
                        member("six@example.com")
                )
        );

        assertThat(validator.validate(tooSmall)).isNotEmpty();
        assertThat(validator.validate(tooLarge)).isNotEmpty();
    }

    @Test
    void rejectsInvalidMemberDetails() {
        var invalid = new TeamRequest(
                "Team",
                List.of(
                        new TeamRequest.Member("", "not-an-email", LocalDate.now().plusDays(1), "", false),
                        member("three@example.com")
                )
        );

        assertThat(validator.validate(invalid)).hasSizeGreaterThanOrEqualTo(4);
    }
}
