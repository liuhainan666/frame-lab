package com.framelab.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.framelab.config.RetryProperties;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void doublesBackoffAndCapsAtMaximum() {
        RetryPolicy policy = policy(2, 30);

        assertThat(policy.delaySeconds(1)).isEqualTo(2);
        assertThat(policy.delaySeconds(2)).isEqualTo(4);
        assertThat(policy.delaySeconds(3)).isEqualTo(8);
        assertThat(policy.delaySeconds(4)).isEqualTo(16);
        assertThat(policy.delaySeconds(5)).isEqualTo(30);
        assertThat(policy.delaySeconds(6)).isEqualTo(30);
    }

    @Test
    void respectsBaseAndMaximumBounds() {
        assertThat(policy(1, 1).delaySeconds(4)).isEqualTo(1);
        assertThat(policy(60, 600).delaySeconds(1)).isEqualTo(60);
        assertThat(policy(60, 600).delaySeconds(2)).isEqualTo(120);
        assertThat(policy(60, 600).delaySeconds(5)).isEqualTo(600);
        assertThat(policy(0, 0).delaySeconds(1)).isEqualTo(1);
    }

    private RetryPolicy policy(int base, int max) {
        RetryProperties properties = new RetryProperties();
        properties.setBackoffBaseSeconds(base);
        properties.setBackoffMaxSeconds(max);
        return new RetryPolicy(properties);
    }
}
