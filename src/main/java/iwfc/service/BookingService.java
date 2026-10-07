package iwfc.service;

import iwfc.domain.Equipment;
import iwfc.domain.FitnessSession;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import iwfc.exception.InvalidBookingException;
import iwfc.repository.GenericRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Coordinates IWFC session scheduling and member booking rules. */
public final class BookingService {
    private static final List<String> WELLNESS_TIPS = List.of(
            "Drink water before and after training.",
            "Warm up for 5 minutes before you start.",
            "Rest well between hard sessions.");

    private final GenericRepository<User> userRepository;
    private final GenericRepository<Equipment> equipmentRepository;
    private final GenericRepository<FitnessSession> sessionRepository;
    private final LocalTime openingTime;
    private final LocalTime closingTime;

    public BookingService(GenericRepository<User> userRepository,
                          GenericRepository<Equipment> equipmentRepository,
                          GenericRepository<FitnessSession> sessionRepository,
                          LocalTime openingTime, LocalTime closingTime) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.equipmentRepository = Objects.requireNonNull(equipmentRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.openingTime = Objects.requireNonNull(openingTime);
        this.closingTime = Objects.requireNonNull(closingTime);
    }

    public FitnessSession scheduleSession(String sessionId, String title, String instructorId,
                                          String studio, LocalDateTime startTime,
                                          LocalDateTime endTime, int capacity,
                                          Collection<String> equipmentIds)
            throws InvalidBookingException, DuplicateDataException {
        FitnessSession candidate = prepareSession(sessionId, title, instructorId, studio,
                startTime, endTime, capacity, equipmentIds, List.of());
        sessionRepository.add(candidate);
        return candidate;
    }

    /**
     * Validates and creates an all-or-nothing weekly series with IDs based on
     * {@code baseId-W1}, {@code baseId-W2}, and so on.
     */
    public List<FitnessSession> scheduleWeeklySessions(String baseId, String title,
                                                        String instructorId, String studio,
                                                        LocalDateTime firstStart,
                                                        LocalDateTime firstEnd, int capacity,
                                                        Collection<String> equipmentIds, int weeks)
            throws InvalidBookingException, DuplicateDataException {
        if (weeks < 1 || weeks > 12) {
            throw new InvalidBookingException("Recurring weeks must be between 1 and 12");
        }
        String validatedBaseId;
        try {
            validatedBaseId = requireText(baseId, "Recurring session base ID");
        } catch (IllegalArgumentException exception) {
            throw new InvalidBookingException(exception.getMessage());
        }
        List<FitnessSession> candidates = new ArrayList<>();
        for (int week = 1; week <= weeks; week++) {
            long offset = week - 1L;
            FitnessSession candidate = prepareSession(validatedBaseId + "-W" + week, title,
                    instructorId, studio, shiftWeeks(firstStart, offset),
                    shiftWeeks(firstEnd, offset), capacity, equipmentIds, candidates);
            candidates.add(candidate);
        }
        for (FitnessSession candidate : candidates) {
            sessionRepository.add(candidate);
        }
        return List.copyOf(candidates);
    }

    private FitnessSession prepareSession(String sessionId, String title, String instructorId,
                                           String studio, LocalDateTime startTime,
                                           LocalDateTime endTime, int capacity,
                                           Collection<String> equipmentIds,
                                           Collection<FitnessSession> pendingSessions)
            throws InvalidBookingException, DuplicateDataException {
        validateOperatingHours(startTime, endTime);
        User instructor = requireActiveUser(instructorId, User.Role.INSTRUCTOR, "Instructor");

        FitnessSession candidate;
        try {
            candidate = new FitnessSession(sessionId, title, instructor.getId(), studio,
                    startTime, endTime, capacity, equipmentIds);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidBookingException(exception.getMessage());
        }

        if (sessionRepository.containsId(candidate.getId())) {
            throw new DuplicateDataException("Duplicate session ID: " + candidate.getId());
        }

        validateEquipmentState(candidate);
        validateScheduleConflicts(candidate, pendingSessions);
        return candidate;
    }

    private LocalDateTime shiftWeeks(LocalDateTime dateTime, long weeks)
            throws InvalidBookingException {
        if (dateTime == null) {
            throw new InvalidBookingException("Session start and end times are required");
        }
        return dateTime.plusWeeks(weeks);
    }

