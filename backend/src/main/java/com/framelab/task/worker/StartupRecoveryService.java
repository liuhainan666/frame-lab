package com.framelab.task.worker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.framelab.task.TaskMapper;
import com.framelab.task.TaskRow;
import com.framelab.task.TaskStepAttemptMapper;
import com.framelab.task.TaskStepAttemptRow;
import com.framelab.task.TaskStepMapper;
import com.framelab.task.TaskStepRow;
import com.framelab.task.domain.AttemptStatus;
import com.framelab.task.domain.StepStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartupRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryService.class);

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskStepAttemptMapper taskStepAttemptMapper;

    public StartupRecoveryService(TaskMapper taskMapper,
                                  TaskStepMapper taskStepMapper,
                                  TaskStepAttemptMapper taskStepAttemptMapper) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.taskStepAttemptMapper = taskStepAttemptMapper;
    }

    @Transactional
    public RecoveryReport recover() {
        List<String> taskIds = taskMapper.findRunningIdsForUpdate();
        List<String> recovered = new ArrayList<>();
        int succeeded = 0;
        for (String taskId : taskIds) {
            TaskRow task = taskMapper.findById(taskId);
            if (task == null) {
                continue;
            }
            List<TaskStepRow> steps = taskStepMapper.findByTask(taskId);
            List<TaskStepAttemptRow> attempts = taskStepAttemptMapper.findByTask(taskId);
            Set<Long> interruptedSteps = new HashSet<>();
            for (TaskStepAttemptRow attempt : attempts) {
                if (attempt.getStatus() == AttemptStatus.RUNNING) {
                    taskStepAttemptMapper.markInterrupted(attempt.getId());
                    interruptedSteps.add(attempt.getStepId());
                }
            }
            for (TaskStepRow step : steps) {
                boolean interrupted = interruptedSteps.contains(step.getId());
                if (step.getStatus() == StepStatus.RUNNING || interrupted) {
                    taskStepMapper.resetForResume(step.getId(), interrupted);
                }
            }

            boolean allSucceeded = steps.stream().allMatch(step -> step.getStatus() == StepStatus.SUCCEEDED);
            if (allSucceeded && task.getResultStorageKey() != null) {
                taskMapper.markSucceeded(taskId, task.getResultStorageKey(),
                        task.getResultMimeType(), task.getResultPosterKey(),
                        task.getResultWidth(), task.getResultHeight(),
                        task.getResultDurationMs(), task.getResultSizeBytes());
                succeeded++;
            } else {
                taskMapper.markQueuedForResume(taskId);
            }
            recovered.add(taskId);
            log.info("task.recovered taskId={} interruptedAttempts={} resumeCount={}",
                    taskId, interruptedSteps.size(), task.getResumeCount() + 1);
        }
        return new RecoveryReport(recovered.size(), succeeded, recovered);
    }

    public record RecoveryReport(int recovered, int succeeded, List<String> taskIds) {
    }
}
