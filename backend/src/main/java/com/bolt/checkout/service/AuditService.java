package com.bolt.checkout.service;

import com.bolt.checkout.entity.AuditLog;
import com.bolt.checkout.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes security-relevant events to {@code audit_logs}.
 *
 * <p>Two deliberate constraints:
 *
 * <ul>
 *   <li><b>Never fatal.</b> A failed audit insert is logged and swallowed. Losing an
 *       audit row must never turn a successful login or checkout into a 500.
 *   <li><b>Never secret.</b> Nothing here accepts an OTP, a password hash or a session
 *       token, so those cannot leak into the audit trail by accident.
 * </ul>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** Stable event names, so the audit trail can be queried without matching free text. */
    public static final String EVENT_REGISTRATION = "REGISTRATION";
    public static final String EVENT_OTP_VERIFY = "OTP_VERIFY";
    public static final String EVENT_CHECKOUT = "CHECKOUT";

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";

    /** Keeps a detail string inside the 500-character column even if a caller passes something long. */
    private static final int MAX_DETAIL_LENGTH = 500;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Commits independently of the caller ({@code REQUIRES_NEW}).
     *
     * <p>That is the whole point for failure events: a failed login throws, which rolls
     * back the caller's transaction, and a failed login is precisely the event an audit
     * trail exists to capture. Sharing the caller's transaction would record only the
     * events that succeeded, which is the opposite of what is wanted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String event, String outcome, String email, Long userId, String detail) {
        try {
            AuditLog entry = new AuditLog();
            entry.setEvent(event);
            entry.setOutcome(outcome);
            entry.setEmail(truncate(email, 255));
            entry.setUserId(userId);
            entry.setDetail(truncate(detail, MAX_DETAIL_LENGTH));
            entry.setIpAddress(currentClientIp());
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            // Swallowed on purpose - see the class comment.
            log.warn("Failed to write audit log event={} outcome={}", event, outcome, ex);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Resolves the caller address for the current request, or {@code null} when called
     * outside a request (unit tests, scheduled work).
     */
    private static String currentClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            return truncate(resolveClientIp(request), 45);
        }
        return null;
    }

    /**
     * Honours {@code X-Forwarded-For} because the app sits behind Vercel and Render, so
     * {@code getRemoteAddr()} is the proxy rather than the user.
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Left-most entry is the original client.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
