package iwfc.domain;

import java.util.Objects;

public final class Equipment {
    public enum Status { OPERATIONAL, FAULTY, UNDER_MAINTENANCE }

    private final String id;
    private String name;
    private String location;
    private Status status;
    private double cumulativeUsageHours;
    private boolean active;

    public Equipment(String id, String name, String location) {
        this.id = requireText(id, "Equipment ID");
        this.name = requireText(name, "Equipment name");
        this.location = requireText(location, "Equipment location");
        this.status = Status.OPERATIONAL;
        this.active = true;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public Status getStatus() { return status; }
    public double getCumulativeUsageHours() { return cumulativeUsageHours; }
    public boolean isActive() { return active; }

    public void updateDetails(String name, String location) {
        this.name = requireText(name, "Equipment name");
        this.location = requireText(location, "Equipment location");
    }

    public void deactivate() { active = false; }

    public void addUsageHours(double hours) {
        if (!Double.isFinite(hours) || hours <= 0) {
            throw new IllegalArgumentException("Usage hours must be a positive finite value");
        }
        cumulativeUsageHours += hours;
    }

    public boolean requiresPreventativeMaintenance(double threshold) {
        if (!Double.isFinite(threshold) || threshold <= 0) {
            throw new IllegalArgumentException("Maintenance threshold must be a positive finite value");
        }
        return cumulativeUsageHours >= threshold;
    }

    public boolean isAvailableForScheduling() {
        return active && status == Status.OPERATIONAL;
    }

    public void markFaulty() { status = Status.FAULTY; }
    public void markUnderMaintenance() { status = Status.UNDER_MAINTENANCE; }
    public void markOperational() { status = Status.OPERATIONAL; }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(fieldName + " is required");
        return value.trim();
    }
}
