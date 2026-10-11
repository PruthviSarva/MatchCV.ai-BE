package com.tailorresume.admin;

import com.tailorresume.auth.Emails;
import com.tailorresume.billing.SubscriptionService;
import com.tailorresume.config.AppProperties;
import com.tailorresume.user.Role;
import com.tailorresume.user.UserAccount;
import com.tailorresume.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Creates the first administrator on startup.
 *
 * <p>The first admin cannot be made through the admin console, because reaching
 * the console requires already being one. The alternative to this class is a
 * hand-written UPDATE against the users table -- the kind of step that gets run
 * once on one machine, never written down, and then cannot be reproduced on a
 * fresh environment.
 *
 * <p>Three things it deliberately will not do:
 *
 * <ul>
 *   <li>It will not run if any enabled administrator already exists, so it
 *       cannot resurrect an account that was disabled on purpose, and a restart
 *       is never a privilege-escalation event.
 *   <li>It will not change an existing account. If the configured address
 *       belongs to an ordinary user, that is reported and left alone rather
 *       than silently promoted -- an environment variable should not be able to
 *       hand out admin access to an account somebody else controls.
 *   <li>It will not run at all when the password is blank, which is how the
 *       test profile keeps an unexpected extra row out of its assertions.
 * </ul>
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.Admin config;

    public AdminBootstrap(UserRepository users,
                          SubscriptionService subscriptions,
                          PasswordEncoder passwordEncoder,
                          AppProperties properties) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.passwordEncoder = passwordEncoder;
        this.config = properties.admin();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!config.isSeedingEnabled()) {
            return;
        }
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }

        String email = Emails.normalize(config.email());
        Optional<UserAccount> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            log.warn("Not seeding an administrator: {} already belongs to a non-admin account. "
                    + "Promote it from the admin console, or point ADMIN_EMAIL somewhere else.",
                    email);
            return;
        }

        UserAccount admin = UserAccount.createWithRole(
                config.name(), email, passwordEncoder.encode(config.password()), Role.ADMIN);
        users.save(admin);
        subscriptions.startFreePlan(admin);

        // WARN rather than INFO on purpose: on a real deployment this line
        // means a known-password account now exists, and that should be hard to
        // scroll past.
        log.warn("Seeded the first administrator: {}. Change this password now -- "
                + "it came from configuration and is not a secret.", email);
    }
}
