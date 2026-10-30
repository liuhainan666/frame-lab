package com.framelab.task;

import com.framelab.task.domain.TaskFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskStepService {

    private static final Logger log = LoggerFactory.getLogger(TaskStepService.class);
    private static final String SIMULATED_ARGS = "{\"simulated\":true}";

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskStepAttemptMapper taskStepAttemptMapper;
    private final RetryPolicy retryPolicy;

    public TaskStepService(TaskMapper taskMapper,
                           TaskStepMapper taskStepMapper,
                           TaskStepAttemptMapper taskStepAttemptMapper,
                           RetryPolicy retryPolicy) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.taskStepAttemptMapper = taskStepAttemptMapper;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public TaskClaimService.ClaimedStep complete(TaskClaimService.ClaimedStep claim, String messageArgs) {
        succeedAttemptAndStep(claim.stepId(), messageArgs);
        TaskStepRow next = taskStepMapper.findByTaskAndSeq(claim.taskId(), claim.seq() + 1);
        if (next == null) {
            return null;
        }
        if (taskStepMapper.markRunning(next.getId()) == 0) {
            throw new IllegalStateException("Unable to start next task step");
        }
        TaskStepAttemptRow attempt = new TaskStepAttemptRow();
        attempt.setTaskId(claim.taskId());
        attempt.setStepId(next.getId());
        attempt.setAttemptNo(taskStepAttemptMapper.maxAttemptNo(next.getId()) + 1);
        taskStepAttemptMapper.insert(attempt);
        if (taskMapper.updateCurrentStage(claim.taskId(), next.getStage()) == 0) {
            throw new IllegalStateException("Unable to advance task stage");
        }
        return new TaskClaimService.ClaimedStep(claim.taskId(), claim.userId(),
                next.getId(), next.getSeq(), next.getStage());
    }

    @Transactional
    public void completeFinal(TaskClaimService.ClaimedStep claim, String messageArgs,
                              String storageKey, int width, int height, long sizeBytes) {
        succeedAttemptAndStep(claim.stepId(), messageArgs);
        if (taskMapper.markSucceeded(claim.taskId(), storageKey, width, height, sizeBytes) == 0) {
            throw new IllegalStateException("Task is no longer running");
        }
    }

    @Transactional
    public void fail(TaskClaimService.ClaimedStep claim, TaskFailureException failure) {
        TaskStepRow step = taskStepMapper.findByTaskAndSeq(claim.taskId(), claim.seq());
        TaskRow task = taskMapper.findById(claim.taskId());
        if (step == null || task == null) {
            throw new IllegalStateException("Task or step disappeared while failing");
        }
        TaskStepAttemptRow attempt = taskStepAttemptMapper.findRunningByStep(claim.stepId());
        if (attempt != null) {
            taskStepAttemptMapper.markFailed(attempt.getId(), failure.code().name(),
                    truncate(failure.detail()));
        }
        if (taskStepMapper.markFailed(claim.stepId(), failure.code().name()) == 0) {
            throw new IllegalStateException("Unable to fail task step");
        }

        boolean retry = failure.retryable() && step.getAttemptCount() < task.getMaxStepAttempts();
        if (retry) {
            int delaySeconds = retryPolicy.delaySeconds(step.getAttemptCount());
            if (taskMapper.markRetrying(claim.taskId(), claim.stage(), failure.code().name(),
                    truncate(failure.detail()), failure.simulated(), delaySeconds) == 0) {
                throw new IllegalStateException("Unable to schedule task retry");
            }
            log.info("task.retry_scheduled taskId={} stage={} attempt={} code={} delaySeconds={}",
                    claim.taskId(), claim.stage(), step.getAttemptCount(), failure.code(), delaySeconds);
            return;
        }

        taskStepMapper.markSkippedAfter(claim.taskId(), claim.seq());
        String args = failure.simulated() ? SIMULATED_ARGS : null;
        if (taskMapper.markFailed(claim.taskId(), failure.code().name(), args,
                truncate(failure.detail()), failure.simulated()) == 0) {
            throw new IllegalStateException("Unable to fail task");
        }
        log.info("task.failed taskId={} stage={} attempt={} code={} simulated={}",
                claim.taskId(), claim.stage(), step.getAttemptCount(), failure.code(), failure.simulated());
    }

    private void succeedAttemptAndStep(long stepId, String messageArgs) {
        TaskStepAttemptRow attempt = taskStepAttemptMapper.findRunningByStep(stepId);
        if (attempt == null) {
            throw new IllegalStateException("No running attempt for step");
        }
        if (taskStepAttemptMapper.markSucceeded(attempt.getId()) == 0) {
            throw new IllegalStateException("Unable to finish task attempt");
        }
        if (taskStepMapper.markSucceeded(stepId, messageArgs) == 0) {
            throw new IllegalStateException("Unable to finish task step");
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
