package de.podolak.tools.myrequirementsengineering.domain;

import jakarta.persistence.*;

import java.util.Objects;

/**
 * Requirement entity. Forms a tree per project via parentId.
 * parentId == 0 indicates the root node of a project.
 * identifier is auto-generated and immutable.
 * title is the display name in the navigation tree; description is optional.
 */
@Entity
@Table(name = "requirement")
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false, length = 100, updatable = false)
    private String identifier;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "parent_id", nullable = false)
    private Long parentId = 0L;

    public Requirement() {
    }

    public Requirement(Long projectId, String identifier, String title, String description, Long parentId) {
        this.projectId = projectId;
        this.identifier = identifier;
        this.title = title;
        this.description = description;
        this.parentId = (parentId != null ? parentId : 0L);
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = (parentId != null ? parentId : 0L);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Requirement that = (Requirement) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Requirement{" +
                "id=" + id +
                ", identifier='" + identifier + '\'' +
                ", title='" + title + '\'' +
                ", parentId=" + parentId +
                '}';
    }
}
