package de.bht.modulebook.studyprogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        studyProgram.setDegreeType("BACHELOR");

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
        StudyProgram studyProgram = new StudyProgram(10L, "Architektur", "BACHELOR", null, null, null, null);
        when(studyProgramRepository.findAll()).thenReturn(List.of(studyProgram));

        List<StudyProgram> result = service.getAll();

        assertThat(result).containsExactly(studyProgram);
        verify(studyProgramRepository).findAll();
    }

    @Test
    void deleteUsesRepositoryAfterCheckingStudyProgramExists() {
        StudyProgram studyProgram = new StudyProgram(10L, "Architektur", "BACHELOR", null, null, null, null);
        when(studyProgramRepository.findById(10L)).thenReturn(Optional.of(studyProgram));
        when(studyProgramRepository.delete(10L)).thenReturn(true);

        service.delete(10L);

        verify(studyProgramRepository).findById(10L);
        verify(studyProgramRepository).delete(10L);
    }
}
