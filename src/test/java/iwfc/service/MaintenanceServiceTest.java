package iwfc.service;

import iwfc.domain.Administrator;
import iwfc.domain.Equipment;
import iwfc.domain.FitnessSession;
import iwfc.domain.Instructor;
import iwfc.domain.MaintenanceRequest;
import iwfc.domain.Member;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import iwfc.exception.UnauthorizedAccessException;
import iwfc.repository.GenericRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceServiceTest {
    private GenericRepository<User> users;
    private GenericRepository<Equipment> equipment;
    private GenericRepository<FitnessSession> sessions;
    private MaintenanceService service;
    private Administrator administrator;
    private Administrator secondAdministrator;
    private Administrator inactiveAdministrator;
    private Instructor reporter;
    private Instructor unrelatedInstructor;
    private Member member;
    private Equipment bike;

    @BeforeEach
    void setUp() throws Exception {
        users = new GenericRepository<>(User::getId);
        equipment = new GenericRepository<>(Equipment::getId);
        sessions = new GenericRepository<>(FitnessSession::getId);
        GenericRepository<MaintenanceRequest> requests =
                new GenericRepository<>(MaintenanceRequest::getId);
        service = new MaintenanceService(users, equipment, sessions, requests);

        administrator = new Administrator("A1", "Asha");
        secondAdministrator = new Administrator("A2", "Amal");
        inactiveAdministrator = new Administrator("A3", "Arun");
        inactiveAdministrator.deactivate();
        reporter = new Instructor("I1", "Ishan");
        unrelatedInstructor = new Instructor("I2", "Indira");
        member = new Member("M1", "Maya");
        for (User user : List.of(administrator, secondAdministrator, inactiveAdministrator, reporter,
                unrelatedInstructor, member)) {
            users.add(user);
            service.registerObserver(user);
        }

        bike = new Equipment("EQ1", "Spin Bike", "Studio A");
        equipment.add(bike);
        equipment.add(new Equipment("EQ2", "Rower", "Cardio Zone"));
        sessions.add(new FitnessSession("S1", "Spin", "I1", "Studio A",
                at(9), at(10), 10, List.of("EQ1")));
    }

    @Test
    void reportsFaultMarksEquipmentFaultyAndPreservesAdministratorNotifications() throws Exception {
        MaintenanceRequest request = report("R1");

        assertEquals(MaintenanceRequest.Status.PENDING, request.getStatus());
        assertEquals(Equipment.Status.FAULTY, bike.getStatus());
        assertEquals(1, administrator.getNotifications().size());
        assertTrue(administrator.getNotifications().getFirst().contains("R1"));
        assertEquals(1, secondAdministrator.getNotifications().size());
        assertTrue(inactiveAdministrator.getNotifications().isEmpty());
        assertEquals(List.of(scheduleNotice("Spin")), reporter.getNotifications());
        assertTrue(unrelatedInstructor.getNotifications().isEmpty());
        assertTrue(member.getNotifications().isEmpty());
    }

    @Test
    void eachObserverReceivesOnlyOneMessagePerFaultEvent() throws Exception {
        service.registerObserver(administrator);
        report("R1");

        assertEquals(1, administrator.getNotifications().size());
    }

    @Test
    void faultNotifiesOnlyActiveMembersBookedIntoAffectedSessions() throws Exception {
        Member inactiveBookedMember = new Member("M2", "Inactive Member");
        inactiveBookedMember.deactivate();
        users.add(inactiveBookedMember);
        service.registerObserver(inactiveBookedMember);
        FitnessSession affected = sessions.findById("S1").orElseThrow();
        affected.addMember(member.getId());
        affected.addMember(inactiveBookedMember.getId());

        report("R1");

        assertEquals(List.of("Schedule notice: " + affected.getTitle()
                        + " may be affected - " + bike.getName() + " reported faulty"),
                member.getNotifications());
        assertTrue(inactiveBookedMember.getNotifications().isEmpty());
    }

    @Test
    void memberBookedIntoUnrelatedSessionReceivesNoFaultNotice() throws Exception {
        Member unrelatedMember = new Member("M2", "Unrelated Member");
        users.add(unrelatedMember);
        service.registerObserver(unrelatedMember);
        FitnessSession unrelated = new FitnessSession("S2", "Strength Circuit", "I2", "Room B",
                at(10), at(11), 5, List.of("EQ2"));
        unrelated.addMember(unrelatedMember.getId());
        sessions.add(unrelated);

        report("R1");

        assertTrue(unrelatedMember.getNotifications().isEmpty());
    }

    @Test
    void activeInstructorAssignedToAffectedSessionReceivesExactScheduleNotice() throws Exception {
        sessions.findById("S1").orElseThrow().deactivate();
        sessions.add(new FitnessSession("S2", "Cycle Power", "I2", "Studio B",
                at(11), at(12), 8, List.of("EQ1")));

        report("R1");

        assertEquals(List.of(scheduleNotice("Cycle Power")),
                unrelatedInstructor.getNotifications());
        assertTrue(reporter.getNotifications().isEmpty());
    }

    @Test
    void instructorLeadingMultipleAffectedSessionsReceivesOneNoticeForEarliestSession()
            throws Exception {
        sessions.add(new FitnessSession("S2", "Late Cycle", "I1", "Studio B",
                at(11), at(12), 8, List.of("EQ1")));
        sessions.add(new FitnessSession("S0", "Early Cycle", "I1", "Studio C",
                at(7), at(8), 8, List.of("EQ1")));

        report("R1");

        assertEquals(List.of(scheduleNotice("Early Cycle")), reporter.getNotifications());
    }

    @Test
    void reportingInstructorLeadingAffectedSessionReceivesOneScheduleImpactNotice()
            throws Exception {
        report("R1");

        assertEquals(List.of(scheduleNotice("Spin")), reporter.getNotifications());
    }

    @Test
    void unrelatedAndInactiveInstructorsReceiveNoScheduleImpactNotice() throws Exception {
        Instructor inactiveAffectedInstructor = new Instructor("I3", "Inactive Instructor");
        inactiveAffectedInstructor.deactivate();
        users.add(inactiveAffectedInstructor);
        service.registerObserver(inactiveAffectedInstructor);
        sessions.add(new FitnessSession("S2", "Inactive-led Cycle", "I3", "Studio B",
                at(11), at(12), 8, List.of("EQ1")));

        report("R1");

        assertTrue(unrelatedInstructor.getNotifications().isEmpty());
        assertTrue(inactiveAffectedInstructor.getNotifications().isEmpty());
    }

    @Test
    void rejectsDuplicateRequestId() throws Exception {
        report("R1");

        assertThrows(DuplicateDataException.class, () -> report("R1"));
    }

    @Test
    void validatesRequestIdDescriptionUrgencyAndEquipment() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reportFault(" ", "EQ1", "Fault", MaintenanceRequest.Urgency.HIGH, "I1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.reportFault("R1", "EQ1", " ", MaintenanceRequest.Urgency.HIGH, "I1"));
        assertThrows(NullPointerException.class,
                () -> service.reportFault("R1", "EQ1", "Fault", null, "I1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.reportFault("R1", "UNKNOWN", "Fault", MaintenanceRequest.Urgency.HIGH, "I1"));
    }

    @Test
    void rejectsDeactivatedEquipmentAndInvalidReporterAccounts() throws Exception {
        bike.deactivate();
        assertThrows(IllegalArgumentException.class, () -> report("R1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.reportFault("R2", "EQ2", "Fault", MaintenanceRequest.Urgency.LOW, "UNKNOWN"));
        reporter.deactivate();
        assertThrows(IllegalArgumentException.class,
                () -> service.reportFault("R3", "EQ2", "Fault", MaintenanceRequest.Urgency.LOW, "I1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.reportFault("R4", "EQ2", "Fault", MaintenanceRequest.Urgency.LOW, "M1"));
    }

    @Test
    void assignmentMovesPendingToAssignedAndNotifiesOnlyActiveReporter() throws Exception {
        MaintenanceRequest request = report("R1");
        int administratorMessages = administrator.getNotifications().size();

        service.assignRequest("R1", "Technician A");

        assertEquals(MaintenanceRequest.Status.ASSIGNED, request.getStatus());
        assertEquals("Technician A", request.getAssignedTo());
        assertEquals(Equipment.Status.UNDER_MAINTENANCE, bike.getStatus());
        assertEquals(2, reporter.getNotifications().size());
        assertTrue(reporter.getNotifications().get(1).contains("assigned"));
        assertEquals(administratorMessages, administrator.getNotifications().size());
        assertTrue(unrelatedInstructor.getNotifications().isEmpty());
        assertTrue(member.getNotifications().isEmpty());
    }

    @Test
    void inactiveReporterReceivesNoAssignmentOrCompletionNotifications() throws Exception {
        report("R1");
        reporter.deactivate();

        service.assignRequest("R1", "Technician A");
        service.completeRequest("R1");

        assertEquals(List.of(scheduleNotice("Spin")), reporter.getNotifications());
    }

    @Test
    void completionRestoresActiveEquipmentAndNotifiesReporterOnce() throws Exception {
        MaintenanceRequest request = report("R1");
        service.assignRequest("R1", "Technician A");

        service.completeRequest("R1");

        assertEquals(MaintenanceRequest.Status.COMPLETED, request.getStatus());
        assertEquals(Equipment.Status.OPERATIONAL, bike.getStatus());
        assertEquals(3, reporter.getNotifications().size());
        assertTrue(reporter.getNotifications().get(2).contains("completed"));
    }

    @Test
    void completionLeavesEquipmentOperationalWhenNoOtherRequestIsOpen() throws Exception {
        report("R1");
        service.assignRequest("R1", "Technician A");

        service.completeRequest("R1");

        assertEquals(Equipment.Status.OPERATIONAL, bike.getStatus());
    }

    @Test
    void completionLeavesEquipmentFaultyWhenAnotherRequestIsPending() throws Exception {
        report("R1");
        report("R2");
        service.assignRequest("R1", "Technician A");

        service.completeRequest("R1");

        assertEquals(Equipment.Status.FAULTY, bike.getStatus());
    }

    @Test
    void completionLeavesEquipmentUnderMaintenanceWhenAnotherRequestIsAssigned() throws Exception {
        report("R1");
        report("R2");
        service.assignRequest("R1", "Technician A");
        service.assignRequest("R2", "Technician B");

        service.completeRequest("R1");

        assertEquals(Equipment.Status.UNDER_MAINTENANCE, bike.getStatus());
    }

    @Test
    void completionNeverReactivatesDeactivatedEquipment() throws Exception {
        report("R1");
        service.assignRequest("R1", "Technician A");
        bike.deactivate();

        service.completeRequest("R1");

        assertFalse(bike.isActive());
        assertEquals(Equipment.Status.UNDER_MAINTENANCE, bike.getStatus());
    }

    @Test
    void permitsOnlyPendingAssignedCompletedStateFlow() throws Exception {
        report("R1");
        assertThrows(IllegalStateException.class, () -> service.completeRequest("R1"));
        service.assignRequest("R1", "Technician A");
        assertThrows(IllegalStateException.class,
                () -> service.assignRequest("R1", "Technician B"));
        service.completeRequest("R1");
        assertThrows(IllegalStateException.class, () -> service.completeRequest("R1"));
    }

    @Test
    void rejectsUnknownRequestsAndBlankAssignee() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.assignRequest("UNKNOWN", "Technician"));
        assertThrows(IllegalArgumentException.class, () -> service.completeRequest("UNKNOWN"));
        report("R1");
        assertThrows(IllegalArgumentException.class, () -> service.assignRequest("R1", " "));
    }

    @Test
    void requestListingIsReadOnly() throws Exception {
        MaintenanceRequest request = report("R1");
        List<MaintenanceRequest> requests = service.findAllRequests();

        assertEquals(List.of(request), requests);
        assertThrows(UnsupportedOperationException.class, () -> requests.add(request));
    }

    @Test
    void recordsAuthorisedSessionEquipmentUsage() throws Exception {
        service.recordSessionEquipmentUsage("I1", "S1", "EQ1", 2.5);

        assertEquals(2.5, bike.getCumulativeUsageHours());
    }

    @Test
    void preventativeAlertUsesInclusiveBoundaryAndIsNotDuplicated() throws Exception {
        service.recordSessionEquipmentUsage("I1", "S1", "EQ1", 99.9);
        assertFalse(service.checkPreventativeMaintenance("EQ1"));
        assertTrue(administrator.getNotifications().isEmpty());

        service.recordSessionEquipmentUsage("I1", "S1", "EQ1", 0.1);
        assertTrue(service.checkPreventativeMaintenance("EQ1"));
        assertTrue(service.checkPreventativeMaintenance("EQ1"));

        assertEquals(100.0, bike.getCumulativeUsageHours(), 0.000_001);
        assertEquals(1, administrator.getNotifications().size());
        assertEquals(1, secondAdministrator.getNotifications().size());
        assertTrue(administrator.getNotifications().getFirst().contains("Preventative maintenance"));
        assertTrue(inactiveAdministrator.getNotifications().isEmpty());
        assertTrue(reporter.getNotifications().isEmpty());
        assertTrue(unrelatedInstructor.getNotifications().isEmpty());
        assertTrue(member.getNotifications().isEmpty());
    }

    @Test
    void completedMaintenanceStartsANewHundredHourAlertCycle() throws Exception {
        service.recordSessionEquipmentUsage("I1", "S1", "EQ1", 100.0);
        assertEquals(1, administrator.getNotifications().size());

        report("R1");
        service.assignRequest("R1", "Technician A");
        service.completeRequest("R1");

        assertFalse(service.checkPreventativeMaintenance("EQ1"));
        service.recordSessionEquipmentUsage("I1", "S1", "EQ1", 99.9);
        assertFalse(service.checkPreventativeMaintenance("EQ1"));
        service.recordSessionEquipmentUsage("I1", "S1", "EQ1", 0.1);
        assertTrue(service.checkPreventativeMaintenance("EQ1"));
        assertTrue(service.checkPreventativeMaintenance("EQ1"));

        assertEquals(200.0, bike.getCumulativeUsageHours(), 0.000_001);
        assertEquals(3, administrator.getNotifications().size());
        assertEquals(3, secondAdministrator.getNotifications().size());
    }

    @Test
    void rejectsInvalidUsageValues() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSessionEquipmentUsage("I1", "S1", "EQ1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSessionEquipmentUsage("I1", "S1", "EQ1", -1));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSessionEquipmentUsage("I1", "S1", "EQ1", Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSessionEquipmentUsage("I1", "S1", "EQ1", Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsUnknownOrUnassignedUsageRelationships() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSessionEquipmentUsage("I1", "UNKNOWN", "EQ1", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSessionEquipmentUsage("I1", "S1", "UNKNOWN", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSessionEquipmentUsage("I1", "S1", "EQ2", 1));
    }

    @Test
    void rejectsUsageByDifferentInstructorAndForInactiveSession() throws Exception {
        assertThrows(UnauthorizedAccessException.class,
                () -> service.recordSessionEquipmentUsage("I2", "S1", "EQ1", 1));
        sessions.findById("S1").orElseThrow().deactivate();
        assertThrows(IllegalStateException.class,
                () -> service.recordSessionEquipmentUsage("I1", "S1", "EQ1", 1));
    }

    private MaintenanceRequest report(String requestId) throws DuplicateDataException {
        return service.reportFault(requestId, "EQ1", "Resistance failure",
                MaintenanceRequest.Urgency.HIGH, "I1");
    }

    private String scheduleNotice(String sessionTitle) {
        return "Schedule notice: " + sessionTitle
                + " may be affected - " + bike.getName() + " reported faulty";
    }

    private static LocalDateTime at(int hour) {
        return LocalDateTime.of(2026, 10, 5, hour, 0);
    }
}
