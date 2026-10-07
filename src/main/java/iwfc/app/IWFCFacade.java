package iwfc.app;

import iwfc.domain.Equipment;
import iwfc.domain.FitnessSession;
import iwfc.domain.MaintenanceRequest;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import iwfc.exception.InvalidBookingException;
import iwfc.exception.UnauthorizedAccessException;
import iwfc.pattern.EntityFactory;
import iwfc.repository.GenericRepository;
import iwfc.service.BookingService;
import iwfc.service.MaintenanceService;

import java.io.PrintStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/** Structural Facade and console entry point for IWFC. */
public final class IWFCFacade {
    private final GenericRepository<User> userRepository = new GenericRepository<>(User::getId);
    private final GenericRepository<Equipment> equipmentRepository = new GenericRepository<>(Equipment::getId);
    private final GenericRepository<FitnessSession> sessionRepository = new GenericRepository<>(FitnessSession::getId);
    private final GenericRepository<MaintenanceRequest> maintenanceRepository =
            new GenericRepository<>(MaintenanceRequest::getId);
    private final EntityFactory entityFactory = new EntityFactory();
    private final BookingService bookingService = new BookingService(
            userRepository, equipmentRepository, sessionRepository, LocalTime.of(6, 0), LocalTime.of(22, 0));
    private final MaintenanceService maintenanceService = new MaintenanceService(
            userRepository, equipmentRepository, sessionRepository, maintenanceRepository);

    public EntityFactory getEntityFactory() { return entityFactory; }
    public BookingService getBookingService() { return bookingService; }
    public MaintenanceService getMaintenanceService() { return maintenanceService; }

    /**
     * Creates the first Administrator through the Factory. This operation is
     * available only while the user repository is empty; all later account
     * creation requires an authorized Administrator.
     */
    public User initializeAdministrator(String id, String name) throws DuplicateDataException {
        if (!userRepository.findAll().isEmpty()) {
            throw new DuplicateDataException("IWFC has already been initialized");
        }
        User administrator = entityFactory.createUser(User.Role.ADMINISTRATOR, id, name);
        userRepository.add(administrator);
        maintenanceService.registerObserver(administrator);
        return administrator;
    }

    public User registerUser(User actor, User.Role role, String id, String name)
            throws UnauthorizedAccessException, DuplicateDataException {
        requireAdministrator(actor);
        User user = entityFactory.createUser(role, id, name);
        userRepository.add(user);
        maintenanceService.registerObserver(user);
        return user;
    }

    public List<User> viewUsers(User actor) throws UnauthorizedAccessException {
        requireAdministrator(actor);
        return userRepository.findAll();
    }

    public void deactivateUser(User actor, String userId) throws UnauthorizedAccessException {
        requireAdministrator(actor);
        requireUser(userId).deactivate();
    }

    public Equipment addEquipment(User actor, String id, String name, String location)
            throws UnauthorizedAccessException, DuplicateDataException {
        requireAdministrator(actor);
        Equipment equipment = entityFactory.createEquipment(id, name, location);
        equipmentRepository.add(equipment);
        return equipment;
    }

    public void updateEquipment(User actor, String equipmentId, String name, String location)
            throws UnauthorizedAccessException {
        requireAdministrator(actor);
        requireEquipment(equipmentId).updateDetails(name, location);
    }

    public void deactivateEquipment(User actor, String equipmentId)
            throws UnauthorizedAccessException {
        requireAdministrator(actor);
        requireEquipment(equipmentId).deactivate();
    }

    public List<Equipment> viewEquipment(User actor) throws UnauthorizedAccessException {
        requireAdministrator(actor);
        return equipmentRepository.findAll();
    }

    /** Returns every scheduled session to an active registered Administrator. */
    public List<FitnessSession> viewAllSessions(User actor) throws UnauthorizedAccessException {
        requireAdministrator(actor);
        return sessionRepository.findAll();
    }

