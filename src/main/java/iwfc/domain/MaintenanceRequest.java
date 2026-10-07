package iwfc.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class MaintenanceRequest {
    public enum Urgency { LOW, MEDIUM, HIGH }
    public enum Status { PENDING, ASSIGNED, COMPLETED }

    private final String id;
    private final String equipmentId;
    private final String description;
    private final Urgency urgency;
    private Status status;
    private final String reportedByInstructorId;
    private String assignedTo;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MaintenanceRequest(String id, String equipmentId, String description,
                              Urgency urgency, String reportedByInstructorId) {
        this.id = requireText(id, "Request ID");
        this.equipmentId = requireText(equipmentId, "Equipment ID");
        this.description = requireText(description, "Description");
        this.urgency = Objects.requireNonNull(urgency, "Urgency is required");
        this.reportedByInstructorId = requireText(reportedByInstructorId, "Reporter ID");
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public String getId() { return id; }
    public String getEquipmentId() { return equipmentId; }
    public String getDescription() { return description; }
    public Urgency getUrgency() { return urgency; }
    public Status getStatus() { return status; }
    public String getReportedByInstructorId() { return reportedByInstructorId; }
    public String getAssignedTo() { return assignedTo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void assignTo(String assignee) {
        if (status != Status.PENDING) throw new IllegalStateException("Only pending requests can be assigned");
        assignedTo = requireText(assignee, "Assignee");
        status = Status.ASSIGNED;
        updatedAt = LocalDateTime.now();
    }

    public void complete() {
        if (status != Status.ASSIGNED) throw new IllegalStateException("Only assigned requests can be completed");
        status = Status.COMPLETED;
        updatedAt = LocalDateTime.now();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " is required");
        return value.trim();
    }
}

