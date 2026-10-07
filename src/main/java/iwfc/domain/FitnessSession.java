package iwfc.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FitnessSession {
    private final String id;
    private final String title;
    private final String instructorId;
    private final String studio;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final int capacity;
    private final List<String> equipmentIds;
    private final Set<String> bookedMemberIds;
    private boolean active;

    public FitnessSession(String id, String title, String instructorId, String studio,
                          LocalDateTime startTime, LocalDateTime endTime, int capacity,
                          Collection<String> equipmentIds) {
        this.id = requireText(id, "Session ID");
        this.title = requireText(title, "Session title");
        this.instructorId = requireText(instructorId, "Instructor ID");
        this.studio = requireText(studio, "Studio");
        this.startTime = Objects.requireNonNull(startTime, "Start time is required");
        this.endTime = Objects.requireNonNull(endTime, "End time is required");
        if (!endTime.isAfter(startTime)) throw new IllegalArgumentException("End time must be after start time");
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be greater than zero");
        this.capacity = capacity;
        Objects.requireNonNull(equipmentIds, "Equipment IDs are required");
        this.equipmentIds = new ArrayList<>();
        Set<String> uniqueEquipmentIds = new HashSet<>();
        for (String equipmentId : equipmentIds) {
            String validatedId = requireText(equipmentId, "Equipment ID");
            if (!uniqueEquipmentIds.add(validatedId)) {
                throw new IllegalArgumentException("Equipment IDs must be unique within a session");
            }
            this.equipmentIds.add(validatedId);
        }
        this.bookedMemberIds = new HashSet<>();
        this.active = true;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getInstructorId() { return instructorId; }
    public String getStudio() { return studio; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public int getCapacity() { return capacity; }
    public boolean isActive() { return active; }
    public List<String> getEquipmentIds() { return Collections.unmodifiableList(equipmentIds); }
    public Set<String> getBookedMemberIds() { return Collections.unmodifiableSet(bookedMemberIds); }

    public boolean overlaps(FitnessSession other) {
        Objects.requireNonNull(other, "Other session is required");
        return startTime.isBefore(other.endTime) && endTime.isAfter(other.startTime);
    }

    public boolean usesStudio(String studio) { return this.studio.equalsIgnoreCase(studio); }
    public boolean usesAnyEquipment(Collection<String> ids) {
        Objects.requireNonNull(ids, "Equipment IDs are required");
        return ids.stream().anyMatch(equipmentIds::contains);
    }
    public boolean hasCapacity() { return bookedMemberIds.size() < capacity; }
    public boolean containsMember(String memberId) { return bookedMemberIds.contains(memberId); }
    public boolean addMember(String memberId) { return bookedMemberIds.add(requireText(memberId, "Member ID")); }
    public void deactivate() { active = false; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " is required");
        return value.trim();
    }
}
