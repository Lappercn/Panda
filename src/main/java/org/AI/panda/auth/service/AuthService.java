package org.AI.panda.auth.service;

import org.AI.panda.auth.model.EmailVerificationCode;
import org.AI.panda.auth.model.UserAccount;
import org.AI.panda.auth.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AuthService {
    private final UserAccountRepository userRepository;
    private final PasswordService passwordService;
    private final VerificationCodeService codeService;
    private final SessionService sessionService;

    public record AuthResult(String userId, String email, String sessionToken) {
    }

    public AuthService(
            UserAccountRepository userRepository,
            PasswordService passwordService,
            VerificationCodeService codeService,
            SessionService sessionService
    ) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.codeService = codeService;
        this.sessionService = sessionService;
    }

    public void sendRegisterCode(String email) {
        String emailLower = normalizeEmail(email);
        if (userRepository.existsByEmailLower(emailLower)) {
            throw new IllegalArgumentException("该邮箱已注册");
        }
        codeService.sendCode(email, EmailVerificationCode.Purpose.REGISTER);
    }

    public void sendLoginCode(String email) {
        String emailLower = normalizeEmail(email);
        if (!userRepository.existsByEmailLower(emailLower)) {
            throw new IllegalArgumentException("该邮箱未注册");
        }
        codeService.sendCode(email, EmailVerificationCode.Purpose.LOGIN);
    }

    public void sendResetPasswordCode(String email) {
        String emailLower = normalizeEmail(email);
        if (!userRepository.existsByEmailLower(emailLower)) {
            throw new IllegalArgumentException("该邮箱未注册");
        }
        codeService.sendCode(email, EmailVerificationCode.Purpose.RESET_PASSWORD);
    }

    public AuthResult register(String email, String password, String code, String ip, String userAgent) {
        String emailLower = normalizeEmail(email);
        if (userRepository.existsByEmailLower(emailLower)) {
            throw new IllegalArgumentException("该邮箱已注册");
        }
        validatePassword(password);
        boolean ok = codeService.verifyAndConsume(email, EmailVerificationCode.Purpose.REGISTER, code);
        if (!ok) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        Date now = new Date();
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setEmailLower(emailLower);
        user.setPasswordHash(passwordService.hash(password));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setLastLoginAt(now);

        UserAccount saved = userRepository.save(user);
        String token = sessionService.createSession(saved.getId(), ip, userAgent);
        return new AuthResult(saved.getId(), saved.getEmail(), token);
    }

    public AuthResult loginWithPassword(String email, String password, String ip, String userAgent) {
        String emailLower = normalizeEmail(email);
        UserAccount user = userRepository.findByEmailLower(emailLower)
                .orElseThrow(() -> new IllegalArgumentException("邮箱或密码错误"));

        if (!passwordService.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }

        Date now = new Date();
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        String token = sessionService.createSession(user.getId(), ip, userAgent);
        return new AuthResult(user.getId(), user.getEmail(), token);
    }

    public AuthResult loginWithCode(String email, String code, String ip, String userAgent) {
        String emailLower = normalizeEmail(email);
        UserAccount user = userRepository.findByEmailLower(emailLower)
                .orElseThrow(() -> new IllegalArgumentException("该邮箱未注册"));

        boolean ok = codeService.verifyAndConsume(email, EmailVerificationCode.Purpose.LOGIN, code);
        if (!ok) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        Date now = new Date();
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        String token = sessionService.createSession(user.getId(), ip, userAgent);
        return new AuthResult(user.getId(), user.getEmail(), token);
    }

    public void resetPassword(String email, String newPassword, String code) {
        String emailLower = normalizeEmail(email);
        UserAccount user = userRepository.findByEmailLower(emailLower)
                .orElseThrow(() -> new IllegalArgumentException("该邮箱未注册"));

        validatePassword(newPassword);
        boolean ok = codeService.verifyAndConsume(email, EmailVerificationCode.Purpose.RESET_PASSWORD, code);
        if (!ok) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        Date now = new Date();
        user.setPasswordHash(passwordService.hash(newPassword));
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码至少 8 位");
        }
        if (password.length() > 128) {
            throw new IllegalArgumentException("密码过长");
        }
    }
}
