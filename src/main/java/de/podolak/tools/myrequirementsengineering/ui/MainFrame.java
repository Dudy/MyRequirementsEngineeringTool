package de.podolak.tools.myrequirementsengineering.ui;

import de.podolak.tools.myrequirementsengineering.domain.Requirement;
import de.podolak.tools.myrequirementsengineering.service.RecentProjectsService;
import de.podolak.tools.myrequirementsengineering.service.RequirementService;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Main application window.
 * - Top: Menu bar (File, Edit, Config, Help)
 * - Left: JTree showing requirement hierarchy (title)
 * - Right: Form to edit selected Requirement
 * - Center split with movable divider
 *
 * Keyboard in tree:
 *   Up/Down: navigate
 *   Left/Right: collapse/expand
 *   Ctrl+N: create new child node (inline title edit in tree)
 */
public class MainFrame extends JFrame {

    private final RequirementService requirementService;
    private final RecentProjectsService recentProjectsService;

    // UI
    private JTree requirementTree;
    private DefaultTreeModel treeModel;
    private RequirementFormPanel formPanel;
    private JSplitPane splitPane;
    private JLabel statusLabel;

    // State
    private Long currentProjectId;
    private Requirement currentRoot;
    private Map<Long, List<Requirement>> currentChildrenMap;
    private boolean treeReloading = false;

