package iwfc.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FitnessSessionTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 5, 9, 0);

    @Test
    void createsValidActiveSession() {
        FitnessSession session = session("S1", START, START.plusHours(1), 2, List.of("EQ1"));
        assertEquals("S1", session.getId());
        assertTrue(session.isActive());
        assertTrue(session.hasCapacity());
    }

    @Test
    void rejectsInvalidIdentityAndCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new FitnessSession(" ", "Yoga", "I1", "Studio A", START,
                        START.plusHours(1), 2, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> session("S1", START, START.plusHours(1), 0, List.of()));
    }

    @Test
    void rejectsInvalidTimeRange() {
        assertThrows(IllegalArgumentException.class,
                () -> session("S1", START, START, 2, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> session("S1", START, START.minusMinutes(1), 2, List.of()));
    }

    @Test
    void defensivelyCopiesAndProtectsEquipmentIds() {
        List<String> equipmentIds = new ArrayList<>(List.of("EQ1"));
        FitnessSession session = session("S1", START, START.plusHours(1), 2, equipmentIds);
        equipmentIds.add("EQ2");
        assertEquals(List.of("EQ1"), session.getEquipmentIds());
        assertThrows(UnsupportedOperationException.class,
                () -> session.getEquipmentIds().add("EQ3"));
    }

    @Test
    void validatesEquipmentIdsInsideCollection() {
        assertThrows(IllegalArgumentException.class,
                () -> session("S1", START, START.plusHours(1), 2, List.of(" ")));
        assertThrows(IllegalArgumentException.class,
                () -> session("S1", START, START.plusHours(1), 2, List.of("EQ1", "EQ1")));
    }

    @Test
    void detectsOverlappingIntervals() {
        FitnessSession existing = session("S1", START, START.plusHours(2), 2, List.of("EQ1"));
        FitnessSession partial = session("S2", START.plusHours(1), START.plusHours(3), 2, List.of("EQ2"));
        FitnessSession contained = session("S3", START.plusMinutes(15), START.plusMinutes(45), 2, List.of());
        assertTrue(existing.overlaps(partial));
        assertTrue(existing.overlaps(contained));
    }

    @Test
    void treatsAdjacentIntervalsAsNonOverlapping() {
        FitnessSession first = session("S1", START, START.plusHours(1), 2, List.of());
        FitnessSession second = session("S2", START.plusHours(1), START.plusHours(2), 2, List.of());
        assertFalse(first.overlaps(second));
    }

    @Test
    void managesMemberCapacityWithoutDuplicateIds() {
        FitnessSession session = session("S1", START, START.plusHours(1), 2, List.of());
        assertTrue(session.addMember("M1"));
        assertFalse(session.addMember("M1"));
        assertTrue(session.addMember("M2"));
        assertTrue(session.containsMember("M1"));
        assertFalse(session.hasCapacity());
        assertThrows(UnsupportedOperationException.class,
                () -> session.getBookedMemberIds().add("M3"));
    }

    @Test
    void deactivatesSession() {
        FitnessSession session = session("S1", START, START.plusHours(1), 2, List.of());
        session.deactivate();
        assertFalse(session.isActive());
    }

    private static FitnessSession session(String id, LocalDateTime start, LocalDateTime end,
                                          int capacity, List<String> equipmentIds) {
        return new FitnessSession(id, "Yoga", "I1", "Studio A", start, end, capacity, equipmentIds);
    }
}
