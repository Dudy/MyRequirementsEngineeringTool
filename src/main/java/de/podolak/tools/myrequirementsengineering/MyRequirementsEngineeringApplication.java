package de.podolak.tools.myrequirementsengineering;

import de.podolak.tools.myrequirementsengineering.service.RecentProjectsService;
import de.podolak.tools.myrequirementsengineering.service.RequirementService;
import de.podolak.tools.myrequirementsengineering.ui.MainFrame;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;

/**
 * Spring Boot entry point for the Requirements Engineering Tool.
 *
 * Starts the Spring context (JPA + Flyway + Services) and then
 * launches the Swing UI on the Event Dispatch Thread.
 */
@SpringBootApplication
public class MyRequirementsEngineeringApplication {

    public static void main(String[] args) {
        // Optional: allow headless=false explicitly for Swing
        System.setProperty("java.awt.headless", "false");

        ConfigurableApplicationContext context = SpringApplication.run(MyRequirementsEngineeringApplication.class, args);

        // Launch Swing UI after Spring is ready
        SwingUtilities.invokeLater(() -> {
            try {
                MainFrame mainFrame = new MainFrame(
                        context.getBean(RequirementService.class),
                        context.getBean(RecentProjectsService.class)
                );
                mainFrame.setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Fehler beim Starten der Oberfläche:\n" + ex.getMessage(),
                        "Startfehler", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
                System.exit(1);
            }
        });
    }
}
