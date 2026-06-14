package de.podolak.tools.myrequirementsengineering.ui;

import de.podolak.tools.myrequirementsengineering.domain.Requirement;

import java.util.Objects;

/**
 * Wrapper for use as userObject in JTree nodes.
 * toString() returns the title for display in the tree.
 */
public class RequirementWrapper {

    private Requirement requirement;

    public RequirementWrapper(Requirement requirement) {
        this.requirement = Objects.requireNonNull(requirement, "requirement must not be null");
    }

    public Requirement getRequirement() {
        return requirement;
    }

    public void setRequirement(Requirement requirement) {
        this.requirement = Objects.requireNonNull(requirement);
    }

    @Override
    public String toString() {
        return requirement.getTitle() != null ? requirement.getTitle() : "(no title)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequirementWrapper that = (RequirementWrapper) o;
        return Objects.equals(requirement.getId(), that.requirement.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(requirement.getId());
    }
}
