package de.podolak.tools.myrequirementsengineering.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages the list of recently opened projects (max 10).
 * Persisted using Java Preferences API (user-specific).
 */
@Service
public class RecentProjectsService {

    private static final String NODE_NAME = "de/myreqeng/recent";
    private static final String KEY_PREFIX = "recent_";
    private static final int MAX_RECENT = 10;

    private final Preferences prefs;

    public RecentProjectsService() {
        this.prefs = Preferences.userRoot().node(NODE_NAME);
    }

    /**
     * Returns the list of recent entries as "id|title" strings, most recent first.
     */
    public List<String> getRecent() {
        List<String> recents = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String val = prefs.get(KEY_PREFIX + i, null);
            if (val != null && !val.isBlank()) {
                recents.add(val);
            }
        }
        return recents;
    }

    /**
     * Adds or moves the project to the top of recent list.
     */
    public void addRecent(Long projectId, String title) {
        if (projectId == null || title == null) return;
        String entry = projectId + "|" + title;

        List<String> current = getRecent();
        current.remove(entry);
        current.add(0, entry);
        if (current.size() > MAX_RECENT) {
            current = current.subList(0, MAX_RECENT);
        }

        // clear old
        for (int i = 0; i < MAX_RECENT; i++) {
            prefs.remove(KEY_PREFIX + i);
        }
        for (int i = 0; i < current.size(); i++) {
            prefs.put(KEY_PREFIX + i, current.get(i));
        }
        try {
            prefs.flush();
        } catch (Exception ignored) {
        }
    }

    public void clear() {
        for (int i = 0; i < MAX_RECENT; i++) {
            prefs.remove(KEY_PREFIX + i);
        }
        try {
            prefs.flush();
        } catch (Exception ignored) {
        }
    }
}
