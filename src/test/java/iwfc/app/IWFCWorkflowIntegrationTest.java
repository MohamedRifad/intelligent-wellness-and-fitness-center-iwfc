package iwfc.app;

import iwfc.domain.Equipment;
import iwfc.domain.FitnessSession;
import iwfc.domain.MaintenanceRequest;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import iwfc.exception.InvalidBookingException;
import iwfc.exception.UnauthorizedAccessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IWFCWorkflowIntegrationTest {
    @Test
    void completesFullIwfcWorkflowUsingOnlyFacadeOperations() throws Exception {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        User instructor = facade.registerUser(administrator, User.Role.INSTRUCTOR, "I1", "Ishan");
        User member = facade.registerUser(administrator, User.Role.MEMBER, "M1", "Maya");
        Equipment equipment = facade.addEquipment(administrator, "EQ1", "Spin Bike", "Studio A");

        FitnessSession session = facade.scheduleSession(instructor, "S1", "Morning Spin",
                at(9), at(10), "Studio A", List.of("EQ1"), 12);
        facade.bookSession(member, session.getId());
        facade.recordSessionEquipmentUsage(instructor, session.getId(), equipment.getId(), 2.5);
        MaintenanceRequest request = facade.reportFault(instructor, "R1", equipment.getId(),
                "Resistance failure", MaintenanceRequest.Urgency.HIGH);

        assertEquals(List.of(request), facade.viewMaintenanceRequests(administrator));
        assertEquals(Equipment.Status.FAULTY, equipment.getStatus());
        assertEquals(2.5, equipment.getCumulativeUsageHours());
        assertEquals(List.of(session), facade.viewMyBookings(member));

        facade.assignMaintenance(administrator, request.getId(), "Technician A");
        assertEquals(MaintenanceRequest.Status.ASSIGNED, request.getStatus());
        assertEquals(Equipment.Status.UNDER_MAINTENANCE, equipment.getStatus());
        assertEquals(2, instructor.getNotifications().size());
        assertEquals("Schedule notice: Morning Spin may be affected - Spin Bike reported faulty",
                instructor.getNotifications().getFirst());
        assertTrue(instructor.getNotifications().get(1).contains("assigned"));

        facade.completeMaintenance(administrator, request.getId());
        assertEquals(MaintenanceRequest.Status.COMPLETED, request.getStatus());
        assertEquals(Equipment.Status.OPERATIONAL, equipment.getStatus());
        assertTrue(equipment.isActive());
        assertEquals(3, instructor.getNotifications().size());
        assertTrue(instructor.getNotifications().get(2).contains("completed"));
    }

    @Test
    void rejectsDuplicateEquipmentThroughFacadeWorkflow() throws Exception {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        facade.addEquipment(administrator, "EQ1", "Spin Bike", "Studio A");

        assertThrows(DuplicateDataException.class,
                () -> facade.addEquipment(administrator, "EQ1", "Replacement Bike", "Studio B"));
    }

    @Test
    void rejectsConflictingSessionsThroughFacadeWorkflow() throws Exception {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        User instructor = facade.registerUser(administrator, User.Role.INSTRUCTOR, "I1", "Ishan");
        facade.addEquipment(administrator, "EQ1", "Spin Bike", "Studio A");
        facade.scheduleSession(instructor, "S1", "Spin", at(9), at(10),
                "Studio A", List.of("EQ1"), 10);

        assertThrows(InvalidBookingException.class,
                () -> facade.scheduleSession(instructor, "S2", "Overlapping Spin",
                        at(9, 30), at(10, 30), "Studio A", List.of("EQ1"), 10));
    }

    @Test
    void rejectsUnauthorisedMaintenanceLogAccess() throws Exception {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        User instructor = facade.registerUser(administrator, User.Role.INSTRUCTOR, "I1", "Ishan");
        User member = facade.registerUser(administrator, User.Role.MEMBER, "M1", "Maya");

        assertThrows(UnauthorizedAccessException.class,
                () -> facade.viewMaintenanceRequests(instructor));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.viewMaintenanceRequests(member));
    }

    @Test
    void rejectsInvalidMaintenanceTransitionsThroughFacadeWorkflow() throws Exception {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        User instructor = facade.registerUser(administrator, User.Role.INSTRUCTOR, "I1", "Ishan");
        facade.addEquipment(administrator, "EQ1", "Spin Bike", "Studio A");
        facade.reportFault(instructor, "R1", "EQ1", "Resistance failure",
                MaintenanceRequest.Urgency.HIGH);

        assertThrows(IllegalStateException.class,
                () -> facade.completeMaintenance(administrator, "R1"));
        facade.assignMaintenance(administrator, "R1", "Technician A");
        assertThrows(IllegalStateException.class,
                () -> facade.assignMaintenance(administrator, "R1", "Technician B"));
        facade.completeMaintenance(administrator, "R1");
        assertThrows(IllegalStateException.class,
                () -> facade.completeMaintenance(administrator, "R1"));
    }

    @Test
    void resetsPreventativeAlertAfterCompletedMaintenanceForLaterCycle() throws Exception {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        User instructor = facade.registerUser(administrator, User.Role.INSTRUCTOR, "I1", "Ishan");
        Equipment equipment = facade.addEquipment(administrator, "EQ1", "Spin Bike", "Studio A");
        facade.scheduleSession(instructor, "S1", "Spin", at(9), at(10),
                "Studio A", List.of("EQ1"), 10);

        facade.recordSessionEquipmentUsage(instructor, "S1", "EQ1", 100.0);
        assertTrue(facade.checkPreventativeMaintenance(administrator, "EQ1"));
        assertTrue(facade.checkPreventativeMaintenance(administrator, "EQ1"));
        assertEquals(1, administrator.getNotifications().size());

        facade.reportFault(instructor, "R1", "EQ1", "Scheduled maintenance",
                MaintenanceRequest.Urgency.MEDIUM);
        facade.assignMaintenance(administrator, "R1", "Technician A");
        facade.completeMaintenance(administrator, "R1");

        assertFalse(facade.checkPreventativeMaintenance(administrator, "EQ1"));
        assertEquals(2, administrator.getNotifications().size());

        facade.recordSessionEquipmentUsage(instructor, "S1", "EQ1", 99.9);
        assertFalse(facade.checkPreventativeMaintenance(administrator, "EQ1"));
        assertEquals(2, administrator.getNotifications().size());

        facade.recordSessionEquipmentUsage(instructor, "S1", "EQ1", 0.1);
        assertTrue(facade.checkPreventativeMaintenance(administrator, "EQ1"));
        assertTrue(facade.checkPreventativeMaintenance(administrator, "EQ1"));

        assertEquals(200.0, equipment.getCumulativeUsageHours(), 0.000_001);
        assertEquals(3, administrator.getNotifications().size());
        assertTrue(administrator.getNotifications().get(2).contains("Preventative maintenance"));
    }

    private static LocalDateTime at(int hour) {
        return at(hour, 0);
    }

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 5, hour, minute);
    }
}
