package com.framelab.asset;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetMapper {

    int insert(UploadAsset asset);

    UploadAsset findOwned(@Param("id") String id, @Param("userId") long userId);

    int countTaskReferences(@Param("assetId") String assetId);

    int deleteOwned(@Param("id") String id, @Param("userId") long userId);
}
