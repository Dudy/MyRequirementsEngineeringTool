package de.podolak.tools.myrequirementsengineering.ui;

import de.podolak.tools.myrequirementsengineering.domain.Requirement;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Form for editing a single Requirement.
 * All changes are saved immediately (no Save button).
 * Fields: identifier, title, description, parentId. The identifier is read-only.
 */
public class RequirementFormPanel extends JPanel {

    private final JTextField identifierField = new JTextField(25);
    private final JTextField titleField = new JTextField(25);
    private final JTextArea descriptionArea = new JTextArea(8, 25);
    private final JTextField parentIdField = new JTextField(10);

    private Requirement currentRequirement;
    private boolean suppressChangeEvents = false;

    private Consumer<Requirement> changeListener;

    public RequirementFormPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        identifierField.setEditable(false);

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Identifier
        gbc.gridx = 0; gbc.gridy = 0;
        fields.add(new JLabel("Identifier"), gbc);
        gbc.gridx = 1;
        fields.add(identifierField, gbc);

        // Row 1: Title
        gbc.gridx = 0; gbc.gridy = 1;
        fields.add(new JLabel("Titel*"), gbc);
        gbc.gridx = 1;
        fields.add(titleField, gbc);

        // Row 2: Parent ID
        gbc.gridx = 0; gbc.gridy = 2;
        fields.add(new JLabel("Parent ID*"), gbc);
        gbc.gridx = 1;
        fields.add(parentIdField, gbc);

        // Row 3-4: Description
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        fields.add(new JLabel("Beschreibung"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(300, 140));
        fields.add(descScroll, gbc);

        add(fields, BorderLayout.CENTER);

        // No Save button anymore — changes are saved immediately

        // Listeners for immediate save
        DocumentListener dl = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { fireImmediateSave(); }
            @Override public void removeUpdate(DocumentEvent e) { fireImmediateSave(); }
            @Override public void changedUpdate(DocumentEvent e) { fireImmediateSave(); }
        };
        titleField.getDocument().addDocumentListener(dl);
        descriptionArea.getDocument().addDocumentListener(dl);
        parentIdField.getDocument().addDocumentListener(dl);
    }

    public void setChangeListener(Consumer<Requirement> listener) {
        this.changeListener = listener;
    }

    private void fireImmediateSave() {
        if (currentRequirement == null || changeListener == null || suppressChangeEvents) {
            return;
        }

        // Copy current UI values into the model object
        currentRequirement.setIdentifier(identifierField.getText().trim());
        currentRequirement.setTitle(titleField.getText().trim());
        currentRequirement.setDescription(descriptionArea.getText().trim());

        try {
            currentRequirement.setParentId(Long.parseLong(parentIdField.getText().trim()));
        } catch (NumberFormatException ignored) {
            currentRequirement.setParentId(0L);
        }

        // Notify listener (MainFrame) to persist immediately
        changeListener.accept(currentRequirement);
    }

    public void setRequirement(Requirement req) {
        this.currentRequirement = req;

        suppressChangeEvents = true;
        if (req == null) {
            try {
                clearForm();
            } finally {
                suppressChangeEvents = false;
            }
            return;
        }

        try {
            identifierField.setText(req.getIdentifier() != null ? req.getIdentifier() : "");
            titleField.setText(req.getTitle() != null ? req.getTitle() : "");
            descriptionArea.setText(req.getDescription() != null ? req.getDescription() : "");
            parentIdField.setText(req.getParentId() != null ? String.valueOf(req.getParentId()) : "0");
        } finally {
            suppressChangeEvents = false;
        }
    }

    private void clearForm() {
        identifierField.setText("");
        titleField.setText("");
        descriptionArea.setText("");
        parentIdField.setText("");
    }

    public Requirement getCurrentRequirement() {
        return currentRequirement;
    }
}
