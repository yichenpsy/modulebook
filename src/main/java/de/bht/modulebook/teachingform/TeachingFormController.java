package de.bht.modulebook.teachingform;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.bht.modulebook.teachingform.dto.TeachingFormResponse;

@RestController
@RequestMapping("/api/teaching-forms")
public class TeachingFormController {
    private final TeachingFormRepository teachingFormRepository;

    public TeachingFormController(TeachingFormRepository teachingFormRepository) {
        this.teachingFormRepository = teachingFormRepository;
    }

    @GetMapping
    public List<TeachingFormResponse> getAll() {
        return teachingFormRepository.findAll()
                .stream()
                .map(TeachingFormMapper::toResponse)
                .toList();
    }
}
