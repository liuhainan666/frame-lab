package com.framelab.task;

import java.util.List;

import com.framelab.common.util.Uuids;
import com.framelab.config.RetryProperties;
import com.framelab.task.domain.TaskStage;
import com.framelab.template.TemplateRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCreator {

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final RetryProperties retryProperties;

    public TaskCreator(TaskMapper taskMapper, TaskStepMapper taskStepMapper, RetryProperties retryProperties) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.retryProperties = retryProperties;
    }

    @Transactional
    public String create(long userId, TemplateRow template, List<String> assetIds, String clientRequestId) {
        TaskRow task = new TaskRow();
        task.setId(Uuids.newId());
        task.setUserId(userId);
        task.setTemplateId(template.getId());
        task.setTemplateTitle(template.getTitle());
        task.setTemplateCoverPath(template.getCoverPath());
        task.setCanvasWidth(template.getCanvasWidth());
        task.setCanvasHeight(template.getCanvasHeight());
        task.setClientRequestId(clientRequestId);
        task.setMaxStepAttempts(retryProperties.getMaxStepAttempts());
        taskMapper.insert(task);
        for (int index = 0; index < assetIds.size(); index++) {
            taskMapper.insertMaterial(task.getId(), index + 1, assetIds.get(index));
        }
        for (TaskStage stage : TaskStage.values()) {
            taskStepMapper.insert(task.getId(), stage.seq(), stage);
        }
        return task.getId();
    }
}
