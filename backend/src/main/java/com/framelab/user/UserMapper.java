package com.framelab.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByEmail(@Param("email") String email);

    User findById(@Param("id") long id);

    UserAuthState findAuthState(@Param("id") long id);

    int insertRefreshToken(NewRefreshToken token);

    int touchRefreshToken(@Param("tokenHash") String tokenHash);

    RefreshSession findActiveSession(@Param("tokenHash") String tokenHash);

    RefreshSession findSessionForUpdate(@Param("tokenHash") String tokenHash);

    int markRotated(@Param("id") long id, @Param("replacedBy") long replacedBy);

    int revokeFamily(@Param("familyId") String familyId, @Param("reason") String reason);

    int revokeAllForUser(@Param("userId") long userId, @Param("reason") String reason);

    int incrementTokenVersion(@Param("id") long id);
}
