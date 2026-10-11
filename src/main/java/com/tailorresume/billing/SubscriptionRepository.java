package com.tailorresume.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Long userId);

    /**
     * Every subscription for a page of users, in one query.
     *
     * <p>The admin user table shows each account's plan. Looking them up one
     * at a time is a round trip per row -- barely noticeable against a local
     * database, and seconds against a managed one a few hundred miles away.
     *
     * <p>The {@code join fetch} is not optional. {@code user} is lazy and this
     * entity uses field access, so reading {@code getUser().getId()} off the
     * returned rows would initialize each proxy one at a time and reintroduce
     * exactly the per-row query this method exists to remove.
     */
    @Query("select s from Subscription s join fetch s.user where s.user.id in :userIds")
    List<Subscription> findAllForUsers(@Param("userIds") Collection<Long> userIds);

    /** Admin overview: how many accounts sit on each plan. */
    long countByPlan(Plan plan);
}