    public void bookSession(String memberId, String sessionId) throws InvalidBookingException {
        User member = requireActiveUser(memberId, User.Role.MEMBER, "Member");
        String validatedSessionId;
        try {
            validatedSessionId = requireText(sessionId, "Session ID");
        } catch (IllegalArgumentException exception) {
            throw new InvalidBookingException(exception.getMessage());
        }
        FitnessSession session = sessionRepository.findById(validatedSessionId)
                .orElseThrow(() -> new InvalidBookingException("Unknown session ID: " + sessionId));

        if (!session.isActive()) {
            throw new InvalidBookingException("The session is not available for booking");
        }
        validateEquipmentState(session);
        if (session.containsMember(member.getId())) {
            throw new InvalidBookingException("Member is already booked into this session");
        }
        if (!session.hasCapacity()) {
            throw new InvalidBookingException("The session has reached capacity");
        }
        session.addMember(member.getId());
        member.receiveNotification("Booking confirmed: " + session.getTitle() + " on "
                + session.getStartTime().toLocalDate() + " at "
                + session.getStartTime().toLocalTime() + " in " + session.getStudio());
        int tipIndex = (session.getBookedMemberIds().size() - 1) % WELLNESS_TIPS.size();
        member.receiveNotification("Wellness tip: " + WELLNESS_TIPS.get(tipIndex));
    }

    public List<FitnessSession> findAvailableSessions() {
        return sessionRepository.findAll().stream()
                .filter(FitnessSession::isActive)
                .filter(FitnessSession::hasCapacity)
                .filter(this::hasAvailableEquipment)
                .toList();
    }

    public List<FitnessSession> findSessionsForMember(String memberId) throws InvalidBookingException {
        User member = requireActiveUser(memberId, User.Role.MEMBER, "Member");
        return sessionRepository.findAll().stream()
                .filter(session -> session.containsMember(member.getId()))
                .toList();
    }

    private void validateOperatingHours(LocalDateTime startTime, LocalDateTime endTime)
            throws InvalidBookingException {
        if (startTime == null || endTime == null) {
            throw new InvalidBookingException("Session start and end times are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new InvalidBookingException("Session end time must be after start time");
        }
        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            throw new InvalidBookingException("A session must start and end on the same day");
        }
        if (startTime.toLocalTime().isBefore(openingTime)
                || endTime.toLocalTime().isAfter(closingTime)) {
            throw new InvalidBookingException(
                    "Session must be within operating hours " + openingTime + "-" + closingTime);
        }
    }

    private void validateEquipmentState(FitnessSession session) throws InvalidBookingException {
        for (String equipmentId : session.getEquipmentIds()) {
            Equipment equipment = equipmentRepository.findById(equipmentId)
                    .orElseThrow(() -> new InvalidBookingException("Unknown equipment ID: " + equipmentId));
            if (!equipment.isAvailableForScheduling()) {
                throw new InvalidBookingException("Equipment is unavailable: " + equipmentId);
            }
        }
    }

    private void validateScheduleConflicts(FitnessSession candidate,
                                           Collection<FitnessSession> pendingSessions)
            throws InvalidBookingException {
        for (FitnessSession existing : sessionRepository.findAll()) {
            validateConflict(candidate, existing);
        }
        for (FitnessSession pending : pendingSessions) {
            validateConflict(candidate, pending);
        }
    }

    private void validateConflict(FitnessSession candidate, FitnessSession existing)
            throws InvalidBookingException {
        if (!existing.isActive() || !candidate.overlaps(existing)) return;
        if (candidate.getInstructorId().equals(existing.getInstructorId())) {
            throw new InvalidBookingException("Instructor is already scheduled at this time");
        }
        if (candidate.usesStudio(existing.getStudio())) {
            throw new InvalidBookingException("Studio is already scheduled at this time");
        }
        if (candidate.usesAnyEquipment(existing.getEquipmentIds())) {
            throw new InvalidBookingException("Equipment is already scheduled at this time");
        }
    }

    private User requireActiveUser(String userId, User.Role role, String label)
            throws InvalidBookingException {
        String validatedId;
        try {
            validatedId = requireText(userId, label + " ID");
        } catch (IllegalArgumentException exception) {
            throw new InvalidBookingException(exception.getMessage());
        }
        User user = userRepository.findById(validatedId)
                .orElseThrow(() -> new InvalidBookingException("Unknown " + label.toLowerCase() + " ID: " + validatedId));
        if (!user.isActive()) {
            throw new InvalidBookingException(label + " account is inactive");
        }
        if (user.getRole() != role) {
            throw new InvalidBookingException(label + " role is required");
        }
        return user;
    }

    private boolean hasAvailableEquipment(FitnessSession session) {
        return session.getEquipmentIds().stream()
                .map(equipmentRepository::findById)
                .allMatch(optional -> optional.isPresent() && optional.orElseThrow().isAvailableForScheduling());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
