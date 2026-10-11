package com.tailorresume.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link JpaSpecificationExecutor} is here for the admin user search, which
 * filters on any combination of free text, active flag and role.
 *
 * <p>A single {@code @Query} with {@code :param is null or ...} branches would
 * read more simply but makes Hibernate infer the type of a null enum
 * parameter, which it does differently on H2 and PostgreSQL. A specification
 * builds only the predicates that are actually present, so there is no null to
 * type.
 */
public interface UserRepository extends JpaRepository<UserAccount, Long>,
        JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    long countByActive(boolean active);

    long countByRole(Role role);

    long countByCreatedAtAfter(Instant cutoff);

    /** Backs the "new accounts" strip on the admin overview. */
    List<UserAccount> findTop8ByOrderByCreatedAtDesc();
}
