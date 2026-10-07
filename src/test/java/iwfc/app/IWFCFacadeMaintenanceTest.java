package iwfc.app;

import iwfc.domain.Equipment;
import iwfc.domain.Instructor;
import iwfc.domain.MaintenanceRequest;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import iwfc.exception.UnauthorizedAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IWFCFacadeMaintenanceTest {
    private IWFCFacade facade;
    private User administrator;
    private User instructor;
    private User otherInstructor;
    private User member;
    private Equipment equipment;

    @BeforeEach
    void setUp() throws Exception {
        facade = new IWFCFacade();
        administrator = facade.initializeAdministrator("A1", "Asha");
        instructor = facade.registerUser(administrator, User.Role.INSTRUCTOR, "I1", "Ishan");
        otherInstructor = facade.registerUser(administrator, User.Role.INSTRUCTOR, "I2", "Indira");
        member = facade.registerUser(administrator, User.Role.MEMBER, "M1", "Maya");
        equipment = facade.addEquipment(administrator, "EQ1", "Spin Bike", "Studio A");
    }

    @Test
    void activeInstructorReportsFaultThroughFacade() throws Exception {
        MaintenanceRequest request = facade.reportFault(instructor, "R1", "EQ1",
                "Resistance failure", MaintenanceRequest.Urgency.HIGH);

        assertEquals("I1", request.getReportedByInstructorId());
        assertEquals(Equipment.Status.FAULTY, equipment.getStatus());
        assertEquals(List.of(request), facade.viewMaintenanceRequests(administrator));
        assertEquals(1, administrator.getNotifications().size());
    }

    @Test
    void facadePropagatesDuplicateRequestIdAndInputValidation() throws Exception {
        facade.reportFault(instructor, "R1", "EQ1", "Fault", MaintenanceRequest.Urgency.LOW);

        assertThrows(DuplicateDataException.class,
                () -> facade.reportFault(instructor, "R1", "EQ1", "Another fault",
                        MaintenanceRequest.Urgency.MEDIUM));
        assertThrows(IllegalArgumentException.class,
                () -> facade.reportFault(instructor, "R2", "EQ1", " ", MaintenanceRequest.Urgency.LOW));
        assertThrows(NullPointerException.class,
                () -> facade.reportFault(instructor, "R3", "EQ1", "Fault", null));
        assertThrows(IllegalArgumentException.class,
                () -> facade.reportFault(instructor, "R4", "UNKNOWN", "Fault",
                        MaintenanceRequest.Urgency.LOW));
    }

    @Test
    void reportFaultRequiresExactActiveRegisteredInstructor() throws Exception {
        User fabricated = new Instructor("I1", "Impostor");

        assertThrows(UnauthorizedAccessException.class,
                () -> facade.reportFault(member, "R1", "EQ1", "Fault", MaintenanceRequest.Urgency.LOW));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.reportFault(null, "R1", "EQ1", "Fault", MaintenanceRequest.Urgency.LOW));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.reportFault(fabricated, "R1", "EQ1", "Fault", MaintenanceRequest.Urgency.LOW));
        facade.deactivateUser(administrator, instructor.getId());
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.reportFault(instructor, "R1", "EQ1", "Fault", MaintenanceRequest.Urgency.LOW));
    }

    @Test
    void administratorAssignsAndCompletesMaintenanceThroughFacade() throws Exception {
        MaintenanceRequest request = facade.reportFault(instructor, "R1", "EQ1",
                "Fault", MaintenanceRequest.Urgency.HIGH);

        facade.assignMaintenance(administrator, "R1", "Technician A");
        assertEquals(MaintenanceRequest.Status.ASSIGNED, request.getStatus());
        assertEquals(Equipment.Status.UNDER_MAINTENANCE, equipment.getStatus());

        facade.completeMaintenance(administrator, "R1");
        assertEquals(MaintenanceRequest.Status.COMPLETED, request.getStatus());
        assertEquals(Equipment.Status.OPERATIONAL, equipment.getStatus());
        assertEquals(2, instructor.getNotifications().size());
    }

    @Test
    void assignmentCompletionAndLogAreAdministratorOnly() throws Exception {
        facade.reportFault(instructor, "R1", "EQ1", "Fault", MaintenanceRequest.Urgency.HIGH);

        assertThrows(UnauthorizedAccessException.class,
                () -> facade.assignMaintenance(instructor, "R1", "Technician"));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.completeMaintenance(member, "R1"));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.viewMaintenanceRequests(instructor));
        facade.deactivateUser(administrator, administrator.getId());
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.assignMaintenance(administrator, "R1", "Technician"));
    }

    @Test
    void facadePreservesIllegalStateForInvalidTransitions() throws Exception {
        facade.reportFault(instructor, "R1", "EQ1", "Fault", MaintenanceRequest.Urgency.HIGH);

        assertThrows(IllegalStateException.class,
                () -> facade.completeMaintenance(administrator, "R1"));
        facade.assignMaintenance(administrator, "R1", "Technician");
        assertThrows(IllegalStateException.class,
                () -> facade.assignMaintenance(administrator, "R1", "Other"));
        facade.completeMaintenance(administrator, "R1");
        assertThrows(IllegalStateException.class,
                () -> facade.completeMaintenance(administrator, "R1"));
    }

    @Test
    void completionDoesNotReactivateAdministrativelyDeactivatedEquipment() throws Exception {
        facade.reportFault(instructor, "R1", "EQ1", "Fault", MaintenanceRequest.Urgency.HIGH);
        facade.assignMaintenance(administrator, "R1", "Technician");
        facade.deactivateEquipment(administrator, "EQ1");

        facade.completeMaintenance(administrator, "R1");

        assertFalse(equipment.isActive());
        assertEquals(Equipment.Status.UNDER_MAINTENANCE, equipment.getStatus());
    }

    @Test
    void sessionInstructorRecordsUsageAndTriggersOnePreventativeAlert() throws Exception {
        facade.scheduleSession(instructor, "S1", "Spin", at(9), at(10),
                "Studio A", List.of("EQ1"), 10);

        facade.recordSessionEquipmentUsage(instructor, "S1", "EQ1", 99.9);
        assertFalse(facade.checkPreventativeMaintenance(administrator, "EQ1"));
        facade.recordSessionEquipmentUsage(instructor, "S1", "EQ1", 0.1);
        assertTrue(facade.checkPreventativeMaintenance(administrator, "EQ1"));
        assertTrue(facade.checkPreventativeMaintenance(administrator, "EQ1"));

        assertEquals(100.0, equipment.getCumulativeUsageHours(), 0.000_001);
        assertEquals(1, administrator.getNotifications().size());
        assertTrue(instructor.getNotifications().isEmpty());
        assertTrue(otherInstructor.getNotifications().isEmpty());
        assertTrue(member.getNotifications().isEmpty());
    }

    @Test
    void usageRequiresExactActiveInstructorAndSessionOwnership() throws Exception {
        facade.scheduleSession(instructor, "S1", "Spin", at(9), at(10),
                "Studio A", List.of("EQ1"), 10);
        User fabricated = new Instructor("I1", "Impostor");

        assertThrows(UnauthorizedAccessException.class,
                () -> facade.recordSessionEquipmentUsage(member, "S1", "EQ1", 1));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.recordSessionEquipmentUsage(fabricated, "S1", "EQ1", 1));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.recordSessionEquipmentUsage(otherInstructor, "S1", "EQ1", 1));
        facade.deactivateUser(administrator, instructor.getId());
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.recordSessionEquipmentUsage(instructor, "S1", "EQ1", 1));
    }

    @Test
    void preventativeCheckIsAdministratorOnly() throws Exception {
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.checkPreventativeMaintenance(instructor, "EQ1"));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.checkPreventativeMaintenance(member, "EQ1"));
        assertThrows(IllegalArgumentException.class,
                () -> facade.checkPreventativeMaintenance(administrator, "UNKNOWN"));
    }

    private static LocalDateTime at(int hour) {
        return LocalDateTime.of(2026, 10, 5, hour, 0);
    }
}
