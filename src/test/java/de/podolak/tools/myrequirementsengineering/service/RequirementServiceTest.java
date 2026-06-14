package de.podolak.tools.myrequirementsengineering.service;

import de.podolak.tools.myrequirementsengineering.domain.Requirement;
import de.podolak.tools.myrequirementsengineering.repository.RequirementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementServiceTest {

    @Mock
    private RequirementRepository requirementRepository;

    @InjectMocks
    private RequirementService requirementService;

    @Test
    void createProjectGeneratesRootRequirementWithFirstProjectAndIdentifier() {
        when(requirementRepository.findMaxProjectId()).thenReturn(0L);
        when(requirementRepository.findIdentifiersByProjectId(1L)).thenReturn(List.of());
        when(requirementRepository.save(any(Requirement.class))).thenAnswer(invocation -> {
            Requirement req = invocation.getArgument(0);
            req.setId(100L);
            return req;
        });

        Requirement root = requirementService.createProject("Project A", "Description");

        assertEquals(1L, root.getProjectId());
        assertEquals("REQ-1", root.getIdentifier());
        assertEquals("Project A", root.getTitle());
        assertEquals("Description", root.getDescription());
        assertEquals(0L, root.getParentId());
    }

    @Test
    void createRequirementUsesNextProjectSequenceForIdentifier() {
        when(requirementRepository.findIdentifiersByProjectId(7L))
                .thenReturn(List.of("REQ-1", "REQ-3", "REQ-12", "BROKEN"));
        when(requirementRepository.save(any(Requirement.class))).thenAnswer(invocation -> {
            Requirement req = invocation.getArgument(0);
            req.setId(200L);
            return req;
        });

        Requirement req = requirementService.createRequirement(7L, "Child", null, 99L);

        assertEquals(7L, req.getProjectId());
        assertEquals("REQ-13", req.getIdentifier());
        assertEquals("Child", req.getTitle());
        assertEquals(99L, req.getParentId());
    }

    @Test
    void updateRequirementKeepsIdentifierAndProjectIdImmutable() {
        Requirement stored = new Requirement(9L, "REQ-77", "Stored", "", 0L);
        stored.setId(44L);

        when(requirementRepository.findById(44L)).thenReturn(Optional.of(stored));
        when(requirementRepository.save(any(Requirement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Requirement incoming = new Requirement(123L, "REQ-999", "Updated", "New", 0L);
        incoming.setId(44L);

        Requirement updated = requirementService.updateRequirement(incoming);

        assertEquals(9L, updated.getProjectId());
        assertEquals("REQ-77", updated.getIdentifier());
        assertEquals("Updated", updated.getTitle());
    }
}