    public FitnessSession scheduleSession(User actor, String sessionId, String title,
                                          LocalDateTime startTime, LocalDateTime endTime,
                                          String studio, Collection<String> equipmentIds,
                                          int capacity)
            throws UnauthorizedAccessException, InvalidBookingException, DuplicateDataException {
        User instructor = requireRole(actor, User.Role.INSTRUCTOR);
        return bookingService.scheduleSession(sessionId, title, instructor.getId(), studio,
                startTime, endTime, capacity, equipmentIds);
    }

    /** Creates an atomic weekly session series for an active registered Instructor. */
    public List<FitnessSession> scheduleWeeklySessions(User actor, String baseId, String title,
                                                        LocalDateTime firstStart,
                                                        LocalDateTime firstEnd, String studio,
                                                        Collection<String> equipmentIds,
                                                        int capacity, int weeks)
            throws UnauthorizedAccessException, InvalidBookingException, DuplicateDataException {
        User instructor = requireRole(actor, User.Role.INSTRUCTOR);
        return bookingService.scheduleWeeklySessions(baseId, title, instructor.getId(), studio,
                firstStart, firstEnd, capacity, equipmentIds, weeks);
    }

    public void bookSession(User actor, String sessionId)
            throws UnauthorizedAccessException, InvalidBookingException {
        User member = requireRole(actor, User.Role.MEMBER);
        bookingService.bookSession(member.getId(), sessionId);
    }

    public List<FitnessSession> viewAvailableSessions(User actor)
            throws UnauthorizedAccessException {
        requireRole(actor, User.Role.MEMBER);
        return bookingService.findAvailableSessions();
    }

    public List<FitnessSession> viewMyBookings(User actor)
            throws UnauthorizedAccessException, InvalidBookingException {
        User member = requireRole(actor, User.Role.MEMBER);
        return bookingService.findSessionsForMember(member.getId());
    }

    public MaintenanceRequest reportFault(User actor, String requestId, String equipmentId,
                                          String description, MaintenanceRequest.Urgency urgency)
            throws UnauthorizedAccessException, DuplicateDataException {
        User instructor = requireRole(actor, User.Role.INSTRUCTOR);
        return maintenanceService.reportFault(
                requestId, equipmentId, description, urgency, instructor.getId());
    }

    public void assignMaintenance(User actor, String requestId, String assignee)
            throws UnauthorizedAccessException {
        requireAdministrator(actor);
        maintenanceService.assignRequest(requestId, assignee);
    }

    public void completeMaintenance(User actor, String requestId)
            throws UnauthorizedAccessException {
        requireAdministrator(actor);
        maintenanceService.completeRequest(requestId);
    }

    public List<MaintenanceRequest> viewMaintenanceRequests(User actor)
            throws UnauthorizedAccessException {
        requireAdministrator(actor);
        return maintenanceService.findAllRequests();
    }

    public void recordSessionEquipmentUsage(User actor, String sessionId,
                                            String equipmentId, double hours)
            throws UnauthorizedAccessException {
        User instructor = requireRole(actor, User.Role.INSTRUCTOR);
        maintenanceService.recordSessionEquipmentUsage(
                instructor.getId(), sessionId, equipmentId, hours);
    }

    public boolean checkPreventativeMaintenance(User actor, String equipmentId)
            throws UnauthorizedAccessException {
        requireAdministrator(actor);
        return maintenanceService.checkPreventativeMaintenance(equipmentId);
    }

