package org.AI.panda.auth.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.AI.panda.auth.model.EmailVerificationCode;
import org.AI.panda.auth.repository.UserAccountRepository;
import org.AI.panda.auth.service.AuthService;
import org.AI.panda.auth.service.SessionService;
import org.AI.panda.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionService sessionService;
    private final UserAccountRepository userAccountRepository;

    @Value("${panda.auth.session.cookie-name:PANDA_SESSION}")
    private String cookieName;

    @Value("${panda.auth.email-code.ttl-minutes:10}")
    private int codeTtlMinutes;

    public AuthController(AuthService authService, SessionService sessionService, UserAccountRepository userAccountRepository) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.userAccountRepository = userAccountRepository;
    }

    public record SendCodeRequest(@Email @NotBlank String email, @NotBlank String purpose) {
    }

    @PostMapping("/send-code")
    public Result<Map<String, Object>> sendCode(@RequestBody @Validated SendCodeRequest req) {
        EmailVerificationCode.Purpose purpose = parsePurpose(req.purpose());
        switch (purpose) {
            case REGISTER -> authService.sendRegisterCode(req.email());
            case LOGIN -> authService.sendLoginCode(req.email());
            case RESET_PASSWORD -> authService.sendResetPasswordCode(req.email());
        }
        return Result.success(Map.of("ttlMinutes", codeTtlMinutes));
    }

    public record RegisterRequest(@Email @NotBlank String email, @NotBlank String password, @NotBlank String code) {
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody @Validated RegisterRequest req,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        AuthService.AuthResult result = authService.register(
                req.email(),
                req.password(),
                req.code(),
                resolveClientIp(request),
                request.getHeader("User-Agent")
        );
        setSessionCookie(response, result.sessionToken());
        return Result.success(Map.of("userId", result.userId(), "email", result.email()));
    }

    public record LoginPasswordRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    @PostMapping("/login/password")
    public Result<Map<String, Object>> loginWithPassword(@RequestBody @Validated LoginPasswordRequest req,
                                                         HttpServletRequest request,
                                                         HttpServletResponse response) {
        AuthService.AuthResult result = authService.loginWithPassword(
                req.email(),
                req.password(),
                resolveClientIp(request),
                request.getHeader("User-Agent")
        );
        setSessionCookie(response, result.sessionToken());
        return Result.success(Map.of("userId", result.userId(), "email", result.email()));
    }

    public record LoginCodeRequest(@Email @NotBlank String email, @NotBlank String code) {
    }

    @PostMapping("/login/code")
    public Result<Map<String, Object>> loginWithCode(@RequestBody @Validated LoginCodeRequest req,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        AuthService.AuthResult result = authService.loginWithCode(
                req.email(),
                req.code(),
                resolveClientIp(request),
                request.getHeader("User-Agent")
        );
        setSessionCookie(response, result.sessionToken());
        return Result.success(Map.of("userId", result.userId(), "email", result.email()));
    }

    public record ResetPasswordRequest(@Email @NotBlank String email, @NotBlank String newPassword, @NotBlank String code) {
    }

    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@RequestBody @Validated ResetPasswordRequest req) {
        authService.resetPassword(req.email(), req.newPassword(), req.code());
        return Result.success(null);
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Object v = request.getAttribute(AuthUserIdFilter.ATTR_AUTH_USER_ID);
        if (!(v instanceof String userId) || userId.isBlank()) {
            return Result.error(401, "未登录");
        }
        return userAccountRepository.findById(userId)
                .map(u -> Result.success(Map.<String, Object>of("userId", u.getId(), "email", u.getEmail())))
                .orElseGet(() -> Result.error(401, "未登录"));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = readCookie(request, cookieName);
        if (token != null && !token.isBlank()) {
            sessionService.deleteSession(token);
        }
        clearSessionCookie(response);
        return Result.success(null);
    }

    private void setSessionCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(sessionService.getTtlDays()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private EmailVerificationCode.Purpose parsePurpose(String p) {
        if (p == null) throw new IllegalArgumentException("purpose 不能为空");
        return switch (p.trim().toUpperCase()) {
            case "REGISTER" -> EmailVerificationCode.Purpose.REGISTER;
            case "LOGIN" -> EmailVerificationCode.Purpose.LOGIN;
            case "RESET_PASSWORD", "RESET" -> EmailVerificationCode.Purpose.RESET_PASSWORD;
            default -> throw new IllegalArgumentException("不支持的 purpose");
        };
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || name == null || name.isBlank()) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            for (String part : parts) {
                String ip = part == null ? "" : part.trim();
                if (!ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
            }
        }

        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank() && !"unknown".equalsIgnoreCase(xri.trim())) {
            return xri.trim();
        }

        String forwarded = request.getHeader("Forwarded");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] items = forwarded.split(";");
            for (String item : items) {
                String kv = item.trim();
                if (kv.regionMatches(true, 0, "for=", 0, 4)) {
                    String v = kv.substring(4).trim();
                    if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                        v = v.substring(1, v.length() - 1);
                    }
                    int commaIdx = v.indexOf(',');
                    if (commaIdx >= 0) v = v.substring(0, commaIdx).trim();
                    if (v.startsWith("[")) {
                        int end = v.indexOf(']');
                        if (end > 1) v = v.substring(1, end);
                    } else {
                        int colonIdx = v.indexOf(':');
                        if (colonIdx > 0) v = v.substring(0, colonIdx);
                    }
                    if (!v.isBlank() && !"unknown".equalsIgnoreCase(v)) {
                        return v;
                    }
                }
            }
        }

        return request.getRemoteAddr();
    }
}
