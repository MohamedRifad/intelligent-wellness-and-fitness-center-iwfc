package iwfc.app;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IWFCFacadeConsoleTest {
    @Test
    void automaticallyInitialisesDataAndValidatesMenuInputWithoutCrashing() {
        String input = String.join(System.lineSeparator(),
                "A1", "invalid", "0", "EXIT") + System.lineSeparator();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            IWFCFacade.runConsole(scanner, output);
        }

        String transcript = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(transcript.contains("Intelligent Wellness and Fitness Center"));
        assertTrue(transcript.contains("[STATUS] Initialising demonstration data..."));
        assertTrue(transcript.contains("[SUCCESS] Demonstration data loaded."));
        assertTrue(transcript.contains("[ERROR] Enter an Administrator menu option from 0 to 7."));
        assertTrue(transcript.contains("Thank you for using IWFC."));
    }

    @Test
    void handlesEndOfInputAtAccountSelectionSafely() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (Scanner scanner = new Scanner("");
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            IWFCFacade.runConsole(scanner, output);
        }

        assertTrue(bytes.toString(StandardCharsets.UTF_8)
                .contains("Input ended. IWFC closed safely."));
    }

    @Test
    void administratorMenusDisplayCompleteAutomaticallyLoadedSampleData() {
        String input = String.join(System.lineSeparator(),
                "A1", "1", "2", "3", "4", "0", "EXIT") + System.lineSeparator();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            IWFCFacade.runConsole(scanner, output);
        }

        String transcript = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(transcript.contains("A1 | ADMINISTRATOR | Rifad | ACTIVE"));
        assertTrue(transcript.contains("ID | Name | Location | Status | Active | Usage hrs"));
        assertTrue(transcript.contains("ID | Title | Date | Time | Studio | Booked/Capacity"));
        assertTrue(transcript.contains("ID | Equipment | Urgency | Status | Assignee"));
        assertTrue(transcript.contains("S1 | Boxing Class"));
        assertTrue(transcript.contains("S4-W1 | Weekly Stretch"));
        assertTrue(transcript.contains("S4-W2 | Weekly Stretch"));
        assertTrue(transcript.contains("S4-W3 | Weekly Stretch"));
        assertTrue(transcript.contains("S4-W4 | Weekly Stretch"));
    }

    @Test
    void startupAccountTableContainsEveryActiveSampleUserAndNoEmailColumn() {
        String input = String.join(System.lineSeparator(),
                "EXIT") + System.lineSeparator();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            IWFCFacade.runConsole(scanner, output);
        }

        String transcript = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(transcript.contains("A1 | ADMINISTRATOR | Rifad    | ACTIVE"));
        assertTrue(transcript.contains("I1 | INSTRUCTOR    | Tharindu | ACTIVE"));
        assertTrue(transcript.contains("I2 | INSTRUCTOR    | Nadeesha | ACTIVE"));
        assertTrue(transcript.contains("M5 | MEMBER        | Mark     | ACTIVE"));
        assertFalse(transcript.contains("EMAIL"));
    }

    @Test
    void administratorIsInitialAccountAndSecurityNoticeIsDisplayed() {
        String transcript = runConsole("EXIT");

        assertTrue(transcript.contains("--- Select a demonstration account ---"));
        assertTrue(transcript.contains(
                "Account selection is used for demonstration only."));
        assertTrue(transcript.contains("It is not password authentication."));
        assertFalse(transcript.contains("Administrator ID:"));
        assertFalse(transcript.contains("Administrator name:"));
    }

    @Test
    void failedAccountSelectionRetriesWithoutOpeningAMenu() {
        String transcript = runConsole(
                "UNKNOWN", "M4", "0", "EXIT");

        assertTrue(transcript.contains("[ERROR] Unknown registered user ID: UNKNOWN"));
        assertTrue(transcript.contains("[SUCCESS] Demonstration account selected: M4 - Kavindu (MEMBER)"));
        assertTrue(countOccurrences(transcript, "--- Member menu ---") == 1);
    }

    @Test
    void mainNotificationViewShowsOnlyTheSelectedAccountsHistory() {
        String transcript = runConsole(
                "M4", "4", "0", "EXIT");

        assertTrue(transcript.contains("Notifications for M4 - Kavindu"));
        assertTrue(transcript.contains("Booking confirmed: Cardio Mix"));
        assertFalse(transcript.contains("New fault R1 reported for equipment E4"));
    }

    @Test
    void completesDeterministicRoleBasedPresentationWorkflow() {
        String transcript = runConsole(
                "M4", "1", "2", "S2", "2", "S2", "3", "4", "0",
                "I1", "1", "4", "S2", "E5", "1.5",
                "5", "R3", "E5", "Leg press resistance problem", "HIGH",
                "6", "0",
                "A1", "4", "5", "R3", "Technician A", "6", "R3", "7", "0",
                "EXIT");

        assertTrue(transcript.contains("[SUCCESS] Demonstration account selected: M4 - Kavindu (MEMBER)"));
        assertTrue(transcript.contains("--- Member menu ---"));
        assertTrue(transcript.contains("[SUCCESS] Session booked: S2"));
        assertTrue(transcript.contains("Booking confirmed: Leg Day"));
        assertTrue(transcript.contains("Wellness tip:"));
        assertTrue(transcript.contains("[ERROR] Member is already booked into this session"));
        assertTrue(transcript.contains("[SUCCESS] Demonstration account selected: I1 - Tharindu (INSTRUCTOR)"));
        assertTrue(transcript.contains("--- Instructor menu ---"));
        assertTrue(transcript.contains("[SUCCESS] Equipment usage recorded."));
        assertTrue(transcript.contains("[SUCCESS] Fault reported: R3 | HIGH | PENDING"));
        assertTrue(transcript.contains("[SUCCESS] Demonstration account selected: A1 - Rifad (ADMINISTRATOR)"));
        assertTrue(transcript.contains("--- Administrator menu ---"));
        assertTrue(transcript.contains("R3 | E5 | HIGH | PENDING"));
        assertTrue(transcript.contains("[STATUS] Request R3: ASSIGNED | Equipment E5: UNDER_MAINTENANCE"));
        assertTrue(transcript.contains("[STATUS] Request R3: COMPLETED | Equipment E5: OPERATIONAL"));
        assertTrue(transcript.contains("Thank you for using IWFC."));
    }

    @Test
    void invalidRoleMenuChoiceAndEndOfInputReturnSafely() {
        String transcript = runConsole("A1", "invalid");

        assertTrue(transcript.contains("[ERROR] Enter an Administrator menu option from 0 to 7."));
        assertTrue(transcript.contains("Input ended. Returning safely."));
        assertTrue(transcript.contains("Input ended. IWFC closed safely."));
    }

    @Test
    void zeroIsTheOnlyRoleMenuLogoutAndExitIsReservedForAccountSelection() {
        String transcript = runConsole("A1", "EXIT", "0", "EXIT");

        assertTrue(transcript.contains("[ERROR] Enter an Administrator menu option from 0 to 7."));
        assertTrue(countOccurrences(transcript, "--- Select a demonstration account ---") == 2);
        assertTrue(transcript.contains("0 - Logout"));
        assertFalse(transcript.contains("8 - Logout"));
        assertFalse(transcript.contains("7 - Logout"));
        assertFalse(transcript.contains("5 - Logout"));
        assertTrue(transcript.contains("Thank you for using IWFC."));
    }

    private static String runConsole(String... inputLines) {
        String input = String.join(System.lineSeparator(), inputLines) + System.lineSeparator();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            IWFCFacade.runConsole(scanner, output);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}
