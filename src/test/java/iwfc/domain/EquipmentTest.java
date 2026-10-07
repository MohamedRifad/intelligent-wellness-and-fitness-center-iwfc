package iwfc.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentTest {
    @Test
    void startsOperationalActiveAndUnused() {
        Equipment equipment = new Equipment("EQ1", "Treadmill", "Cardio Zone");
        assertEquals(Equipment.Status.OPERATIONAL, equipment.getStatus());
        assertTrue(equipment.isActive());
        assertEquals(0.0, equipment.getCumulativeUsageHours());
        assertTrue(equipment.isAvailableForScheduling());
    }

    @Test
    void validatesRequiredConstructorData() {
        assertThrows(NullPointerException.class, () -> new Equipment(null, "Bike", "Studio A"));
        assertThrows(IllegalArgumentException.class, () -> new Equipment("EQ1", " ", "Studio A"));
        assertThrows(IllegalArgumentException.class, () -> new Equipment("EQ1", "Bike", " "));
    }

    @Test
    void updatesMutableDetailsButPreservesId() {
        Equipment equipment = new Equipment("EQ1", "Bike", "Studio A");
        equipment.updateDetails("Spin Bike", "Studio B");
        assertEquals("EQ1", equipment.getId());
        assertEquals("Spin Bike", equipment.getName());
        assertEquals("Studio B", equipment.getLocation());
    }

    @Test
    void accumulatesUsageHours() {
        Equipment equipment = new Equipment("EQ1", "Bike", "Studio A");
        equipment.addUsageHours(25.5);
        equipment.addUsageHours(10.0);
        assertEquals(35.5, equipment.getCumulativeUsageHours());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidUsageHours(double hours) {
        Equipment equipment = new Equipment("EQ1", "Bike", "Studio A");
        assertThrows(IllegalArgumentException.class, () -> equipment.addUsageHours(hours));
    }

    @Test
    void appliesInclusiveMaintenanceThreshold() {
        Equipment equipment = new Equipment("EQ1", "Bike", "Studio A");
        equipment.addUsageHours(100.0);
        assertFalse(equipment.requiresPreventativeMaintenance(100.1));
        assertTrue(equipment.requiresPreventativeMaintenance(100.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidMaintenanceThreshold(double threshold) {
        Equipment equipment = new Equipment("EQ1", "Bike", "Studio A");
        assertThrows(IllegalArgumentException.class,
                () -> equipment.requiresPreventativeMaintenance(threshold));
    }

    @Test
    void availabilityReflectsStatusAndActiveState() {
        Equipment equipment = new Equipment("EQ1", "Bike", "Studio A");
        equipment.markFaulty();
        assertFalse(equipment.isAvailableForScheduling());
        equipment.markUnderMaintenance();
        assertFalse(equipment.isAvailableForScheduling());
        equipment.markOperational();
        assertTrue(equipment.isAvailableForScheduling());
        equipment.deactivate();
        assertFalse(equipment.isAvailableForScheduling());
    }
}
