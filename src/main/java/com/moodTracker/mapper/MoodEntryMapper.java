package com.moodTracker.mapper;

import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.entity.MoodEntry;
import com.moodTracker.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MoodEntryMapper {

    @Mapping(source = "user.id", target = "userId")
    MoodEntryDto toDto(MoodEntry moodEntry);

    @Mapping(target = "user", source = "userId", qualifiedByName = "userFromId")
    MoodEntry toEntity(MoodEntryDto dto);

    List<MoodEntryDto> toDto(List<MoodEntry> entities);
    List<MoodEntry> toEntity(List<MoodEntryDto> dtos);

    @Named("userFromId")
    default User mapUserFromId(Long id) {
        if (id == null) return null;
        User u = new User();
        u.setId(id);
        return u;
    }
}
