package com.framelab.user;

import java.util.Optional;

import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Optional<User> findByEmail(String normalizedEmail) {
        return Optional.ofNullable(userMapper.findByEmail(normalizedEmail));
    }

    public User requireActive(long userId) {
        User user = userMapper.findById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw ApiException.of(ErrorCode.TOKEN_INVALID);
        }
        return user;
    }

    public void incrementTokenVersion(long userId) {
        userMapper.incrementTokenVersion(userId);
    }
}
