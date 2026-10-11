package com.tailorresume.tailor;

import com.tailorresume.billing.TailoringUsage;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Supplies the quota check with the row count it needs. */
@Component
public class TailoringUsageCounter implements TailoringUsage {

    private final TailoredResumeRepository tailored;

    public TailoringUsageCounter(TailoredResumeRepository tailored) {
        this.tailored = tailored;
    }

    @Override
    public long countForPeriod(Long userId, Instant periodStart) {
        return tailored.countByUserIdAndBillingPeriodStart(userId, periodStart);
    }
}
