package com.framelab.task;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskStepAttemptMapper {

    int insert(TaskStepAttemptRow attempt);

    List<TaskStepAttemptRow> findByTask(@Param("taskId") String taskId);

    TaskStepAttemptRow findRunningByStep(@Param("stepId") long stepId);

    int markSucceeded(@Param("id") long id);

    int markFailed(@Param("id") long id,
                   @Param("errorCode") String errorCode,
                   @Param("errorDetail") String errorDetail);
}
