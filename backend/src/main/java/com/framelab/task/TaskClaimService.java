package com.framelab.task;

import java.util.List;

import com.framelab.task.domain.StepStatus;
import com.framelab.task.domain.TaskStage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskClaimService {

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskStepAttemptMapper taskStepAttemptMapper;

    public TaskClaimService(TaskMapper taskMapper,
                            TaskStepMapper taskStepMapper,
                            TaskStepAttemptMapper taskStepAttemptMapper) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.taskStepAttemptMapper = taskStepAttemptMapper;
    }

    @Transactional
    public ClaimedStep claimNext() {
        String taskId = taskMapper.lockNextClaimableId();
        if (taskId == null) {
            return null;
        }
        TaskRow task = taskMapper.findById(taskId);
        if (task == null) {
            return null;
        }
        List<TaskStepRow> steps = taskStepMapper.findByTask(taskId);
        TaskStepRow next = steps.stream()
                .filter(step -> step.getStatus() != StepStatus.SUCCEEDED)
                .findFirst()
                .orElse(null);
        if (next == null) {
            return null;
        }
        if (taskMapper.markRunning(taskId, next.getStage()) == 0) {
            return null;
        }
        if (taskStepMapper.markRunning(next.getId()) == 0) {
            throw new IllegalStateException("Unable to start task step");
        }
        TaskStepAttemptRow attempt = new TaskStepAttemptRow();
        attempt.setTaskId(taskId);
        attempt.setStepId(next.getId());
        attempt.setAttemptNo(next.getAttemptCount() + 1);
        taskStepAttemptMapper.insert(attempt);
        return new ClaimedStep(taskId, task.getUserId(), next.getId(), next.getSeq(), next.getStage());
    }

    public record ClaimedStep(String taskId, long userId, long stepId, int seq, TaskStage stage) {
    }
}
