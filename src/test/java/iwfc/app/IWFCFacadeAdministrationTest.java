package iwfc.app;

import iwfc.domain.Administrator;
import iwfc.domain.Equipment;
import iwfc.domain.FitnessSession;
import iwfc.domain.MaintenanceRequest;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import iwfc.exception.UnauthorizedAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IWFCFacadeAdministrationTest {
    @Test
    void initializesFirstAdministratorThroughFacade() throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();

        User administrator = facade.initializeAdministrator("A1", "Asha");

        assertInstanceOf(Administrator.class, administrator);
        assertEquals(User.Role.ADMINISTRATOR, administrator.getRole());
        assertEquals(administrator, facade.viewUsers(administrator).getFirst());
    }

    @Test
    void preventsSecondInitialization() throws DuplicateDataException {
        IWFCFacade facade = new IWFCFacade();
        facade.initializeAdministrator("A1", "Asha");

        assertThrows(DuplicateDataException.class,
                () -> facade.initializeAdministrator("A2", "Second Administrator"));
    }

    @ParameterizedTest
    @EnumSource(User.Role.class)
    void administratorRegistersEveryRoleUsingFactory(User.Role role)
            throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");

        User created = facade.registerUser(administrator, role, "U-" + role.name(), "Created User");

        assertEquals(role, created.getRole());
        assertTrue(facade.viewUsers(administrator).contains(created));
    }

    @Test
    void rejectsDuplicateUserId() throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        facade.registerUser(administrator, User.Role.MEMBER, "M1", "Maya");

        assertThrows(DuplicateDataException.class,
                () -> facade.registerUser(administrator, User.Role.MEMBER, "M1", "Mina"));
    }

    @Test
    void rejectsBlankUserInput() throws DuplicateDataException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");

        assertThrows(IllegalArgumentException.class,
                () -> facade.registerUser(administrator, User.Role.MEMBER, " ", "Maya"));
        assertThrows(IllegalArgumentException.class,
                () -> facade.registerUser(administrator, User.Role.MEMBER, "M1", " "));
        assertThrows(NullPointerException.class,
                () -> facade.registerUser(administrator, null, "M1", "Maya"));
    }

    @Test
    void administratorListsAndDeactivatesUser() throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        User member = facade.registerUser(administrator, User.Role.MEMBER, "M1", "Maya");

        facade.deactivateUser(administrator, member.getId());

        assertFalse(member.isActive());
        assertEquals(2, facade.viewUsers(administrator).size());
        assertThrows(UnsupportedOperationException.class,
                () -> facade.viewUsers(administrator).add(member));
    }

    @Test
    void rejectsUnknownUserId() throws DuplicateDataException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");

        assertThrows(IllegalArgumentException.class,
                () -> facade.deactivateUser(administrator, "UNKNOWN"));
        assertThrows(IllegalArgumentException.class,
                () -> facade.deactivateUser(administrator, " "));
    }

    @ParameterizedTest
    @EnumSource(value = User.Role.class, names = "ADMINISTRATOR", mode = EnumSource.Mode.EXCLUDE)
    void rejectsNonAdministratorUserManagement(User.Role role)
            throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        User nonAdministrator = facade.registerUser(administrator, role, "U1", "Other User");

        assertThrows(UnauthorizedAccessException.class,
                () -> facade.registerUser(nonAdministrator, User.Role.MEMBER, "M2", "Mina"));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.viewUsers(nonAdministrator));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.deactivateUser(nonAdministrator, administrator.getId()));
    }

    @Test
    void rejectsNullUnregisteredAndInactiveAdministrators()
            throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();
        User registeredAdministrator = facade.initializeAdministrator("A1", "Asha");
        User fabricatedAdministrator = new Administrator("A1", "Impostor");

        assertThrows(UnauthorizedAccessException.class, () -> facade.viewUsers(null));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.viewUsers(fabricatedAdministrator));

        facade.deactivateUser(registeredAdministrator, registeredAdministrator.getId());
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.viewUsers(registeredAdministrator));
    }

    @Test
    void administratorAddsUpdatesListsAndDeactivatesEquipment()
            throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");

        Equipment equipment = facade.addEquipment(administrator, "EQ1", "Spin Bike", "Studio A");
        facade.updateEquipment(administrator, "EQ1", "Premium Spin Bike", "Studio B");

        assertEquals("EQ1", equipment.getId());
        assertEquals("Premium Spin Bike", equipment.getName());
        assertEquals("Studio B", equipment.getLocation());
        assertEquals(equipment, facade.viewEquipment(administrator).getFirst());

        facade.deactivateEquipment(administrator, "EQ1");
        assertFalse(equipment.isActive());
    }

    @Test
    void rejectsDuplicateEquipmentId() throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        facade.addEquipment(administrator, "EQ1", "Spin Bike", "Studio A");

        assertThrows(DuplicateDataException.class,
                () -> facade.addEquipment(administrator, "EQ1", "Rower", "Cardio Zone"));
    }

    @Test
    void rejectsBlankEquipmentInput() throws DuplicateDataException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");

        assertThrows(IllegalArgumentException.class,
                () -> facade.addEquipment(administrator, " ", "Bike", "Studio A"));
        assertThrows(IllegalArgumentException.class,
                () -> facade.addEquipment(administrator, "EQ1", " ", "Studio A"));
        assertThrows(IllegalArgumentException.class,
                () -> facade.addEquipment(administrator, "EQ1", "Bike", " "));
    }

    @Test
    void rejectsUnknownEquipmentId() throws DuplicateDataException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");

        assertThrows(IllegalArgumentException.class,
                () -> facade.updateEquipment(administrator, "UNKNOWN", "Bike", "Studio A"));
        assertThrows(IllegalArgumentException.class,
                () -> facade.deactivateEquipment(administrator, "UNKNOWN"));
        assertThrows(IllegalArgumentException.class,
                () -> facade.updateEquipment(administrator, " ", "Bike", "Studio A"));
    }

    @ParameterizedTest
    @EnumSource(value = User.Role.class, names = "ADMINISTRATOR", mode = EnumSource.Mode.EXCLUDE)
    void rejectsNonAdministratorEquipmentOperations(User.Role role)
            throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        User nonAdministrator = facade.registerUser(administrator, role, "U1", "Other User");
        facade.addEquipment(administrator, "EQ1", "Bike", "Studio A");

        assertThrows(UnauthorizedAccessException.class,
                () -> facade.addEquipment(nonAdministrator, "EQ2", "Rower", "Cardio Zone"));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.updateEquipment(nonAdministrator, "EQ1", "Updated", "Studio B"));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.deactivateEquipment(nonAdministrator, "EQ1"));
        assertThrows(UnauthorizedAccessException.class,
                () -> facade.viewEquipment(nonAdministrator));
    }

    @Test
    void equipmentListIsReadOnly() throws DuplicateDataException, UnauthorizedAccessException {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Asha");
        Equipment equipment = facade.addEquipment(administrator, "EQ1", "Bike", "Studio A");

        assertThrows(UnsupportedOperationException.class,
                () -> facade.viewEquipment(administrator).add(equipment));
    }

    @Test
    void sampleDataCreatesEveryRequiredRecordAndState() throws Exception {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Rifad");

        facade.loadSampleData(administrator);

        assertEquals(8, facade.viewUsers(administrator).size());
        List<Equipment> equipment = facade.viewEquipment(administrator);
        assertEquals(8, equipment.size());
        Equipment secondEquipment = equipment.stream()
                .filter(item -> item.getId().equals("E2")).findFirst().orElseThrow();
        Equipment thirdEquipment = equipment.stream()
                .filter(item -> item.getId().equals("E3")).findFirst().orElseThrow();
        assertFalse(secondEquipment.isActive());
        assertEquals(98.0, thirdEquipment.getCumulativeUsageHours());

        List<FitnessSession> sessions = facade.viewAllSessions(administrator);
        assertEquals(7, sessions.size());
        FitnessSession firstSession = sessions.stream()
                .filter(session -> session.getId().equals("S1")).findFirst().orElseThrow();
        assertEquals(2, firstSession.getBookedMemberIds().size());
        assertEquals(firstSession.getCapacity(), firstSession.getBookedMemberIds().size());
        List<FitnessSession> weekly = sessions.stream()
                .filter(session -> session.getId().startsWith("S4-W"))
                .sorted(Comparator.comparing(FitnessSession::getId))
                .toList();
        assertEquals(List.of("S4-W1", "S4-W2", "S4-W3", "S4-W4"),
                weekly.stream().map(FitnessSession::getId).toList());
        assertEquals(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY)),
                weekly.getFirst().getStartTime().toLocalDate());
        for (int index = 1; index < weekly.size(); index++) {
            assertEquals(weekly.get(index - 1).getStartTime().plusDays(7),
                    weekly.get(index).getStartTime());
        }

        List<MaintenanceRequest> requests = facade.viewMaintenanceRequests(administrator);
        assertEquals(2, requests.size());
        assertEquals(MaintenanceRequest.Status.PENDING, requests.stream()
                .filter(request -> request.getId().equals("R1")).findFirst().orElseThrow().getStatus());
        assertEquals(MaintenanceRequest.Status.ASSIGNED, requests.stream()
                .filter(request -> request.getId().equals("R2")).findFirst().orElseThrow().getStatus());
    }

    @Test
    void secondSampleLoadIsRejectedWithoutChangingExistingData() throws Exception {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Rifad");
        facade.loadSampleData(administrator);

        DuplicateDataException exception = assertThrows(
                DuplicateDataException.class, () -> facade.loadSampleData(administrator));

        assertEquals("Sample data already loaded", exception.getMessage());
        assertEquals(8, facade.viewUsers(administrator).size());
        assertEquals(8, facade.viewEquipment(administrator).size());
        assertEquals(7, facade.viewAllSessions(administrator).size());
        assertEquals(2, facade.viewMaintenanceRequests(administrator).size());
    }

    @Test
    void allSessionListingRequiresAnAdministrator() throws Exception {
        IWFCFacade facade = new IWFCFacade();
        User administrator = facade.initializeAdministrator("A1", "Rifad");
        User member = facade.registerUser(administrator, User.Role.MEMBER, "M9", "Reader");

        assertThrows(UnauthorizedAccessException.class, () -> facade.viewAllSessions(member));
    }
}
