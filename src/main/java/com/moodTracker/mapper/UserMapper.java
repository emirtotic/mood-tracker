package com.moodTracker.mapper;

import com.moodTracker.dto.UserRegisterRequest;
import com.moodTracker.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    User toEntity(UserRegisterRequest dto);
}

