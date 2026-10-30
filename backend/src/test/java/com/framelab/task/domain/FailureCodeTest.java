package com.framelab.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailureCodeTest {

    @Test
    void classifiesDefaultRetryabilityByFailureCode() {
        assertThat(FailureCode.MATERIAL_CHECK_FAILED.retryable()).isFalse();
        assertThat(FailureCode.GENERATION_FAILED.retryable()).isFalse();
        assertThat(FailureCode.RESULT_RENDER_FAILED.retryable()).isTrue();
        assertThat(FailureCode.STORAGE_UNAVAILABLE.retryable()).isTrue();
        assertThat(FailureCode.INTERNAL_ERROR.retryable()).isTrue();
    }

    @Test
    void mapsStagesToTheirFailureCodes() {
        assertThat(FailureCode.forStage(TaskStage.MATERIAL_CHECK)).isEqualTo(FailureCode.MATERIAL_CHECK_FAILED);
        assertThat(FailureCode.forStage(TaskStage.GENERATE)).isEqualTo(FailureCode.GENERATION_FAILED);
        assertThat(FailureCode.forStage(TaskStage.FINALIZE)).isEqualTo(FailureCode.RESULT_RENDER_FAILED);
    }

    @Test
    void simulatedFailureCarriesTheInjectionOverride() {
        TaskFailureException retryable = TaskFailureException.simulated(TaskStage.MATERIAL_CHECK, true);
        TaskFailureException terminal = TaskFailureException.simulated(TaskStage.GENERATE, false);

        assertThat(retryable.simulated()).isTrue();
        assertThat(retryable.retryable()).isTrue();
        assertThat(retryable.code()).isEqualTo(FailureCode.MATERIAL_CHECK_FAILED);
        assertThat(terminal.retryable()).isFalse();
        assertThat(terminal.code()).isEqualTo(FailureCode.GENERATION_FAILED);
    }
}
