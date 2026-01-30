package org.AI.panda.auth.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.AI.panda.auth.service.ShareLinkService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ShareTokenFilter extends OncePerRequestFilter {

    private final ShareLinkService shareLinkService;

    public ShareTokenFilter(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = readShareToken(request);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        ShareLinkService.ResolvedShare resolved = shareLinkService.resolve(token);
        if (resolved == null) {
            writeUnauthorized(response, "分享链接无效或已过期");
            return;
        }

        request.setAttribute(UserIdResolver.ATTR_SHARE_OWNER_USER_ID, resolved.ownerUserId());
        request.setAttribute(UserIdResolver.ATTR_SHARE_SESSION_ID, resolved.sessionId());
        filterChain.doFilter(request, response);
    }

    private String readShareToken(HttpServletRequest request) {
        String token = request.getParameter("shareToken");
        if (token != null && !token.isBlank()) return token;
        token = request.getHeader("X-Share-Token");
        if (token != null && !token.isBlank()) return token;
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        String safeMsg = message == null ? "" : message.replace("\"", "\\\"");
        String body = "{\"code\":401,\"message\":\"" + safeMsg + "\",\"data\":null}";
        response.getWriter().write(body);
    }
}
