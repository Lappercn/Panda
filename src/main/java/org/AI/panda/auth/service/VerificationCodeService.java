package org.AI.panda.auth.service;

import org.AI.panda.auth.model.EmailVerificationCode;
import org.AI.panda.auth.repository.EmailVerificationCodeRepository;
import org.AI.panda.auth.util.HashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;

@Service
public class VerificationCodeService {
    private final EmailVerificationCodeRepository codeRepository;
    private final EmailService emailService;

    @Value("${panda.auth.email-code.ttl-minutes:10}")
    private int ttlMinutes;

    @Value("${panda.auth.email-code.min-send-interval-seconds:60}")
    private int minSendIntervalSeconds;

    @Value("${panda.auth.email-code.max-per-hour:5}")
    private int maxPerHour;

    private final SecureRandom random = new SecureRandom();

    public VerificationCodeService(EmailVerificationCodeRepository codeRepository, EmailService emailService) {
        this.codeRepository = codeRepository;
        this.emailService = emailService;
    }

    public void sendCode(String email, EmailVerificationCode.Purpose purpose) {
        String emailLower = normalizeEmail(email);
        Date now = new Date();

        codeRepository.findFirstByEmailLowerAndPurposeAndUsedOrderByCreatedAtDesc(emailLower, purpose, false)
                .ifPresent(last -> {
                    long seconds = Duration.between(last.getCreatedAt().toInstant(), now.toInstant()).getSeconds();
                    if (seconds < minSendIntervalSeconds) {
                        throw new IllegalArgumentException("发送过于频繁，请稍后再试");
                    }
                });

        Date oneHourAgo = new Date(now.getTime() - Duration.ofHours(1).toMillis());
        long count = codeRepository.countByEmailLowerAndPurposeAndCreatedAtAfter(emailLower, purpose, oneHourAgo);
        if (count >= maxPerHour) {
            throw new IllegalArgumentException("发送次数过多，请稍后再试");
        }

        String code = generateSixDigits();
        EmailVerificationCode entity = new EmailVerificationCode();
        entity.setEmailLower(emailLower);
        entity.setPurpose(purpose);
        entity.setUsed(false);
        entity.setCreatedAt(now);
        entity.setExpiresAt(new Date(now.getTime() + Duration.ofMinutes(ttlMinutes).toMillis()));
        entity.setCodeHash(hashCode(emailLower, purpose, code));

        EmailVerificationCode saved = codeRepository.save(entity);
        try {
            emailService.sendVerificationCode(email, purposeLabel(purpose), code, ttlMinutes);
        } catch (Exception e) {
            codeRepository.deleteById(saved.getId());
            throw e;
        }
    }

    public boolean verifyAndConsume(String email, EmailVerificationCode.Purpose purpose, String code) {
        String emailLower = normalizeEmail(email);
        Date now = new Date();

        EmailVerificationCode latest = codeRepository
                .findFirstByEmailLowerAndPurposeAndUsedOrderByCreatedAtDesc(emailLower, purpose, false)
                .orElse(null);
        if (latest == null) return false;
        if (latest.getExpiresAt() == null || latest.getExpiresAt().before(now)) return false;

        String expectedHash = latest.getCodeHash();
        String providedHash = hashCode(emailLower, purpose, code);
        if (!providedHash.equals(expectedHash)) return false;

        latest.setUsed(true);
        codeRepository.save(latest);
        return true;
    }

    public int getTtlMinutes() {
        return ttlMinutes;
    }

    private String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase();
    }

    private String generateSixDigits() {
        int n = random.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    private String hashCode(String emailLower, EmailVerificationCode.Purpose purpose, String code) {
        if (code == null) code = "";
        return HashUtil.sha256Hex(code.trim() + ":" + emailLower + ":" + purpose.name());
    }

    private String purposeLabel(EmailVerificationCode.Purpose purpose) {
        return switch (purpose) {
            case REGISTER -> "注册";
            case LOGIN -> "登录";
            case RESET_PASSWORD -> "找回密码";
        };
    }
}
