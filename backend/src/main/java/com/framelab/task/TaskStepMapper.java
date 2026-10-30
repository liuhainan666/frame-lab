package com.framelab.task;

import java.util.List;

import com.framelab.task.domain.TaskStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskStepMapper {

    int insert(@Param("taskId") String taskId, @Param("seq") int seq, @Param("stage") TaskStage stage);

    List<TaskStepRow> findByTask(@Param("taskId") String taskId);

    TaskStepRow findByTaskAndSeq(@Param("taskId") String taskId, @Param("seq") int seq);

    int markRunning(@Param("id") long id);

    int markSucceeded(@Param("id") long id, @Param("messageArgs") String messageArgs);

    int markFailed(@Param("id") long id, @Param("errorCode") String errorCode);

    int markSkippedAfter(@Param("taskId") String taskId, @Param("seq") int seq);
}
