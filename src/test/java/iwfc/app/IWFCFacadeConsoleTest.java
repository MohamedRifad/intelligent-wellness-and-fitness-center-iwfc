package iwfc.app;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IWFCFacadeConsoleTest {
    @Test
    void validatesInputAndRunsPresentationWorkflowWithoutCrashing() {
        String input = String.join(System.lineSeparator(),
                "", "A1", "Asha", "invalid", "1", "1", "0") + System.lineSeparator();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            IWFCFacade.runConsole(scanner, output);
        }

        String transcript = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(transcript.contains("Intelligent Wellness and Fitness Center"));
        assertTrue(transcript.contains("This value is required"));
        assertTrue(transcript.contains("Enter 1 to run the demo or 0 to exit"));
        assertTrue(transcript.contains("[SUCCESS] Guided IWFC workflow completed."));
        assertTrue(transcript.contains("Booking confirmed:"));
        assertTrue(transcript.contains("Wellness tip:"));
        assertTrue(transcript.contains("Schedule notice:"));
        assertTrue(transcript.contains("Preventative maintenance due"));
        assertTrue(transcript.contains("[STATUS] Equipment: OPERATIONAL"));
        assertTrue(transcript.contains("[STATUS] Maintenance request: COMPLETED"));
        assertTrue(transcript.contains("Demonstration could not continue"));
        assertTrue(transcript.contains("The console remains available"));
        assertTrue(transcript.contains("Thank you for using IWFC."));
    }

    @Test
    void handlesEndOfInputDuringSetupSafely() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (Scanner scanner = new Scanner("");
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            IWFCFacade.runConsole(scanner, output);
        }

        assertTrue(bytes.toString(StandardCharsets.UTF_8)
                .contains("Input ended. IWFC closed safely."));
    }

    @Test
    void loadsAndDisplaysCompleteSampleDataAndRejectsSecondLoad() {
        String input = String.join(System.lineSeparator(),
                "A1", "Rifad", "2", "2", "3", "0") + System.lineSeparator();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            IWFCFacade.runConsole(scanner, output);
        }

        String transcript = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(transcript.contains("[SUCCESS] Sample data loaded."));
        assertTrue(transcript.contains("[ERROR] Sample data already loaded"));
        assertTrue(transcript.contains("Equipment:   ID | Name | Location | Status | Active | Usage hrs"));
        assertTrue(transcript.contains("Sessions:    ID | Title | Date | Time | Location | Booked/Capacity"));
        assertTrue(transcript.contains("Maintenance: ID | Equipment | Description | Urgency | Status"));
        assertTrue(transcript.contains("S1 | Boxing Class"));
        assertTrue(transcript.contains("Training Room | 2/2"));
        assertTrue(transcript.contains("S4-W1 | Weekly Stretch"));
        assertTrue(transcript.contains("S4-W2 | Weekly Stretch"));
        assertTrue(transcript.contains("S4-W3 | Weekly Stretch"));
        assertTrue(transcript.contains("S4-W4 | Weekly Stretch"));
    }

    @Test
    void currentDataViewExplainsHowToLoadAnEmptyDataset() {
        String input = String.join(System.lineSeparator(),
                "A1", "Rifad", "3", "0") + System.lineSeparator();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            IWFCFacade.runConsole(scanner, output);
        }

        assertTrue(bytes.toString(StandardCharsets.UTF_8)
                .contains("No data yet - choose option 2 to load sample data."));
    }
}
