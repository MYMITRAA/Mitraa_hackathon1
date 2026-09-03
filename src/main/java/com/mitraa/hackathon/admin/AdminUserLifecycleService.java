package com.mitraa.hackathon.admin;

import com.mitraa.hackathon.user.Role;
import com.mitraa.hackathon.user.User;
import com.mitraa.hackathon.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class AdminUserLifecycleService {

    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public AdminUserLifecycleService(UserRepository users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Transactional
    public User setEnabled(Long userId, boolean enabled, Authentication authentication) {
        User target = findTarget(userId);
        requireManagePermission(target, authentication, enabled ? "enable" : "disable");

        if (!enabled && target.getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new IllegalArgumentException("You cannot disable your own active account.");
        }

        target.setEnabled(enabled);
        return users.save(target);
    }

    @Transactional
    public void permanentlyDelete(Long userId, Authentication authentication) {
        User target = findTarget(userId);
        requireManagePermission(target, authentication, "delete");

        if (target.getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new IllegalArgumentException("You cannot permanently delete your own account.");
        }

        String email = target.getEmail();

        // Records where the target acted as an administrator, jury member or author.
        jdbc.update("DELETE FROM admin_action_audit WHERE actor_user_id = ?", userId);
        jdbc.update("DELETE FROM evaluation_overrides WHERE overridden_by_user_id = ?", userId);
        jdbc.update("DELETE FROM winner_selections WHERE selected_by_user_id = ? OR approved_by_user_id = ?", userId, userId);
        jdbc.update("DELETE FROM jury_evaluation_scores WHERE evaluation_id IN (SELECT id FROM jury_evaluations WHERE jury_user_id = ?)", userId);
        jdbc.update("DELETE FROM evaluation_overrides WHERE evaluation_id IN (SELECT id FROM jury_evaluations WHERE jury_user_id = ?)", userId);
        jdbc.update("DELETE FROM jury_evaluations WHERE jury_user_id = ?", userId);
        jdbc.update("DELETE FROM jury_arena_assignments WHERE jury_user_id = ? OR assigned_by_user_id = ?", userId, userId);
        jdbc.update("DELETE FROM submission_deadlines WHERE updated_by = ?", userId);
        jdbc.update("UPDATE result_publications SET approved_by = NULL WHERE approved_by = ?", userId);
        jdbc.update("DELETE FROM support_messages WHERE author_user_id = ?", userId);
        jdbc.update("UPDATE support_tickets SET assigned_admin_id = NULL WHERE assigned_admin_id = ?", userId);
        jdbc.update("DELETE FROM support_tickets WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM team_members WHERE linked_user_id = ?", userId);

        // Participant registration, POC, evaluation, payment, invoice and team data.
        jdbc.update("DELETE FROM jury_evaluation_scores WHERE evaluation_id IN (SELECT je.id FROM jury_evaluations je JOIN submissions s ON s.id = je.submission_id JOIN registrations r ON r.id = s.registration_id WHERE r.user_id = ?)", userId);
        jdbc.update("DELETE FROM evaluation_overrides WHERE evaluation_id IN (SELECT je.id FROM jury_evaluations je JOIN submissions s ON s.id = je.submission_id JOIN registrations r ON r.id = s.registration_id WHERE r.user_id = ?)", userId);
        jdbc.update("DELETE FROM jury_evaluations WHERE submission_id IN (SELECT s.id FROM submissions s JOIN registrations r ON r.id = s.registration_id WHERE r.user_id = ?)", userId);
        jdbc.update("DELETE FROM submission_rankings WHERE submission_id IN (SELECT s.id FROM submissions s JOIN registrations r ON r.id = s.registration_id WHERE r.user_id = ?)", userId);
        jdbc.update("DELETE FROM winner_selections WHERE submission_id IN (SELECT s.id FROM submissions s JOIN registrations r ON r.id = s.registration_id WHERE r.user_id = ?)", userId);
        jdbc.update("DELETE FROM certificates WHERE user_id = ? OR submission_id IN (SELECT s.id FROM submissions s JOIN registrations r ON r.id = s.registration_id WHERE r.user_id = ?)", userId, userId);
        jdbc.update("DELETE FROM submission_files WHERE submission_id IN (SELECT s.id FROM submissions s JOIN registrations r ON r.id = s.registration_id WHERE r.user_id = ?)", userId);
        jdbc.update("DELETE FROM submission_versions WHERE submission_id IN (SELECT s.id FROM submissions s JOIN registrations r ON r.id = s.registration_id WHERE r.user_id = ?)", userId);
        jdbc.update("DELETE FROM submissions WHERE registration_id IN (SELECT id FROM registrations WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM team_members WHERE team_id IN (SELECT id FROM teams WHERE leader_user_id = ? OR registration_id IN (SELECT id FROM registrations WHERE user_id = ?))", userId, userId);
        jdbc.update("DELETE FROM teams WHERE leader_user_id = ? OR registration_id IN (SELECT id FROM registrations WHERE user_id = ?)", userId, userId);
        jdbc.update("DELETE FROM invoices WHERE payment_id IN (SELECT p.id FROM payments p JOIN registrations r ON r.id = p.registration_id WHERE r.user_id = ?)", userId);
        jdbc.update("DELETE FROM payment_refunds WHERE payment_id IN (SELECT p.id FROM payments p JOIN registrations r ON r.id = p.registration_id WHERE r.user_id = ?)", userId);
        jdbc.update("DELETE FROM payments WHERE registration_id IN (SELECT id FROM registrations WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM guardian_consents WHERE registration_id IN (SELECT id FROM registrations WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM registrations WHERE user_id = ?", userId);

        // Account-linked and email-addressed records.
        jdbc.update("DELETE FROM notification_outbox WHERE LOWER(recipient) = LOWER(?)", email);
        jdbc.update("DELETE FROM account_codes WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM oauth_identities WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private User findTarget(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found."));
    }

    private void requireManagePermission(User target, Authentication authentication, String action) {
        boolean superAdmin = hasRole(authentication, "ROLE_SUPER_ADMIN");
        boolean admin = superAdmin || hasRole(authentication, "ROLE_ADMIN");

        if (!admin) {
            throw new IllegalArgumentException("Administrator access is required.");
        }

        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("A Super Admin account cannot be " + action + "d.");
        }

        if (!superAdmin && target.getRole() != Role.PARTICIPANT) {
            throw new IllegalArgumentException("An Admin can manage participant accounts only.");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
