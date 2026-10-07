package iwfc.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaintenanceRequestTest {
    @Test
    void startsPendingWithAuditInformation() {
        MaintenanceRequest request = request();
        assertEquals(MaintenanceRequest.Status.PENDING, request.getStatus());
        assertEquals(MaintenanceRequest.Urgency.HIGH, request.getUrgency());
        assertNull(request.getAssignedTo());
        assertNotNull(request.getCreatedAt());
        assertEquals(request.getCreatedAt(), request.getUpdatedAt());
    }

    @Test
    void assignsPendingRequest() {
        MaintenanceRequest request = request();
        request.assignTo("Technician A");
        assertEquals(MaintenanceRequest.Status.ASSIGNED, request.getStatus());
        assertEquals("Technician A", request.getAssignedTo());
    }

    @Test
    void completesAssignedRequest() {
        MaintenanceRequest request = request();
        request.assignTo("Technician A");
        request.complete();
        assertEquals(MaintenanceRequest.Status.COMPLETED, request.getStatus());
    }

    @Test
    void rejectsCompletionBeforeAssignment() {
        MaintenanceRequest request = request();
        assertThrows(IllegalStateException.class, request::complete);
    }

    @Test
    void rejectsRepeatedAssignmentAndCompletion() {
        MaintenanceRequest request = request();
        request.assignTo("Technician A");
        assertThrows(IllegalStateException.class, () -> request.assignTo("Technician B"));
        request.complete();
        assertThrows(IllegalStateException.class, request::complete);
    }

    @Test
    void validatesRequiredDataAndAssignee() {
        assertThrows(IllegalArgumentException.class,
                () -> new MaintenanceRequest(" ", "EQ1", "Fault", MaintenanceRequest.Urgency.HIGH, "I1"));
        assertThrows(NullPointerException.class,
                () -> new MaintenanceRequest("R1", "EQ1", "Fault", null, "I1"));
        MaintenanceRequest request = request();
        assertThrows(IllegalArgumentException.class, () -> request.assignTo(" "));
    }

    private static MaintenanceRequest request() {
        return new MaintenanceRequest("R1", "EQ1", "Resistance failure",
                MaintenanceRequest.Urgency.HIGH, "I1");
    }
}