    /** Loads the presentation dataset through the normal public Facade workflows. */
    public void loadSampleData(User administrator)
            throws UnauthorizedAccessException, DuplicateDataException, InvalidBookingException {
        requireAdministrator(administrator);
        rejectReservedIdConflicts();

        User firstInstructor = registerUser(
                administrator, User.Role.INSTRUCTOR, "I1", "Tharindu");
        User secondInstructor = registerUser(
                administrator, User.Role.INSTRUCTOR, "I2", "Nadeesha");
        User firstMember = registerUser(administrator, User.Role.MEMBER, "M1", "Ishara");
        User secondMember = registerUser(administrator, User.Role.MEMBER, "M2", "Mohamed");
        User thirdMember = registerUser(administrator, User.Role.MEMBER, "M3", "Anjali");
        User fourthMember = registerUser(administrator, User.Role.MEMBER, "M4", "Kavindu");
        User fifthMember = registerUser(administrator, User.Role.MEMBER, "M5", "Mark");

        addEquipment(administrator, "E1", "Elliptical", "Endurance Area");
        addEquipment(administrator, "E2", "Stair Climber", "Endurance Area");
        addEquipment(administrator, "E3", "Air Bike", "Training Room");
        addEquipment(administrator, "E4", "Treadmill", "Endurance Area");
        addEquipment(administrator, "E5", "Leg Press", "Weights Area");
        addEquipment(administrator, "E6", "Cable Machine", "Weights Area");
        addEquipment(administrator, "E7", "Boxing Bag", "Training Room");
        addEquipment(administrator, "E8", "Weight Scale", "Wellness Room");

        LocalDate nextTuesday = LocalDate.now().with(
                TemporalAdjusters.next(DayOfWeek.TUESDAY));
        scheduleSession(firstInstructor, "S1", "Boxing Class",
                nextTuesday.atTime(18, 0), nextTuesday.atTime(19, 0),
                "Training Room", List.of("E3", "E7"), 2);
        scheduleSession(firstInstructor, "S2", "Leg Day",
                nextTuesday.atTime(7, 0), nextTuesday.atTime(8, 0),
                "Weights Area", List.of("E5", "E6"), 8);
        scheduleSession(secondInstructor, "S3", "Cardio Mix",
                nextTuesday.atTime(17, 0), nextTuesday.atTime(18, 0),
                "Endurance Area", List.of("E1"), 6);
        scheduleWeeklySessions(secondInstructor, "S4", "Weekly Stretch",
                nextTuesday.atTime(6, 30), nextTuesday.atTime(7, 15),
                "Wellness Room", List.of(), 10, 4);

        bookSession(firstMember, "S1");
        bookSession(secondMember, "S1");
        bookSession(thirdMember, "S3");
        bookSession(fourthMember, "S3");
        bookSession(fifthMember, "S2");
        bookSession(thirdMember, "S4-W1");

        recordSessionEquipmentUsage(firstInstructor, "S1", "E3", 98.0);
        deactivateEquipment(administrator, "E2");
        reportFault(secondInstructor, "R1", "E4", "Treadmill belt slipping",
                MaintenanceRequest.Urgency.LOW);
        reportFault(secondInstructor, "R2", "E8", "Weight scale not reading",
                MaintenanceRequest.Urgency.MEDIUM);
        assignMaintenance(administrator, "R2", "Technician");
    }

    private void rejectReservedIdConflicts() throws DuplicateDataException {
        boolean conflict = List.of("I1", "I2", "M1", "M2", "M3", "M4", "M5").stream()
                .anyMatch(userRepository::containsId)
                || List.of("E1", "E2", "E3", "E4", "E5", "E6", "E7", "E8").stream()
                .anyMatch(equipmentRepository::containsId)
                || List.of("S1", "S2", "S3", "S4-W1", "S4-W2", "S4-W3", "S4-W4").stream()
                .anyMatch(sessionRepository::containsId)
                || List.of("R1", "R2").stream().anyMatch(maintenanceRepository::containsId);
        if (conflict) {
            throw new DuplicateDataException("Sample data already loaded");
        }
    }

    private User requireAdministrator(User actor) throws UnauthorizedAccessException {
        return requireRole(actor, User.Role.ADMINISTRATOR);
    }

