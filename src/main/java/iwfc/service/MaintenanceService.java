package iwfc.service;

import iwfc.domain.Equipment;
import iwfc.domain.FitnessSession;
import iwfc.domain.MaintenanceRequest;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import iwfc.exception.UnauthorizedAccessException;
import iwfc.repository.GenericRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Coordinates maintenance, equipment usage, and Observer notification rules. */
public final class MaintenanceService {
    private static final double PREVENTATIVE_MAINTENANCE_THRESHOLD = 100.0;

    private final GenericRepository<User> userRepository;
    private final GenericRepository<Equipment> equipmentRepository;
    private final GenericRepository<FitnessSession> sessionRepository;
    private final GenericRepository<MaintenanceRequest> requestRepository;
    private final List<User> observers = new ArrayList<>();
    private final Set<String> preventativeAlertsSent = new HashSet<>();
    private final Map<String, Double> nextPreventativeThresholds = new HashMap<>();

    public MaintenanceService(GenericRepository<User> userRepository,
                              GenericRepository<Equipment> equipmentRepository,
                              GenericRepository<FitnessSession> sessionRepository,
                              GenericRepository<MaintenanceRequest> requestRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.equipmentRepository = Objects.requireNonNull(equipmentRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.requestRepository = Objects.requireNonNull(requestRepository);
    }

    public void registerObserver(User user) {
        Objects.requireNonNull(user, "Observer is required");
        if (!observers.contains(user)) observers.add(user);
    }

    public MaintenanceRequest reportFault(String requestId, String equipmentId,
                                          String description, MaintenanceRequest.Urgency urgency,
                                          String instructorId) throws DuplicateDataException {
        User reporter = requireActiveUser(instructorId, User.Role.INSTRUCTOR, "Instructor");
        Equipment equipment = requireEquipment(equipmentId);
        if (!equipment.isActive()) {
            throw new IllegalArgumentException("Cannot report a fault for deactivated equipment");
        }

        MaintenanceRequest request = new MaintenanceRequest(
                requestId, equipment.getId(), description, urgency, reporter.getId());
        requestRepository.add(request);
        equipment.markFaulty();
        notifyActiveAdministrators("New fault " + request.getId() + " reported for equipment "
                + equipment.getId());
        notifyBookedMembersOfFault(equipment);
        notifyAffectedInstructorsOfFault(equipment);
        return request;
    }

    public void assignRequest(String requestId, String assignee) {
        MaintenanceRequest request = requireRequest(requestId);
        request.assignTo(assignee);
        requireEquipment(request.getEquipmentId()).markUnderMaintenance();
        notifyReporter(request, "Maintenance request " + request.getId()
                + " assigned to " + request.getAssignedTo());
    }

    public void completeRequest(String requestId) {
        MaintenanceRequest request = requireRequest(requestId);
        request.complete();
        Equipment equipment = requireEquipment(request.getEquipmentId());
        if (equipment.isActive()) {
            refreshEquipmentStatus(equipment);
        }
        resetPreventativeAlertCycle(equipment);
        notifyReporter(request, "Maintenance request " + request.getId() + " completed");
    }

    private void refreshEquipmentStatus(Equipment equipment) {
        List<MaintenanceRequest> openRequests = requestRepository.findAll().stream()
                .filter(request -> request.getEquipmentId().equals(equipment.getId()))
                .filter(request -> request.getStatus() != MaintenanceRequest.Status.COMPLETED)
                .toList();
        if (openRequests.stream().anyMatch(request ->
                request.getStatus() == MaintenanceRequest.Status.PENDING)) {
            equipment.markFaulty();
        } else if (openRequests.stream().anyMatch(request ->
                request.getStatus() == MaintenanceRequest.Status.ASSIGNED)) {
            equipment.markUnderMaintenance();
        } else {
            equipment.markOperational();
        }
    }

    public List<MaintenanceRequest> findAllRequests() {
        return requestRepository.findAll();
    }

    public void recordSessionEquipmentUsage(String instructorId, String sessionId,
                                            String equipmentId, double hours)
            throws UnauthorizedAccessException {
        User instructor = requireActiveUser(instructorId, User.Role.INSTRUCTOR, "Instructor");
        FitnessSession session = sessionRepository.findById(requireText(sessionId, "Session ID"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown session ID: " + sessionId));
        Equipment equipment = requireEquipment(equipmentId);

        if (!session.isActive()) {
            throw new IllegalStateException("Usage cannot be recorded for an inactive session");
        }
        if (!session.getInstructorId().equals(instructor.getId())) {
            throw new UnauthorizedAccessException("Only the session Instructor can record equipment usage");
        }
        if (!session.getEquipmentIds().contains(equipment.getId())) {
            throw new IllegalArgumentException("Equipment is not assigned to this session: " + equipment.getId());
        }

        equipment.addUsageHours(hours);
        publishPreventativeAlertIfRequired(equipment);
    }

    public boolean checkPreventativeMaintenance(String equipmentId) {
        Equipment equipment = requireEquipment(equipmentId);
        return publishPreventativeAlertIfRequired(equipment);
    }

    private boolean publishPreventativeAlertIfRequired(Equipment equipment) {
        double threshold = nextPreventativeThresholds.getOrDefault(
                equipment.getId(), PREVENTATIVE_MAINTENANCE_THRESHOLD);
        if (!equipment.requiresPreventativeMaintenance(threshold)) {
            return false;
        }
        if (preventativeAlertsSent.add(equipment.getId())) {
            notifyActiveAdministrators("Preventative maintenance due for equipment "
                    + equipment.getId() + " at " + equipment.getCumulativeUsageHours() + " hours");
        }
        return true;
    }

    private void resetPreventativeAlertCycle(Equipment equipment) {
        preventativeAlertsSent.remove(equipment.getId());
        nextPreventativeThresholds.put(equipment.getId(),
                equipment.getCumulativeUsageHours() + PREVENTATIVE_MAINTENANCE_THRESHOLD);
    }

    private void notifyActiveAdministrators(String message) {
        observers.stream()
                .filter(User::isActive)
                .filter(user -> user.getRole() == User.Role.ADMINISTRATOR)
                .forEach(user -> user.receiveNotification(message));
    }

    private void notifyReporter(MaintenanceRequest request, String message) {
        observers.stream()
                .filter(User::isActive)
                .filter(user -> user.getId().equals(request.getReportedByInstructorId()))
                .findFirst()
                .ifPresent(user -> user.receiveNotification(message));
    }

    private void notifyBookedMembersOfFault(Equipment equipment) {
        sessionRepository.findAll().stream()
                .filter(FitnessSession::isActive)
                .filter(session -> session.getEquipmentIds().contains(equipment.getId()))
                .forEach(session -> session.getBookedMemberIds().forEach(memberId ->
                        observers.stream()
                                .filter(User::isActive)
                                .filter(user -> user.getRole() == User.Role.MEMBER)
                                .filter(user -> user.getId().equals(memberId))
                                .findFirst()
                                .ifPresent(user -> user.receiveNotification(
                                        scheduleNotice(session, equipment)))));
    }

    private void notifyAffectedInstructorsOfFault(Equipment equipment) {
        Set<String> notifiedInstructorIds = new HashSet<>();
        sessionRepository.findAll().stream()
                .filter(FitnessSession::isActive)
                .filter(session -> session.getEquipmentIds().contains(equipment.getId()))
                .sorted(Comparator.comparing(FitnessSession::getStartTime)
                        .thenComparing(FitnessSession::getId))
                .forEach(session -> {
                    if (!notifiedInstructorIds.add(session.getInstructorId())) {
                        return;
                    }
                    observers.stream()
                            .filter(User::isActive)
                            .filter(user -> user.getRole() == User.Role.INSTRUCTOR)
                            .filter(user -> user.getId().equals(session.getInstructorId()))
                            .findFirst()
                            .ifPresent(user -> user.receiveNotification(
                                    scheduleNotice(session, equipment)));
                });
    }

    private static String scheduleNotice(FitnessSession session, Equipment equipment) {
        return "Schedule notice: " + session.getTitle()
                + " may be affected - " + equipment.getName() + " reported faulty";
    }

    private User requireActiveUser(String userId, User.Role role, String label) {
        String validatedId = requireText(userId, label + " ID");
        User user = userRepository.findById(validatedId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown " + label.toLowerCase() + " ID: " + validatedId));
        if (!user.isActive()) {
            throw new IllegalArgumentException(label + " account is inactive");
        }
        if (user.getRole() != role) {
            throw new IllegalArgumentException(label + " role is required");
        }
        return user;
    }

    private Equipment requireEquipment(String equipmentId) {
        String validatedId = requireText(equipmentId, "Equipment ID");
        return equipmentRepository.findById(validatedId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown equipment ID: " + validatedId));
    }

    private MaintenanceRequest requireRequest(String requestId) {
        String validatedId = requireText(requestId, "Request ID");
        return requestRepository.findById(validatedId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown maintenance request ID: " + validatedId));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
