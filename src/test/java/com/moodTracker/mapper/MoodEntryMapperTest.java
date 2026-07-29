package com.moodTracker.mapper;

import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.entity.MoodEntry;
import com.moodTracker.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoodEntryMapperTest {

    private final MoodEntryMapper mapper = Mappers.getMapper(MoodEntryMapper.class);

    @Test
    void shouldMapEntityToDto() {
        User user = new User();
        user.setId(1L);
        MoodEntry entity = MoodEntry.builder()
                .id(10L)
                .user(user)
                .entryDate(LocalDate.of(2026, 7, 20))
                .moodScore(4)
                .note("A productive day")
                .build();

        MoodEntryDto result = mapper.toDto(entity);

        assertThat(result)
                .extracting(
                        MoodEntryDto::getId,
                        MoodEntryDto::getUserId,
                        MoodEntryDto::getEntryDate,
                        MoodEntryDto::getMoodScore,
                        MoodEntryDto::getNote
                )
                .containsExactly(
                        10L,
                        1L,
                        LocalDate.of(2026, 7, 20),
                        4,
                        "A productive day"
                );
    }

    @Test
    void shouldMapDtoToEntityAndCreateUserReference() {
        MoodEntryDto dto = MoodEntryDto.builder()
                .id(10L)
                .userId(1L)
                .entryDate(LocalDate.of(2026, 7, 20))
                .moodScore(4)
                .note("A productive day")
                .build();

        MoodEntry result = mapper.toEntity(dto);

        assertThat(result)
                .extracting(
                        MoodEntry::getId,
                        MoodEntry::getEntryDate,
                        MoodEntry::getMoodScore,
                        MoodEntry::getNote
                )
                .containsExactly(
                        10L,
                        LocalDate.of(2026, 7, 20),
                        4,
                        "A productive day"
                );
        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo(1L);
        assertThat(result.getUser().getEmail()).isNull();
    }

    @Test
    void shouldMapEntityAndDtoLists() {
        User user = new User();
        user.setId(1L);
        MoodEntry entity = MoodEntry.builder()
                .id(10L)
                .user(user)
                .entryDate(LocalDate.of(2026, 7, 20))
                .moodScore(4)
                .note("A productive day")
                .build();

        List<MoodEntryDto> dtos = mapper.toDto(List.of(entity));
        List<MoodEntry> entities = mapper.toEntity(dtos);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().getUserId()).isEqualTo(1L);
        assertThat(entities).hasSize(1);
        assertThat(entities.getFirst().getUser().getId()).isEqualTo(1L);
        assertThat(entities.getFirst().getNote()).isEqualTo("A productive day");
    }

    @Test
    void shouldHandleMissingUserReference() {
        MoodEntry entity = MoodEntry.builder()
                .id(10L)
                .entryDate(LocalDate.of(2026, 7, 20))
                .moodScore(4)
                .note("A productive day")
                .build();
        MoodEntryDto dto = MoodEntryDto.builder()
                .id(10L)
                .userId(null)
                .entryDate(LocalDate.of(2026, 7, 20))
                .moodScore(4)
                .note("A productive day")
                .build();

        assertThat(mapper.toDto(entity).getUserId()).isNull();
        assertThat(mapper.toEntity(dto).getUser()).isNull();
        assertThat(mapper.mapUserFromId(null)).isNull();
    }

    @Test
    void shouldReturnNullForNullInputs() {
        assertThat(mapper.toDto((MoodEntry) null)).isNull();
        assertThat(mapper.toEntity((MoodEntryDto) null)).isNull();
        assertThat(mapper.toDto((List<MoodEntry>) null)).isNull();
        assertThat(mapper.toEntity((List<MoodEntryDto>) null)).isNull();
    }
}
