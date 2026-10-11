package com.tailorresume.common;

/**
 * One "how many rows does this user own" pair.
 *
 * <p>Exists so the admin user list can fetch every owner's totals in a single
 * grouped query instead of one count per row. A page of twenty accounts would
 * otherwise be forty-one round trips.
 */
public interface OwnerCount {

    Long getUserId();

    long getTotal();
}
