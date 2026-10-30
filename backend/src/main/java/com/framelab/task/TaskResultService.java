package com.framelab.task;

import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.common.i18n.LocaleSupport;
import com.framelab.infrastructure.storage.ObjectStorage;
import com.framelab.infrastructure.storage.StorageException;
import com.framelab.task.domain.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class TaskResultService {

    private static final Logger log = LoggerFactory.getLogger(TaskResultService.class);

    private final TaskMapper taskMapper;
    private final ObjectStorage objectStorage;

    public TaskResultService(TaskMapper taskMapper, ObjectStorage objectStorage) {
        this.taskMapper = taskMapper;
        this.objectStorage = objectStorage;
    }

    public TaskResultContent content(long userId, String taskId) {
        TaskRow task = taskMapper.findOwnedById(taskId, userId,
                LocaleSupport.tag(LocaleContextHolder.getLocale()));
        if (task == null) {
            throw ApiException.of(ErrorCode.TASK_NOT_FOUND);
        }
        if (task.getStatus() != TaskStatus.SUCCEEDED || task.getResultStorageKey() == null) {
            throw ApiException.of(ErrorCode.TASK_NOT_READY);
        }
        try {
            return new TaskResultContent(task, objectStorage.open(task.getResultStorageKey()));
        } catch (StorageException ex) {
            log.error("task.result_open_failed taskId={}", taskId, ex);
            throw ApiException.of(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    public TaskResultContent poster(long userId, String taskId) {
        TaskRow task = taskMapper.findOwnedById(taskId, userId,
                LocaleSupport.tag(LocaleContextHolder.getLocale()));
        if (task == null) {
            throw ApiException.of(ErrorCode.TASK_NOT_FOUND);
        }
        if (task.getStatus() != TaskStatus.SUCCEEDED || task.getResultPosterKey() == null) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        try {
            return new TaskResultContent(task, objectStorage.open(task.getResultPosterKey()));
        } catch (StorageException ex) {
            log.error("task.poster_open_failed taskId={}", taskId, ex);
            throw ApiException.of(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
