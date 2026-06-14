package de.podolak.tools.myrequirementsengineering.ui;

import de.podolak.tools.myrequirementsengineering.domain.Requirement;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequirementFormPanelTest {

    @Test
    void setRequirementDoesNotTriggerChangeListener() {
        RequirementFormPanel panel = new RequirementFormPanel();
        AtomicInteger calls = new AtomicInteger();
        panel.setChangeListener(req -> calls.incrementAndGet());

        Requirement requirement = new Requirement(1L, "REQ-1", "Title", "Description", 0L);
        requirement.setId(42L);

        panel.setRequirement(requirement);

        assertEquals(0, calls.get());
    }
}
