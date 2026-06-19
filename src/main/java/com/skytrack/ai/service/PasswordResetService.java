package com.skytrack.ai.service;

import com.skytrack.ai.dto.PasswordResetConfirmRequest;
import com.skytrack.ai.entity.PasswordResetCode;
import com.skytrack.ai.entity.User;
import com.skytrack.ai.exception.ExternalServiceException;
import com.skytrack.ai.repository.PasswordResetCodeRepository;
import com.skytrack.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository resetCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemSettingsService settingsService;
    private final JavaMailSender mailSender;

    @Value("${app.password-reset.from-email:}")
    private String fromEmail;

    @Value("${app.password-reset.code-expiration-minutes:10}")
    private long expirationMinutes;

    @Value("${app.password-reset.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${app.password-reset.max-attempts:5}")
    private int maxAttempts;

    @Transactional
    public void requestCode(String requestedEmail) {
        String email = normalizeEmail(requestedEmail);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) return;

        PasswordResetCode current = resetCodeRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElse(null);
        if (current != null && current.getCreatedAt() != null
                && current.getCreatedAt().plusSeconds(resendCooldownSeconds).isAfter(LocalDateTime.now())) {
            return;
        }
        if (current != null) {
            current.setUsed(true);
            resetCodeRepository.save(current);
        }

        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        PasswordResetCode resetCode = new PasswordResetCode();
        resetCode.setEmail(email);
        resetCode.setCodeHash(passwordEncoder.encode(code));
        resetCode.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        resetCodeRepository.save(resetCode);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromEmail != null && !fromEmail.isBlank()) message.setFrom(fromEmail.trim());
            message.setTo(user.getEmail());
            message.setSubject("SkyTrack password reset code");
            message.setText("""
                    Hello %s,

                    Your SkyTrack password reset code is: %s

                    This code expires in %d minutes. If you did not request a password reset, you can ignore this email.

                    SkyTrack AI
                    """.formatted(displayName(user), code, expirationMinutes));
            mailSender.send(message);
        } catch (MailException exception) {
            resetCodeRepository.delete(resetCode);
            throw new ExternalServiceException("Could not send the reset email. Please try again later.");
        }
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        String email = normalizeEmail(request.email());
        PasswordResetCode resetCode = resetCodeRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("Verification code is invalid or expired"));

        if (resetCode.getExpiresAt().isBefore(LocalDateTime.now())
                || resetCode.getFailedAttempts() >= maxAttempts) {
            resetCode.setUsed(true);
            resetCodeRepository.save(resetCode);
            throw new IllegalArgumentException("Verification code is invalid or expired");
        }

        if (!passwordEncoder.matches(request.code(), resetCode.getCodeHash())) {
            resetCode.setFailedAttempts(resetCode.getFailedAttempts() + 1);
            if (resetCode.getFailedAttempts() >= maxAttempts) resetCode.setUsed(true);
            resetCodeRepository.save(resetCode);
            throw new IllegalArgumentException("Verification code is invalid or expired");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Verification code is invalid or expired"));
        settingsService.validatePassword(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetCode.setUsed(true);
        resetCodeRepository.save(resetCode);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String displayName(User user) {
        return user.getName() == null || user.getName().isBlank() ? "SkyTrack member" : user.getName().trim();
    }
}
