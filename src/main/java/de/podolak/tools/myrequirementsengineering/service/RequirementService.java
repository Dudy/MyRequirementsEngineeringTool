package de.podolak.tools.myrequirementsengineering.service;

import de.podolak.tools.myrequirementsengineering.domain.Requirement;
import de.podolak.tools.myrequirementsengineering.repository.RequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for Requirement management and tree operations.
 */
@Service
@Transactional
public class RequirementService {

    private static final String IDENTIFIER_PREFIX = "REQ-";

    private final RequirementRepository requirementRepository;

    public RequirementService(RequirementRepository requirementRepository) {
        this.requirementRepository = requirementRepository;
    }

    @Transactional(readOnly = true)
    public List<Requirement> findByProjectId(Long projectId) {
        return requirementRepository.findByProjectIdOrderByIdAsc(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<Requirement> findById(Long id) {
        return requirementRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Requirement> findProjectRoots() {
        return requirementRepository.findByParentIdOrderByTitleAsc(0L);
    }

    /**
     * Returns the root requirement for the project (the one with parentId == 0).
     * Assumes there is exactly one root per project.
     */
    @Transactional(readOnly = true)
    public Optional<Requirement> findRootForProject(Long projectId) {
        List<Requirement> roots = requirementRepository.findByProjectIdAndParentId(projectId, 0L);
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        // If multiple roots, pick first (creation order)
        return Optional.of(roots.get(0));
    }

    /**
     * Creates a new project as the root requirement of a new project tree.
     * The provided title and description become the root node attributes.
     */
    public Requirement createProject(String title, String description) {
        Long projectId = findNextProjectId();
        return createRootRequirement(projectId, title, description);
    }

    /**
     * Creates a new requirement node within an existing project tree.
     * The identifier is generated automatically and cannot be overridden.
     */
    public Requirement createRequirement(Long projectId, String title, String description, Long parentId) {
        if (projectId == null) throw new IllegalArgumentException("projectId required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
        if (parentId == null || parentId == 0L) {
            throw new IllegalArgumentException("parentId required for child requirements");
        }

        return createRequirementInternal(projectId, title, description, parentId);
    }

    private Requirement createRootRequirement(Long projectId, String title, String description) {
        return createRequirementInternal(projectId, title, description, 0L);
    }

    private Requirement createRequirementInternal(Long projectId, String title, String description, Long parentId) {
        String ident = nextIdentifier(projectId);
        Requirement r = new Requirement(projectId, ident, title.trim(), description, parentId);
        r = requirementRepository.save(r);
        return r;
    }

    /**
     * Updates an existing requirement. The identifier and projectId are immutable.
     */
    public Requirement updateRequirement(Requirement requirement) {
        if (requirement.getId() == null) {
            throw new IllegalArgumentException("Cannot update requirement without id");
        }
        if (requirement.getTitle() == null || requirement.getTitle().isBlank()) {
            throw new IllegalArgumentException("title is mandatory");
        }
        Requirement existing = requirementRepository.findById(requirement.getId())
                .orElseThrow(() -> new IllegalArgumentException("Requirement not found: " + requirement.getId()));

        if (Objects.equals(existing.getParentId(), 0L) && !Objects.equals(requirement.getParentId(), 0L)) {
            throw new IllegalArgumentException("The root requirement must keep parentId = 0");
        }
        if (!Objects.equals(existing.getParentId(), 0L) && Objects.equals(requirement.getParentId(), 0L)) {
            throw new IllegalArgumentException("Only the root requirement may have parentId = 0");
        }

        requirement.setProjectId(existing.getProjectId());
        requirement.setIdentifier(existing.getIdentifier());
        if (requirement.getIdentifier() == null || requirement.getIdentifier().isBlank()) {
            requirement.setIdentifier(nextIdentifier(existing.getProjectId()));
        }
        return requirementRepository.save(requirement);
    }

    public void deleteRequirement(Long id) {
        // Note: children are not auto-deleted here; UI should prevent or handle recursion
        requirementRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Long findNextProjectId() {
        Long maxProjectId = requirementRepository.findMaxProjectId();
        return (maxProjectId != null ? maxProjectId : 0L) + 1L;
    }

    @Transactional(readOnly = true)
    public String nextIdentifier(Long projectId) {
        long max = 0L;
        for (String identifier : requirementRepository.findIdentifiersByProjectId(projectId)) {
            long parsed = parseIdentifierSequence(identifier);
            if (parsed > max) {
                max = parsed;
            }
        }
        return IDENTIFIER_PREFIX + (max + 1L);
    }

    private long parseIdentifierSequence(String identifier) {
        if (identifier == null || !identifier.startsWith(IDENTIFIER_PREFIX)) {
            return 0L;
        }
        try {
            return Long.parseLong(identifier.substring(IDENTIFIER_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /**
     * Builds a flat map of parentId -> list of direct children for fast tree construction.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Requirement>> buildChildrenMap(Long projectId) {
        List<Requirement> all = findByProjectId(projectId);
        Map<Long, List<Requirement>> map = new HashMap<>();
        for (Requirement r : all) {
            map.computeIfAbsent(r.getParentId(), k -> new ArrayList<>()).add(r);
        }
        // Sort children by title for consistent display
        map.values().forEach(list -> list.sort(Comparator.comparing(Requirement::getTitle, String.CASE_INSENSITIVE_ORDER)));
        return map;
    }

    /**
     * Returns the root and a children map ready for UI tree building.
     */
    @Transactional(readOnly = true)
    public TreeData loadTreeData(Long projectId) {
        Optional<Requirement> rootOpt = findRootForProject(projectId);
        if (rootOpt.isEmpty()) {
            return new TreeData(null, Collections.emptyMap());
        }
        Requirement root = rootOpt.get();
        Map<Long, List<Requirement>> childrenMap = buildChildrenMap(projectId);
        return new TreeData(root, childrenMap);
    }

    /**
     * Simple container for tree root + children map.
     */
    public static class TreeData {
        private final Requirement root;
        private final Map<Long, List<Requirement>> childrenMap;

        public TreeData(Requirement root, Map<Long, List<Requirement>> childrenMap) {
            this.root = root;
            this.childrenMap = childrenMap != null ? childrenMap : Collections.emptyMap();
        }

        public Requirement getRoot() {
            return root;
        }

        public Map<Long, List<Requirement>> getChildrenMap() {
            return childrenMap;
        }
    }
}
