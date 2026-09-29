package com.bolt.checkout.service;

import com.bolt.checkout.entity.User;
import com.bolt.checkout.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Records failed login-code attempts in a transaction of its own.
 *
 * <p>This is a separate bean, and it commits independently of the caller, for a
 * specific reason: {@code AuthService.verifyOtp} is transactional and throws when the
 * code is wrong, so anything it writes to the attempt counter would be rolled back along
 * with the failed request. The counter would then stay at zero forever and the
 * brute-force limit would never engage - the control would look present in the code and
 * do nothing in production.
 *
 * <p>{@code REQUIRES_NEW} also means the caller must not have written to the same
 * {@code users} row before calling in, or the two transactions would deadlock waiting
 * on each other's lock. Callers only ever read the user first.
 */
@Service
public class OtpAttemptService {

    private final UserRepository userRepository;
    private final int maxAttempts;
    private final long lockoutMinutes;

    public OtpAttemptService(UserRepository userRepository,
                             @Value("${otp.max-attempts:5}") int maxAttempts,
                             @Value("${otp.lockout-minutes:15}") long lockoutMinutes) {
        this.userRepository = userRepository;
        this.maxAttempts = maxAttempts;
        this.lockoutMinutes = lockoutMinutes;
    }

    /**
     * Increments the failure counter and locks the account once it reaches the limit.
     *
     * @return the failure count after this attempt
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailure(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User disappeared while recording a failed OTP: " + userId));

        int attempts = user.getOtpFailedAttempts() + 1;
        user.setOtpFailedAttempts(attempts);
        if (attempts >= maxAttempts) {
            user.setOtpLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
        }
        userRepository.saveAndFlush(user);
        return attempts;
    }
}