    private User requireRole(User actor, User.Role requiredRole) throws UnauthorizedAccessException {
        if (actor == null) {
            throw new UnauthorizedAccessException("An authenticated user is required");
        }
        User registeredActor = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedAccessException("User is not registered with IWFC"));
        if (registeredActor != actor) {
            throw new UnauthorizedAccessException("Authenticated user does not match the registered account");
        }
        if (!registeredActor.isActive()) {
            throw new UnauthorizedAccessException("Inactive users cannot perform operations");
        }
        if (registeredActor.getRole() != requiredRole) {
            throw new UnauthorizedAccessException(requiredRole + " access is required");
        }
        return registeredActor;
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user ID: " + userId));
    }

    private Equipment requireEquipment(String equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown equipment ID: " + equipmentId));
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            runConsole(scanner, System.out);
        }
    }

    static void runConsole(Scanner scanner, PrintStream output) {
        Objects.requireNonNull(scanner, "Scanner is required");
        Objects.requireNonNull(output, "Output is required");
        IWFCFacade facade = new IWFCFacade();

        output.println("============================================================");
        output.println(" Intelligent Wellness and Fitness Center (IWFC)");
        output.println(" Presentation Console");
        output.println("============================================================");
        output.println("One-time Administrator setup");

        User administrator = null;
        while (administrator == null) {
            String administratorId = readRequired(scanner, output, "Administrator ID: ");
            if (administratorId == null) {
                output.println("Input ended. IWFC closed safely.");
                return;
            }
            String administratorName = readRequired(scanner, output, "Administrator name: ");
            if (administratorName == null) {
                output.println("Input ended. IWFC closed safely.");
                return;
            }
            try {
                administrator = facade.initializeAdministrator(administratorId, administratorName);
                output.println("[SUCCESS] Administrator account created for "
                        + administrator.getName() + ".");
            } catch (DuplicateDataException | IllegalArgumentException exception) {
                output.println("[ERROR] " + exception.getMessage());
            }
        }

        boolean running = true;
        while (running) {
            output.println();
            output.println("1 - Run guided IWFC workflow demonstration");
            output.println("2 - Load sample data");
            output.println("3 - View current data");
            output.println("0 - Exit");
            output.print("Select an option: ");
            if (!scanner.hasNextLine()) {
                output.println();
                output.println("Input ended. IWFC closed safely.");
                return;
            }

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> runGuidedDemonstration(facade, administrator, output);
                case "2" -> loadSampleDataFromConsole(facade, administrator, output);
                case "3" -> printCurrentData(facade, administrator, output);
                case "0" -> running = false;
                default -> output.println(
                        "[ERROR] Enter 1 to run the demo or 0 to exit, or choose 2/3 for data options.");
            }
        }
        output.println("Thank you for using IWFC.");
    }

    private static String readRequired(Scanner scanner, PrintStream output, String prompt) {
        while (true) {
            output.print(prompt);
            if (!scanner.hasNextLine()) return null;
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) return value;
            output.println("[ERROR] This value is required. Please try again.");
        }
    }

    private static void runGuidedDemonstration(IWFCFacade facade, User administrator,
                                               PrintStream output) {
        output.println();
        output.println("--- Guided IWFC workflow ---");
        try {
            output.println("1. Registering demonstration Instructor and Member...");
            User instructor = facade.registerUser(
                    administrator, User.Role.INSTRUCTOR, "DEMO-I1", "Dilan Fernando");
            User member = facade.registerUser(
                    administrator, User.Role.MEMBER, "DEMO-M1", "Sajini Perera");

            output.println("2. Adding fitness equipment...");
            Equipment equipment = facade.addEquipment(
                    administrator, "DEMO-EQ1", "Cross Trainer", "Activity Room");

            output.println("3. Scheduling a session...");
            LocalDate demonstrationDate = LocalDate.now().plusDays(1);
            FitnessSession session = facade.scheduleSession(
                    instructor, "DEMO-S1", "Evening Conditioning",
                    demonstrationDate.atTime(9, 0), demonstrationDate.atTime(10, 0),
                    "Activity Room", List.of(equipment.getId()), 12);

            output.println("4. Booking the Member into the session...");
            facade.bookSession(member, session.getId());
            output.println("   Member notification: " + member.getNotifications().get(0));
            output.println("   Member notification: " + member.getNotifications().get(1));

            output.println("5. Recording session-equipment usage...");
            facade.recordSessionEquipmentUsage(
                    instructor, session.getId(), equipment.getId(), 2.5);
            facade.recordSessionEquipmentUsage(
                    instructor, session.getId(), equipment.getId(), 97.5);
            output.println("   Administrator notification: "
                    + administrator.getNotifications().getLast());

            output.println("6. Reporting an equipment fault...");
            MaintenanceRequest request = facade.reportFault(
                    instructor, "DEMO-R1", equipment.getId(), "Drive mechanism noise",
                    MaintenanceRequest.Urgency.HIGH);
            output.println("   Member notification: " + member.getNotifications().getLast());

            output.println("7. Administrator reviewing and assigning maintenance...");
            facade.viewMaintenanceRequests(administrator);
            facade.assignMaintenance(administrator, request.getId(), "Technician A");
            output.println("   Instructor notification: "
                    + instructor.getNotifications().getLast());

            output.println("8. Administrator completing maintenance...");
            facade.completeMaintenance(administrator, request.getId());
            output.println("   Instructor notification: "
                    + instructor.getNotifications().getLast());

            output.println("[STATUS] Session: " + session.getTitle()
                    + ", booked members: " + session.getBookedMemberIds().size());
            output.println("[STATUS] Equipment: " + equipment.getStatus()
                    + ", usage: " + equipment.getCumulativeUsageHours() + " hours");
            output.println("[STATUS] Maintenance request: " + request.getStatus());
            output.println("[SUCCESS] Guided IWFC workflow completed.");
        } catch (Exception exception) {
            output.println("[ERROR] Demonstration could not continue: " + exception.getMessage());
            output.println("[STATUS] The console remains available; select another option.");
        }
    }

    private static void loadSampleDataFromConsole(IWFCFacade facade, User administrator,
                                                  PrintStream output) {
        try {
            facade.loadSampleData(administrator);
            output.println("[SUCCESS] Sample data loaded.");
        } catch (Exception exception) {
            output.println("[ERROR] " + exception.getMessage());
        }
    }

    private static void printCurrentData(IWFCFacade facade, User administrator,
                                         PrintStream output) {
        try {
            List<Equipment> equipment = facade.viewEquipment(administrator);
            List<FitnessSession> sessions = facade.viewAllSessions(administrator);
            List<MaintenanceRequest> requests = facade.viewMaintenanceRequests(administrator);
            if (equipment.isEmpty() && sessions.isEmpty() && requests.isEmpty()) {
                output.println("No data yet - choose option 2 to load sample data.");
                return;
            }

            output.println("Equipment:   ID | Name | Location | Status | Active | Usage hrs");
            equipment.forEach(item -> output.printf("             %s | %s | %s | %s | %s | %.1f%n",
                    item.getId(), item.getName(), item.getLocation(), item.getStatus(),
                    item.isActive(), item.getCumulativeUsageHours()));
            output.println("Sessions:    ID | Title | Date | Time | Location | Booked/Capacity");
            sessions.forEach(session -> output.printf("             %s | %s | %s | %s-%s | %s | %d/%d%n",
                    session.getId(), session.getTitle(), session.getStartTime().toLocalDate(),
                    session.getStartTime().toLocalTime(), session.getEndTime().toLocalTime(),
                    session.getStudio(), session.getBookedMemberIds().size(), session.getCapacity()));
            output.println("Maintenance: ID | Equipment | Description | Urgency | Status");
            requests.forEach(request -> output.printf("             %s | %s | %s | %s | %s%n",
                    request.getId(), request.getEquipmentId(), request.getDescription(),
                    request.getUrgency(), request.getStatus()));
        } catch (UnauthorizedAccessException exception) {
            output.println("[ERROR] " + exception.getMessage());
        }
    }
}
