package de.bht.modulebook.studyprogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StudyProgramServiceTest {

    @Mock
    private StudyProgramRepository studyProgramRepository;

    private StudyProgramService service;

    @BeforeEach
    void setUp() {
        service = new StudyProgramService(studyProgramRepository);
    }

    @Test
    void findOrCreateReturnsExistingStudyProgram() {
        StudyProgram existing = new StudyProgram(
                10L,
                "Architektur",
                2021,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        // name/year come from the PDF import request.
        when(studyProgramRepository.findByNameAndYear("Architektur", 2021)).thenReturn(Optional.of(existing));

        StudyProgram result = service.findOrCreate("Architektur", 2021);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void findOrCreateCreatesStudyProgramWhenMissing() {
        StudyProgram inserted = new StudyProgram();
        inserted.setId(10L);

        StudyProgram created = new StudyProgram(
                10L,
                "Architektur",
                2021,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(studyProgramRepository.findByNameAndYear("Architektur", 2021)).thenReturn(Optional.empty());
        when(studyProgramRepository.insert(org.mockito.ArgumentMatchers.any(StudyProgram.class))).thenReturn(inserted);
        when(studyProgramRepository.findById(10L)).thenReturn(Optional.of(created));

        StudyProgram result = service.findOrCreate("Architektur", 2021);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Architektur");
        assertThat(result.getYear()).isEqualTo(2021);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        when(studyProgramRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException = (ResponseStatusException) exception;
                    assertThat(responseException.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                })
                .hasMessageContaining("Study program not found");
    }

    @Test
    void createRejectsMissingName() {
        StudyProgram studyProgram = new StudyProgram();
        studyProgram.setYear(2021);

        assertThatThrownBy(() -> service.create(studyProgram))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException = (ResponseStatusException) exception;
                    assertThat(responseException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .hasMessageContaining("Study program name is required");
    }

    @Test
    void getAllReturnsRepositoryResult() {
        StudyProgram studyProgram = new StudyProgram(10L, "Architektur", 2021, null, null);
        when(studyProgramRepository.findAll()).thenReturn(List.of(studyProgram));

        List<StudyProgram> result = service.getAll();

        assertThat(result).containsExactly(studyProgram);
        verify(studyProgramRepository).findAll();
    }
}
