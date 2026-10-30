package com.framelab.task;

import java.util.List;

import com.framelab.task.domain.TaskStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskMapper {

    TaskRow findOwnedById(@Param("id") String id, @Param("userId") long userId, @Param("locale") String locale);

    TaskRow findById(@Param("id") String id);

    TaskRow findByClientRequestId(@Param("userId") long userId,
                                  @Param("clientRequestId") String clientRequestId);

    List<TaskMaterialRow> findMaterials(@Param("taskId") String taskId);

    List<String> findMaterialAssetIds(@Param("taskId") String taskId);

    int insert(TaskRow task);

    int insertMaterial(@Param("taskId") String taskId,
                       @Param("slotIndex") int slotIndex,
                       @Param("assetId") String assetId);

    List<TaskListRow> findPage(@Param("userId") long userId,
                               @Param("status") String status,
                               @Param("active") boolean active,
                               @Param("templateId") String templateId,
                               @Param("locale") String locale,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    int countPage(@Param("userId") long userId,
                  @Param("status") String status,
                  @Param("active") boolean active,
                  @Param("templateId") String templateId);

    String lockNextClaimableId();

    int markRunning(@Param("id") String id, @Param("stage") TaskStage stage);

    int updateCurrentStage(@Param("id") String id, @Param("stage") TaskStage stage);

    int markSucceeded(@Param("id") String id,
                      @Param("storageKey") String storageKey,
                      @Param("width") int width,
                      @Param("height") int height,
                      @Param("sizeBytes") long sizeBytes);

    int markFailed(@Param("id") String id,
                   @Param("failureCode") String failureCode,
                   @Param("failureArgs") String failureArgs,
                   @Param("failureDetail") String failureDetail,
                   @Param("failureSimulated") boolean failureSimulated);
}
