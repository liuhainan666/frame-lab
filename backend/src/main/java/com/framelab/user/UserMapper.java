package com.framelab.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByEmail(@Param("email") String email);

    User findById(@Param("id") long id);

    int insertRefreshToken(@Param("userId") long userId,
                           @Param("tokenHash") String tokenHash,
                           @Param("ttlSeconds") long ttlSeconds);

    int revokeRefreshToken(@Param("tokenHash") String tokenHash);

    int touchRefreshToken(@Param("tokenHash") String tokenHash);

    RefreshSession findActiveSession(@Param("tokenHash") String tokenHash);
}
