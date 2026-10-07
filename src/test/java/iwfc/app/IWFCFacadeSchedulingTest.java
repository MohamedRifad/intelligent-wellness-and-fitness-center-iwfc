package iwfc.app;

import iwfc.domain.Equipment;
import iwfc.domain.FitnessSession;
import iwfc.domain.Instructor;
import iwfc.domain.Member;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import iwfc.exception.InvalidBookingException;
import iwfc.exception.UnauthorizedAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IWFCFacadeSchedulingTest {
    private IWFCFacade facade;
    private User administrator;
    private User instructor;
    private User member;

    @BeforeEach
    void setUp() throws Exception {
        facade = new IWFCFacade();
        administrator = facade.initializeAdministrator("A1", "Asha");
        instructor = facade.registerUser(administrator, User.Role.INSTRUCTOR, "I1", "Ishan");
        member = facade.registerUser(administrator, User.Role.MEMBER, "M1", "Maya");
        facade.addEquipment(administrator, "EQ1", "Spin Bike", "Studio A");
    }

    @Test
    void registeredInstructorSchedulesAllRequiredValuesThroughFacade() throws Exception {
        FitnessSession session = schedule(instructor, "S1", at(6), at(7), "Studio A", List.of("EQ1"), 12);

        assertEquals("S1", session.getId());
        assertEquals("I1", session.getInstructorId());
        assertEquals("Studio A", session.getStudio());
        assertEquals(List.of("EQ1"), session.getEquipmentIds());
        assertEquals(12, session.getCapacity());
    }

    @Test
    void facadePropagatesSchedulingValidationAndDuplicateException() throws Exception {
        schedule(instructor, "S1", at(9), at(10), "Studio A", List.of("EQ1"), 8);

        assertThrows(DuplicateDataException.class,
                () -> schedule(instructor, "S1", at(11), at(12), "Studio B", List.of(), 8));
        assertThrows(InvalidBookingException.class,
                () -> schedule(instructor, "S2", at(5), at(6), "Studio B", List.of(), 8));
        assertThrows(InvalidBookingException.class,
                () -> schedule(instructor, "S3", at(11), at(12), " ", List.of(), 8));
    }

    @Test
    void rejectsWrongRoleNullUnregisteredAndInactiveInstructorActors() throws Exception {
        User fabricated = new Instructor("I1", "Impostor");

        assertThrows(UnauthorizedAccessException.class,
                () -> schedule(member, "S1", at(9), at(10), "A", List.of(), 5));
        assertThrows(UnauthorizedAccessException.class,
                () -> schedule(null, "S1", at(9), at(10), "A", List.of(), 5));
        assertThrows(UnauthorizedAccessException.class,
                () -> schedule(fabricated, "S1", at(9), at(10), "A", List.of(), 5));
        facade.deactivateUser(administrator, instructor.getId());
        assertThrows(UnauthorizedAccessException.class,
                () -> schedule(instructor, "S1", at(9), at(10), "A", List.of(), 5));
    }

    @Test
    void facadeAllowsInstructorButRejectsOtherRolesForRecurringSessions() throws Exception {
        List<FitnessSession> series = facade.scheduleWeeklySessions(instructor, "SERIES",
                "Mobility Basics", at(8), at(9), "Activity Room", List.of(), 5, 2);

        assertEquals(List.of("SERIES-W1", "SERIES-W2"),
                series.stream().map(FitnessSession::getId).toList());
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.scheduleWeeklySessions(member, "MEMBER", "Mobility Basics",
                        at(10), at(11), "Activity Room", List.of(), 5, 2));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.scheduleWeeklySessions(administrator, "ADMIN", "Mobility Basics",
                        at(10), at(11), "Activity Room", List.of(), 5, 2));
    }

    @Test
    void activeRegisteredMemberBooksAndViewsAvailableAndOwnSessions() throws Exception {
        schedule(instructor, "S1", at(9), at(10), "Studio A", List.of("EQ1"), 2);

        assertEquals(List.of("S1"), facade.viewAvailableSessions(member).stream()
                .map(FitnessSession::getId).toList());
        facade.bookSession(member, "S1");

        assertEquals(List.of("S1"), facade.viewMyBookings(member).stream()
                .map(FitnessSession::getId).toList());
    }

    @Test
    void facadeEnforcesDuplicateBookingAndCapacity() throws Exception {
        User secondMember = facade.registerUser(administrator, User.Role.MEMBER, "M2", "Mina");
        schedule(instructor, "S1", at(9), at(10), "Studio A", List.of(), 1);
        facade.bookSession(member, "S1");

        assertThrows(InvalidBookingException.class, () -> facade.bookSession(member, "S1"));
        assertThrows(InvalidBookingException.class, () -> facade.bookSession(secondMember, "S1"));
        assertTrue(facade.viewAvailableSessions(secondMember).isEmpty());
    }

    @Test
    void rejectsWrongRoleUnregisteredAndInactiveMemberActors() throws Exception {
        schedule(instructor, "S1", at(9), at(10), "Studio A", List.of(), 2);
        User fabricated = new Member("M1", "Impostor");

        assertThrows(UnauthorizedAccessException.class, () -> facade.bookSession(instructor, "S1"));
        assertThrows(UnauthorizedAccessException.class, () -> facade.viewAvailableSessions(administrator));
        assertThrows(UnauthorizedAccessException.class, () -> facade.viewMyBookings(instructor));
        assertThrows(UnauthorizedAccessException.class, () -> facade.bookSession(fabricated, "S1"));
        facade.deactivateUser(administrator, member.getId());
        assertThrows(UnauthorizedAccessException.class, () -> facade.bookSession(member, "S1"));
        assertThrows(UnauthorizedAccessException.class, () -> facade.viewAvailableSessions(member));
        assertThrows(UnauthorizedAccessException.class, () -> facade.viewMyBookings(member));
    }

    @Test
    void facadeRejectsEquipmentThatAdministratorDeactivated() throws Exception {
        facade.deactivateEquipment(administrator, "EQ1");

        assertThrows(InvalidBookingException.class,
                () -> schedule(instructor, "S1", at(9), at(10), "Studio A", List.of("EQ1"), 5));
    }

    @Test
    void availableSessionListBecomesEmptyWhenRequiredEquipmentTurnsFaulty() throws Exception {
        schedule(instructor, "S1", at(9), at(10), "Studio A", List.of("EQ1"), 5);
        Equipment equipment = facade.viewEquipment(administrator).getFirst();
        equipment.markFaulty();

        assertTrue(facade.viewAvailableSessions(member).isEmpty());
        assertThrows(InvalidBookingException.class, () -> facade.bookSession(member, "S1"));
    }

    private FitnessSession schedule(User actor, String id, LocalDateTime start,
                                    LocalDateTime end, String studio, List<String> equipmentIds,
                                    int capacity) throws Exception {
        return facade.scheduleSession(actor, id, "Wellness Class", start, end,
                studio, equipmentIds, capacity);
    }

    private static LocalDateTime at(int hour) {
        return LocalDateTime.of(2026, 10, 5, hour, 0);
    }
}
