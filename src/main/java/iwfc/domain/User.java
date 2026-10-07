package iwfc.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Abstract user and observer base for all IWFC roles. */
public abstract class User {
    public enum Role { ADMINISTRATOR, INSTRUCTOR, MEMBER }

    private final String id;
    private final String name;
    private final Role role;
    private boolean active;
    private final List<String> notifications;

    protected User(String id, String name, Role role) {
        this.id = requireText(id, "User ID");
        this.name = requireText(name, "User name");
        this.role = Objects.requireNonNull(role, "Role is required");
        this.active = true;
        this.notifications = new ArrayList<>();
    }

    public final String getId() { return id; }
    public final String getName() { return name; }
    public final Role getRole() { return role; }
    public final boolean isActive() { return active; }

    public final void deactivate() {
        active = false;
    }

    public void receiveNotification(String message) {
        notifications.add(requireText(message, "Notification"));
    }

    public final List<String> getNotifications() {
        return Collections.unmodifiableList(notifications);
    }

    public abstract String getRoleDescription();

    protected static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}