    public MainFrame(RequirementService requirementService,
                     RecentProjectsService recentProjectsService) {
        super("myReqEng - Requirements Engineering Tool");
        this.requirementService = requirementService;
        this.recentProjectsService = recentProjectsService;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }
        });

        setSize(1100, 720);
        setLocationRelativeTo(null);

        initMenuBar();
        initContent();
        initTreeKeyboardHandling();

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ignored) {
        }

        updateStatus("Bereit. Öffnen oder erstellen Sie ein Projekt über Datei > New Project oder Open.");
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // === FILE ===
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem newProjectItem = new JMenuItem("New Project...");
        newProjectItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        newProjectItem.addActionListener(e -> createNewProject());

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> openProjectDialog());

        JMenu recentMenu = new JMenu("Recent Projects");
        recentMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) { rebuildRecentMenu(recentMenu); }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });

        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.addActionListener(e -> closeCurrentProject());

        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exitItem.addActionListener(e -> handleExit());

        fileMenu.add(newProjectItem);
        fileMenu.add(openItem);
        fileMenu.add(recentMenu);
        fileMenu.addSeparator();
        fileMenu.add(closeItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // === EDIT ===
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);
        JMenuItem deleteItem = new JMenuItem("Delete Selected Requirement");
        deleteItem.setEnabled(false); // TODO future
        editMenu.add(deleteItem);
        editMenu.addSeparator();
        JMenuItem placeholder = new JMenuItem("(weitere Funktionen folgen)");
        placeholder.setEnabled(false);
        editMenu.add(placeholder);

        // === CONFIG ===
        JMenu configMenu = new JMenu("Config");
        configMenu.setMnemonic(KeyEvent.VK_C);
        JMenuItem cfgPlaceholder = new JMenuItem("Einstellungen (nicht implementiert)");
        cfgPlaceholder.setEnabled(false);
        configMenu.add(cfgPlaceholder);

        // === HELP ===
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        JMenuItem aboutItem = new JMenuItem("Über myReqEng");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "myReqEng - Requirements Engineering Tool\n\n" +
                        "Java Swing + Spring Boot 3 + PostgreSQL + Flyway\n\n" +
                        "Navigation im Baum: Pfeiltasten\n" +
                        "Neuer Kindknoten: STRG+N\n" +
                        "Unterstruktur auf/zu: Pfeil-Links/Rechts",
                "Über myReqEng", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(configMenu);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void rebuildRecentMenu(JMenu recentMenu) {
        recentMenu.removeAll();
        List<String> recents = recentProjectsService.getRecent();
        if (recents.isEmpty()) {
            JMenuItem none = new JMenuItem("(keine)");
            none.setEnabled(false);
            recentMenu.add(none);
            return;
        }
        for (String entry : recents) {
            String[] parts = entry.split("\\|", 2);
            Long id = Long.parseLong(parts[0]);
            String title = parts.length > 1 ? parts[1] : ("Project " + id);
            JMenuItem item = new JMenuItem(title);
            item.addActionListener(e -> openProjectById(id));
            recentMenu.add(item);
        }
        recentMenu.addSeparator();
        JMenuItem clear = new JMenuItem("Liste leeren");
        clear.addActionListener(e -> {
            recentProjectsService.clear();
            rebuildRecentMenu(recentMenu);
        });
        recentMenu.add(clear);
    }

    private void initContent() {
        // Left: Tree
        requirementTree = new JTree();
        requirementTree.setRootVisible(true);
        requirementTree.setShowsRootHandles(true);
        // Important: Do NOT keep the tree permanently editable.
        // We only enable editing temporarily when the user explicitly starts inline title editing.
        // This prevents rendering glitches (disappearing text) during keyboard navigation with arrow keys.
        requirementTree.setEditable(false);

        // Custom renderer - IMPORTANT: must not pollute state across cells
        requirementTree.setCellRenderer(new DefaultTreeCellRenderer() {
            private final Icon rootIcon = UIManager.getIcon("FileView.computerIcon");
            private final Icon requirementIcon = UIManager.getIcon("FileView.fileIcon");

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

                if (value instanceof DefaultMutableTreeNode node) {
                    Object uo = node.getUserObject();
                    if (uo instanceof RequirementWrapper w) {
                        Requirement req = w.getRequirement();
                        String title = req.getTitle();
                        setText(title != null && !title.isBlank() ? title : "(no title)");

                        if (req.getParentId() == 0) {
                            if (rootIcon != null) {
                                setIcon(rootIcon);
                            }
                        } else if (requirementIcon != null) {
                            setIcon(requirementIcon);
                        }
                    }
                }
                return this;
            }
        });

        // Cell editor for inline title editing.
        requirementTree.setCellEditor(new RequirementTitleCellEditor());

        JScrollPane treeScroll = new JScrollPane(requirementTree);
        treeScroll.setPreferredSize(new Dimension(320, 500));

        // Right: Form (changes are saved immediately - no Save button)
        formPanel = new RequirementFormPanel();
        formPanel.setChangeListener(this::handleLiveEdit);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, formPanel);
        splitPane.setDividerLocation(340);
        splitPane.setResizeWeight(0.3);
        splitPane.setOneTouchExpandable(true);

        // Status bar
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Tree selection -> load form (with dirty check)
        requirementTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                if (treeReloading) return;
                handleTreeSelectionChange(e);
            }
        });

        // Double click = start inline title editing
        requirementTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowRequirementContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowRequirementContextMenu(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    TreePath path = requirementTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        requirementTree.setSelectionPath(path);
                        startInlineEditing(path);
                    }
                }
            }
        });

        // Initial empty state
        clearTreeAndForm();
    }

    private void maybeShowRequirementContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }

        TreePath path = requirementTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }

        requirementTree.setSelectionPath(path);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(node.getUserObject() instanceof RequirementWrapper wrapper)) {
            return;
        }

        Requirement requirement = wrapper.getRequirement();

        JPopupMenu menu = new JPopupMenu();

        JMenuItem addItem = new JMenuItem("add new requirement");
        addItem.addActionListener(action -> createNewChildNode(node));
        menu.add(addItem);

        JMenuItem deleteItem = new JMenuItem("delete requirement");
        deleteItem.setEnabled(requirement.getParentId() != 0);
        deleteItem.addActionListener(action -> deleteRequirementNode(node));
        menu.add(deleteItem);

        JMenuItem linkItem = new JMenuItem("link requirement");
        linkItem.setEnabled(false);
        menu.add(linkItem);

        menu.show(requirementTree, e.getX(), e.getY());
    }

    private class RequirementTitleCellEditor extends AbstractCellEditor implements TreeCellEditor {
        private final JTextField editorField = new JTextField();
        private DefaultMutableTreeNode editingNode;
        private Requirement editingRequirement;

        RequirementTitleCellEditor() {
            editorField.addActionListener(e -> stopCellEditing());
            editorField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelEditing");
            editorField.getActionMap().put("cancelEditing", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    cancelCellEditing();
                }
            });
        }

        @Override
        public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded,
                                                    boolean leaf, int row) {
            editingNode = value instanceof DefaultMutableTreeNode node ? node : null;
            editingRequirement = null;

            if (editingNode != null && editingNode.getUserObject() instanceof RequirementWrapper wrapper) {
                editingRequirement = wrapper.getRequirement();
                String title = editingRequirement.getTitle();
                editorField.setText(title != null ? title : "");
            } else {
                editorField.setText("");
            }

            SwingUtilities.invokeLater(editorField::selectAll);
            return editorField;
        }

        @Override
        public Object getCellEditorValue() {
            return editingNode != null ? editingNode.getUserObject() : null;
        }

        @Override
        public boolean isCellEditable(EventObject e) {
            return true;
        }

        @Override
        public boolean shouldSelectCell(EventObject e) {
            return true;
        }

        @Override
        public boolean stopCellEditing() {
            String newTitle = editorField.getText() != null ? editorField.getText().trim() : "";
            if (editingNode != null && editingRequirement != null) {
                if (newTitle.isBlank()) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Titel darf nicht leer sein.");
                    return false;
                }

                try {
                    Requirement toUpdate = copyRequirement(editingRequirement);
                    toUpdate.setTitle(newTitle);
                    Requirement updated = requirementService.updateRequirement(toUpdate);
                    RequirementWrapper wrapper = (RequirementWrapper) editingNode.getUserObject();
                    wrapper.setRequirement(updated);
                    treeModel.nodeChanged(editingNode);
                    updateRootAfterChange(updated);
                    showRequirementInForm(updated.getId());
                    updateStatus("Titel aktualisiert: " + updated.getTitle());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Fehler beim Speichern des Titels: " + ex.getMessage());
                    return false;
                }
            }

            boolean stopped = super.stopCellEditing();
            requirementTree.setEditable(false);
            return stopped;
        }

        @Override
        public void cancelCellEditing() {
            super.cancelCellEditing();
            requirementTree.setEditable(false);
        }
    }

    private Requirement copyRequirement(Requirement source) {
        Requirement copy = new Requirement(
                source.getProjectId(),
                source.getIdentifier(),
                source.getTitle(),
                source.getDescription(),
                source.getParentId()
        );
        copy.setId(source.getId());
        return copy;
    }

    private void initTreeKeyboardHandling() {
        requirementTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                TreePath selPath = requirementTree.getSelectionPath();
                DefaultMutableTreeNode selNode = (selPath != null)
                        ? (DefaultMutableTreeNode) selPath.getLastPathComponent()
                        : null;

                if (code == KeyEvent.VK_RIGHT) {
                    if (selPath != null) {
                        requirementTree.expandPath(selPath);
                    }
                    e.consume();
                } else if (code == KeyEvent.VK_LEFT) {
                    if (selPath != null) {
                        if (requirementTree.isExpanded(selPath)) {
                            requirementTree.collapsePath(selPath);
                        } else if (selNode != null && selNode.getParent() != null) {
                            // go to parent
                            TreePath parentPath = selPath.getParentPath();
                            requirementTree.setSelectionPath(parentPath);
                            requirementTree.collapsePath(parentPath);
                        }
                    }
                    e.consume();
                } else if (code == KeyEvent.VK_N && e.isControlDown()) {
                    e.consume();
                    createNewChildNode(selNode);
                } else if (code == KeyEvent.VK_F2) {
                    if (selPath != null) {
                        e.consume();
                        startInlineEditing(selPath);
                    }
                } else if (code == KeyEvent.VK_DELETE || code == KeyEvent.VK_BACK_SPACE) {
                    // future: delete
                    e.consume();
                }
            }
        });
    }

    /**
     * Safely starts inline title editing.
     * We temporarily enable editing only for this action and disable it again afterwards.
     * This prevents visual glitches (text disappearing) when navigating with cursor keys.
     */
    private void startInlineEditing(TreePath path) {
        if (path == null) return;
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode node) ||
                !(node.getUserObject() instanceof RequirementWrapper)) {
            return;
        }

        // Stop any previous editing session cleanly
        if (requirementTree.isEditing()) {
            requirementTree.stopEditing();
        }

        requirementTree.setSelectionPath(path);
        requirementTree.scrollPathToVisible(path);
        requirementTree.setEditable(true);
        requirementTree.requestFocusInWindow();
        requirementTree.startEditingAtPath(path);
        if (!requirementTree.isEditing()) {
            requirementTree.setEditable(false);
        }
    }

    private void handleTreeSelectionChange(TreeSelectionEvent e) {
        TreePath path = requirementTree.getSelectionPath();
        if (path == null) {
            formPanel.setRequirement(null);
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object uo = node.getUserObject();
        if (uo instanceof RequirementWrapper wrapper) {
            showRequirementInForm(wrapper.getRequirement().getId());
        }
    }

    private void showRequirementInForm(Long requirementId) {
        if (requirementId == null) {
            formPanel.setRequirement(null);
            return;
        }

        Requirement requirement = requirementService.findById(requirementId).orElse(null);
        formPanel.setRequirement(requirement);
    }

    private void updateRootAfterChange(Requirement updated) {
        if (updated == null || currentRoot == null) {
            return;
        }

        if (Objects.equals(currentRoot.getId(), updated.getId())) {
            currentRoot = updated;
            recentProjectsService.addRecent(updated.getProjectId(), updated.getTitle());
        }
    }

    // ==================== TREE BUILDING ====================

    private void clearTreeAndForm() {
        if (requirementTree.isEditing()) {
            requirementTree.stopEditing();
        }
        requirementTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("(kein Projekt geöffnet)")));
        requirementTree.setEditable(false); // ensure we stay in non-editing mode
        formPanel.setRequirement(null);
        currentProjectId = null;
        currentRoot = null;
        currentChildrenMap = null;
        updateStatus("Kein Projekt geöffnet.");
    }

    private void loadProjectIntoTree(Long projectId) {
        this.currentProjectId = projectId;
        if (projectId == null) {
            clearTreeAndForm();
            return;
        }

        if (requirementTree.isEditing()) {
            requirementTree.stopEditing();
        }
        requirementTree.setEditable(false);

        RequirementService.TreeData treeData = requirementService.loadTreeData(projectId);
        this.currentRoot = treeData.getRoot();
        this.currentChildrenMap = treeData.getChildrenMap();

        if (currentRoot == null) {
            JOptionPane.showMessageDialog(this, "Projekt hat keinen Root-Knoten (parentId=0).");
            clearTreeAndForm();
            return;
        }

        // Build Swing tree
        DefaultMutableTreeNode rootNode = buildTreeNodeRecursive(currentRoot, currentChildrenMap);
        treeModel = createTreeModel(rootNode);
        requirementTree.setModel(treeModel);

        // Expand root and first level
        requirementTree.expandPath(new TreePath(rootNode.getPath()));
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            TreeNode child = rootNode.getChildAt(i);
            requirementTree.expandPath(new TreePath(((DefaultMutableTreeNode) child).getPath()));
        }

        // Select root
        requirementTree.setSelectionPath(new TreePath(rootNode.getPath()));
        showRequirementInForm(currentRoot.getId());

        recentProjectsService.addRecent(projectId, currentRoot.getTitle());
        updateStatus("Projekt geöffnet: " + currentRoot.getTitle() + " (Projekt-ID " + projectId + ")");
    }

    private DefaultMutableTreeNode buildTreeNodeRecursive(Requirement req, Map<Long, List<Requirement>> childrenMap) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new RequirementWrapper(req));
        List<Requirement> children = childrenMap.getOrDefault(req.getId(), Collections.emptyList());
        for (Requirement child : children) {
            node.add(buildTreeNodeRecursive(child, childrenMap));
        }
        return node;
    }

    private DefaultTreeModel createTreeModel(DefaultMutableTreeNode rootNode) {
        return new DefaultTreeModel(rootNode) {
            @Override
            public void valueForPathChanged(TreePath path, Object newValue) {
                if (path == null) {
                    return;
                }

                Object last = path.getLastPathComponent();
                if (!(last instanceof DefaultMutableTreeNode node)) {
                    super.valueForPathChanged(path, newValue);
                    return;
                }

                Object currentValue = node.getUserObject();
                if (currentValue instanceof RequirementWrapper) {
                    if (newValue instanceof RequirementWrapper) {
                        node.setUserObject(newValue);
                    }
                    nodeChanged(node);
                    return;
                }

                super.valueForPathChanged(path, newValue);
            }
        };
    }

    private void reloadCurrentTreeAndReselect(Long selectRequirementId) {
        if (currentProjectId == null) return;

        // Stop any active editing before rebuilding the model (prevents renderer glitches)
        if (requirementTree.isEditing()) {
            requirementTree.stopEditing();
        }

        treeReloading = true;
        try {
            RequirementService.TreeData data = requirementService.loadTreeData(currentProjectId);
            currentRoot = data.getRoot();
            currentChildrenMap = data.getChildrenMap();

            if (currentRoot == null) {
                clearTreeAndForm();
                return;
            }

            DefaultMutableTreeNode newRootNode = buildTreeNodeRecursive(currentRoot, currentChildrenMap);
            treeModel.setRoot(newRootNode);

            // Try to reselect
            DefaultMutableTreeNode toSelect = findNodeByReqId(newRootNode, selectRequirementId);
            if (toSelect == null) {
                toSelect = newRootNode; // fallback
            }
            TreePath path = new TreePath(toSelect.getPath());
            requirementTree.setSelectionPath(path);
            requirementTree.expandPath(path);
            RequirementWrapper wrapper = (RequirementWrapper) toSelect.getUserObject();
            showRequirementInForm(wrapper.getRequirement().getId());
        } finally {
            treeReloading = false;
        }
    }

    private DefaultMutableTreeNode findNodeByReqId(DefaultMutableTreeNode node, Long reqId) {
        if (node == null || reqId == null) return null;
        Object uo = node.getUserObject();
        if (uo instanceof RequirementWrapper w && Objects.equals(w.getRequirement().getId(), reqId)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = findNodeByReqId((DefaultMutableTreeNode) node.getChildAt(i), reqId);
            if (found != null) return found;
        }
        return null;
    }

    // ==================== ACTIONS ====================

    private void createNewProject() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        JTextField titleField = new JTextField(30);
        JTextArea descArea = new JTextArea(4, 30);
        descArea.setLineWrap(true);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Projekt-Titel*:"), gbc);
        gbc.gridx = 1;
        panel.add(titleField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Beschreibung:"), gbc);
        gbc.gridx = 1;
        panel.add(new JScrollPane(descArea), gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Neues Projekt anlegen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String title = titleField.getText().trim();
            if (title.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Titel darf nicht leer sein.");
                return;
            }
            try {
                Requirement rootReq = requirementService.createProject(title, descArea.getText());
                loadProjectIntoTree(rootReq.getProjectId());
                updateStatus("Neues Projekt erstellt: " + rootReq.getTitle());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Fehler beim Anlegen: " + ex.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openProjectDialog() {
        List<Requirement> roots = requirementService.findProjectRoots();
        if (roots.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keine Projekte in der Datenbank vorhanden.\nErstellen Sie zuerst ein neues Projekt.");
            return;
        }

        DefaultListModel<Requirement> model = new DefaultListModel<>();
        for (Requirement root : roots) {
            model.addElement(root);
        }

        JList<Requirement> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Requirement root) {
                    setText(root.getTitle() + " (Projekt-ID " + root.getProjectId() + ")");
                }
                return this;
            }
        });

        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(450, 220));

        int res = JOptionPane.showConfirmDialog(this, sp, "Projekt öffnen", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION && list.getSelectedValue() != null) {
            openProjectById(list.getSelectedValue().getProjectId());
        }
    }

    private void openProjectById(Long projectId) {
        RequirementService.TreeData treeData = requirementService.loadTreeData(projectId);
        if (treeData.getRoot() == null) {
            JOptionPane.showMessageDialog(this, "Projekt nicht gefunden oder ohne Root-Knoten (Projekt-ID " + projectId + ").");
            return;
        }
        loadProjectIntoTree(projectId);
    }

    private void closeCurrentProject() {
        clearTreeAndForm();
    }

    private void createNewChildNode(DefaultMutableTreeNode parentNode) {
        if (parentNode == null || currentProjectId == null) {
            JOptionPane.showMessageDialog(this, "Bitte zuerst ein Requirement im Baum auswählen.");
            return;
        }

        Object uo = parentNode.getUserObject();
        if (!(uo instanceof RequirementWrapper parentWrapper)) {
            return;
        }
        Requirement parentReq = parentWrapper.getRequirement();

        try {
            Requirement newReq = requirementService.createRequirement(
                    parentReq.getProjectId(),
                    "Neues Requirement",
                    "",
                    parentReq.getId()
            );

            // Insert into Swing tree
            RequirementWrapper newWrapper = new RequirementWrapper(newReq);
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(newWrapper);

            treeModel.insertNodeInto(newNode, parentNode, parentNode.getChildCount());

            // Expand parent and select new node
            TreePath parentPath = new TreePath(parentNode.getPath());
            requirementTree.expandPath(parentPath);

            TreePath newPath = new TreePath(newNode.getPath());
            requirementTree.setSelectionPath(newPath);
            requirementTree.scrollPathToVisible(newPath);

            // Start direct inline editing of the title (as requested by user)
            startInlineEditing(newPath);

            updateStatus("Neuer Kindknoten erstellt. Titel direkt im Baum editierbar.");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Anlegen des Kindknotens: " + ex.getMessage());
        }
    }

    private void deleteRequirementNode(DefaultMutableTreeNode node) {
        if (node == null || currentProjectId == null) {
            return;
        }

        Object uo = node.getUserObject();
        if (!(uo instanceof RequirementWrapper wrapper)) {
            return;
        }

        Requirement requirement = wrapper.getRequirement();
        if (requirement.getParentId() == 0) {
            JOptionPane.showMessageDialog(this, "Das Root-Requirement eines Projekts kann nicht gelöscht werden.");
            return;
        }

        int descendantCount = countDescendants(node);
        String message = "Requirement \"" + requirement.getTitle() + "\" wirklich löschen?";
        if (descendantCount > 0) {
            message += "\n\nDabei werden auch " + descendantCount + " Unterelement(e) gelöscht.";
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                message,
                "Requirement löschen",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        if (requirementTree.isEditing()) {
            requirementTree.stopEditing();
        }

        try {
            List<Long> idsToDelete = new ArrayList<>();
            collectRequirementIdsPostOrder(node, idsToDelete);

            for (Long id : idsToDelete) {
                requirementService.deleteRequirement(id);
            }

            reloadCurrentTreeAndReselect(requirement.getParentId());
            updateStatus("Requirement gelöscht: " + requirement.getTitle());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fehler beim Löschen des Requirements: " + ex.getMessage(),
                    "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int countDescendants(DefaultMutableTreeNode node) {
        int count = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            count++;
            count += countDescendants(child);
        }
        return count;
    }

    private void collectRequirementIdsPostOrder(DefaultMutableTreeNode node, List<Long> ids) {
        for (int i = 0; i < node.getChildCount(); i++) {
            collectRequirementIdsPostOrder((DefaultMutableTreeNode) node.getChildAt(i), ids);
        }

        Object uo = node.getUserObject();
        if (uo instanceof RequirementWrapper wrapper) {
            ids.add(wrapper.getRequirement().getId());
        }
    }

    /**
     * Called on every change in the form. Persists immediately (no Save button).
     * Tree is updated in-place for title/identifier/desc changes.
     * Parent changes are persisted but may require reselecting the node for full tree consistency.
     */
    private void handleLiveEdit(Requirement req) {
        if (req == null || currentProjectId == null) return;

        try {
            Requirement updated = requirementService.updateRequirement(req);

            // Update tree node in place (especially important for title changes)
            if (treeModel != null && requirementTree.getModel() != null) {
                DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
                DefaultMutableTreeNode node = findNodeByReqId(root, updated.getId());
                if (node != null) {
                    RequirementWrapper w = (RequirementWrapper) node.getUserObject();
                    w.setRequirement(updated);
                    treeModel.nodeChanged(node);
                    updateRootAfterChange(updated);
                }
            }

            // Note: If the user changed parentId, the tree hierarchy is now outdated.
            // For a clean experience we could reload here, but that disrupts typing.
            // Current compromise: persist immediately, user sees change after next navigation or project reopen.
            // (Can be improved later with smarter reparenting in the tree model.)

        } catch (Exception ex) {
            // Minimal feedback during live editing to avoid dialog spam while typing
            updateStatus("Fehler beim Speichern: " + ex.getMessage());
        }
    }

    private void handleExit() {
        dispose();
        System.exit(0);
    }

    private void updateStatus(String msg) {
        statusLabel.setText(msg);
    }

    // Convenience for external callers if needed
    public void openProject(Long projectId) {
        loadProjectIntoTree(projectId);
    }
}
