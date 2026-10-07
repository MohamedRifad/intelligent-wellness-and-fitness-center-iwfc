package iwfc.service;

import iwfc.domain.Equipment;
import iwfc.domain.FitnessSession;
import iwfc.domain.Instructor;
import iwfc.domain.Member;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import iwfc.exception.InvalidBookingException;
import iwfc.repository.GenericRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingServiceTest {
    private GenericRepository<User> users;
    private GenericRepository<Equipment> equipment;
    private GenericRepository<FitnessSession> sessions;
    private BookingService service;
    private Instructor instructor;
    private Member member;

    @BeforeEach
    void setUp() throws DuplicateDataException {
        users = new GenericRepository<>(User::getId);
        equipment = new GenericRepository<>(Equipment::getId);
        sessions = new GenericRepository<>(FitnessSession::getId);
        service = new BookingService(users, equipment, sessions,
                LocalTime.of(6, 0), LocalTime.of(22, 0));
        instructor = new Instructor("I1", "Ishan");
        member = new Member("M1", "Maya");
        users.add(instructor);
        users.add(new Instructor("I2", "Indira"));
        users.add(member);
        users.add(new Member("M2", "Mina"));
        equipment.add(new Equipment("EQ1", "Spin Bike", "Studio A"));
        equipment.add(new Equipment("EQ2", "Rower", "Cardio Zone"));
    }

    @Test
    void acceptsInclusiveOpeningAndClosingBoundaries() throws Exception {
        FitnessSession opening = schedule("S1", "I1", "Studio A", at(6), at(7), 10, List.of("EQ1"));
        FitnessSession closing = schedule("S2", "I2", "Studio B", at(21), at(22), 10, List.of("EQ2"));

        assertEquals(LocalTime.of(6, 0), opening.getStartTime().toLocalTime());
        assertEquals(LocalTime.of(22, 0), closing.getEndTime().toLocalTime());
    }

    @Test
    void rejectsTimesOutsideOperatingHoursAndCrossDaySessions() {
        assertThrows(InvalidBookingException.class,
                () -> schedule("S1", "I1", "A", at(5, 59), at(7), 10, List.of()));
        assertThrows(InvalidBookingException.class,
                () -> schedule("S2", "I1", "A", at(21), at(22, 1), 10, List.of()));
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleSession("S3", "Yoga", "I1", "A", at(21),
                        at(21).plusDays(1), 10, List.of()));
    }

    @Test
    void rejectsInvalidRequiredSessionValuesAsInvalidBookings() {
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleSession(" ", "Yoga", "I1", "A", at(9), at(10), 5, List.of()));
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleSession("S1", " ", "I1", "A", at(9), at(10), 5, List.of()));
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleSession("S1", "Yoga", "I1", " ", at(9), at(10), 5, List.of()));
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleSession("S1", "Yoga", "I1", "A", at(9), at(10), 0, List.of()));
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleSession("S1", "Yoga", "I1", "A", null, at(10), 5, List.of()));
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleSession("S1", "Yoga", "I1", "A", at(9), at(10), 5, null));
    }

    @Test
    void usesDuplicateDataOnlyForDuplicateSessionId() throws Exception {
        schedule("S1", "I1", "Studio A", at(9), at(10), 10, List.of());

        assertThrows(DuplicateDataException.class,
                () -> schedule("S1", "I2", "Studio B", at(11), at(12), 10, List.of()));
    }

    @Test
    void rejectsInstructorStudioAndEquipmentOverlapsButAllowsAdjacentSessions() throws Exception {
        schedule("S1", "I1", "Studio A", at(9), at(10), 10, List.of("EQ1"));

        assertThrows(InvalidBookingException.class,
                () -> schedule("S2", "I1", "Studio B", at(9, 30), at(10, 30), 10, List.of("EQ2")));
        assertThrows(InvalidBookingException.class,
                () -> schedule("S3", "I2", "Studio A", at(9, 30), at(10, 30), 10, List.of("EQ2")));
        assertThrows(InvalidBookingException.class,
                () -> schedule("S4", "I2", "Studio B", at(9, 30), at(10, 30), 10, List.of("EQ1")));

        FitnessSession adjacent = schedule("S5", "I1", "Studio A", at(10), at(11), 10, List.of("EQ1"));
        assertEquals("S5", adjacent.getId());
    }

    @Test
    void allowsConcurrentSessionsWhenResourcesDiffer() throws Exception {
        schedule("S1", "I1", "Studio A", at(9), at(10), 10, List.of("EQ1"));

        FitnessSession concurrent = schedule("S2", "I2", "Studio B", at(9), at(10), 10, List.of("EQ2"));

        assertEquals("S2", concurrent.getId());
    }

    @Test
    void ignoresInactiveSessionsWhenCheckingConflicts() throws Exception {
        FitnessSession old = schedule("S1", "I1", "Studio A", at(9), at(10), 10, List.of("EQ1"));
        old.deactivate();

        FitnessSession replacement = schedule("S2", "I1", "Studio A", at(9), at(10), 10, List.of("EQ1"));

        assertEquals("S2", replacement.getId());
    }

    @Test
    void rejectsUnknownFaultyUnderMaintenanceAndDeactivatedEquipment() throws Exception {
        assertThrows(InvalidBookingException.class,
                () -> schedule("S0", "I1", "A", at(8), at(9), 5, List.of("UNKNOWN")));

        equipment.findById("EQ1").orElseThrow().markFaulty();
        assertThrows(InvalidBookingException.class,
                () -> schedule("S1", "I1", "A", at(9), at(10), 5, List.of("EQ1")));

        equipment.findById("EQ1").orElseThrow().markUnderMaintenance();
        assertThrows(InvalidBookingException.class,
                () -> schedule("S2", "I1", "A", at(10), at(11), 5, List.of("EQ1")));

        equipment.findById("EQ1").orElseThrow().markOperational();
        equipment.findById("EQ1").orElseThrow().deactivate();
        assertThrows(InvalidBookingException.class,
                () -> schedule("S3", "I1", "A", at(11), at(12), 5, List.of("EQ1")));
    }

    @Test
    void rejectsUnknownInactiveAndWrongRoleInstructor() throws Exception {
        assertThrows(InvalidBookingException.class,
                () -> schedule("S1", "UNKNOWN", "A", at(9), at(10), 5, List.of()));
        instructor.deactivate();
        assertThrows(InvalidBookingException.class,
                () -> schedule("S2", "I1", "A", at(9), at(10), 5, List.of()));
        assertThrows(InvalidBookingException.class,
                () -> schedule("S3", "M1", "A", at(9), at(10), 5, List.of()));
    }

    @Test
    void createsFourWeeklySessionsWithPredictableIdsAndDates() throws Exception {
        List<FitnessSession> series = service.scheduleWeeklySessions("SERIES", "Mobility Basics",
                "I1", "Activity Room", at(8), at(9), 6, List.of(), 4);

        assertEquals(List.of("SERIES-W1", "SERIES-W2", "SERIES-W3", "SERIES-W4"),
                series.stream().map(FitnessSession::getId).toList());
        assertEquals(at(8).plusWeeks(3), series.get(3).getStartTime());
        assertEquals(4, sessions.findAll().size());
        assertThrows(UnsupportedOperationException.class, () -> series.add(series.getFirst()));
    }

    @Test
    void recurringSeriesIsAtomicWhenLaterWeekConflicts() throws Exception {
        schedule("CLASH", "I1", "Activity Room", at(8).plusWeeks(2),
                at(9).plusWeeks(2), 6, List.of());

        assertThrows(InvalidBookingException.class,
                () -> service.scheduleWeeklySessions("SERIES", "Mobility Basics", "I1",
                        "Activity Room", at(8), at(9), 6, List.of(), 4));

        assertEquals(List.of("CLASH"), sessions.findAll().stream()
                .map(FitnessSession::getId).toList());
    }

    @Test
    void rejectsRecurringWeekCountsOutsideOneToTwelve() {
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleWeeklySessions("ZERO", "Mobility Basics", "I1",
                        "Activity Room", at(8), at(9), 6, List.of(), 0));
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleWeeklySessions("LONG", "Mobility Basics", "I1",
                        "Activity Room", at(8), at(9), 6, List.of(), 13));
        assertThrows(InvalidBookingException.class,
                () -> service.scheduleWeeklySessions(" ", "Mobility Basics", "I1",
                        "Activity Room", at(8), at(9), 6, List.of(), 2));
        assertTrue(sessions.findAll().isEmpty());
    }

    @Test
    void eachOccurrenceCanBeBookedIndependently() throws Exception {
        service.scheduleWeeklySessions("SERIES", "Mobility Basics", "I1",
                "Activity Room", at(8), at(9), 2, List.of(), 2);

        service.bookSession("M1", "SERIES-W2");

        assertEquals(List.of("SERIES-W2"), service.findSessionsForMember("M1").stream()
                .map(FitnessSession::getId).toList());
        assertTrue(sessions.findById("SERIES-W1").orElseThrow().getBookedMemberIds().isEmpty());
    }

    @Test
    void activeMemberBooksAndCanListOwnBookings() throws Exception {
        schedule("S1", "I1", "Room B", at(9), at(10), 2, List.of("EQ1"));

        service.bookSession("M1", "S1");

        assertEquals(List.of("S1"), service.findSessionsForMember("M1").stream()
                .map(FitnessSession::getId).toList());
        assertTrue(service.findSessionsForMember("M2").isEmpty());
        assertEquals(List.of(
                "Booking confirmed: Wellness Class on 2026-10-05 at 09:00 in Room B",
                "Wellness tip: Drink water before and after training."),
                member.getNotifications());
    }

    @Test
    void failedBookingSendsNoNotification() throws Exception {
        schedule("S1", "I1", "Room B", at(9), at(10), 1, List.of());
        service.bookSession("M2", "S1");

        assertThrows(InvalidBookingException.class, () -> service.bookSession("M1", "S1"));

        assertTrue(member.getNotifications().isEmpty());
    }

    @Test
    void rejectsDuplicateBookingAndCapacityOverflow() throws Exception {
        schedule("S1", "I1", "A", at(9), at(10), 1, List.of());
        service.bookSession("M1", "S1");

        assertThrows(InvalidBookingException.class, () -> service.bookSession("M1", "S1"));
        assertThrows(InvalidBookingException.class, () -> service.bookSession("M2", "S1"));
    }

    @Test
    void rejectsUnknownInactiveWrongRoleMembersAndUnknownOrBlankSession() throws Exception {
        schedule("S1", "I1", "A", at(9), at(10), 2, List.of());

        assertThrows(InvalidBookingException.class, () -> service.bookSession("UNKNOWN", "S1"));
        member.deactivate();
        assertThrows(InvalidBookingException.class, () -> service.bookSession("M1", "S1"));
        assertThrows(InvalidBookingException.class, () -> service.bookSession("I2", "S1"));
        assertThrows(InvalidBookingException.class, () -> service.bookSession("M2", "UNKNOWN"));
        assertThrows(InvalidBookingException.class, () -> service.bookSession("M2", " "));
    }

    @Test
    void rejectsBookingInactiveSessionOrSessionWhoseEquipmentBecameUnavailable() throws Exception {
        FitnessSession inactive = schedule("S1", "I1", "A", at(9), at(10), 2, List.of());
        inactive.deactivate();
        assertThrows(InvalidBookingException.class, () -> service.bookSession("M1", "S1"));

        schedule("S2", "I1", "B", at(10), at(11), 2, List.of("EQ1"));
        equipment.findById("EQ1").orElseThrow().markFaulty();
        assertThrows(InvalidBookingException.class, () -> service.bookSession("M1", "S2"));
    }

    @Test
    void availableSessionsExcludeFullInactiveAndEquipmentUnavailableSessions() throws Exception {
        schedule("AVAILABLE", "I1", "A", at(8), at(9), 2, List.of());
        schedule("FULL", "I1", "A", at(9), at(10), 1, List.of());
        service.bookSession("M1", "FULL");
        FitnessSession inactive = schedule("INACTIVE", "I1", "A", at(10), at(11), 2, List.of());
        inactive.deactivate();
        schedule("UNAVAILABLE", "I1", "A", at(11), at(12), 2, List.of("EQ1"));
        equipment.findById("EQ1").orElseThrow().markUnderMaintenance();

        List<FitnessSession> available = service.findAvailableSessions();

        assertEquals(List.of("AVAILABLE"), available.stream().map(FitnessSession::getId).toList());
        assertThrows(UnsupportedOperationException.class, () -> available.add(inactive));
    }

    @Test
    void memberBookingListRequiresAnActiveMember() throws Exception {
        schedule("S1", "I1", "A", at(9), at(10), 2, List.of());

        assertThrows(InvalidBookingException.class, () -> service.findSessionsForMember("UNKNOWN"));
        assertThrows(InvalidBookingException.class, () -> service.findSessionsForMember("I1"));
        member.deactivate();
        assertThrows(InvalidBookingException.class, () -> service.findSessionsForMember("M1"));
    }

    private FitnessSession schedule(String id, String instructorId, String studio,
                                    LocalDateTime start, LocalDateTime end, int capacity,
                                    List<String> equipmentIds) throws Exception {
        return service.scheduleSession(id, "Wellness Class", instructorId, studio,
                start, end, capacity, equipmentIds);
    }

    private static LocalDateTime at(int hour) {
        return at(hour, 0);
    }

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 5, hour, minute);
    }
}
