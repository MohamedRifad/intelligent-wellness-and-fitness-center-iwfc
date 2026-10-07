package iwfc.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {
    @Test
    void exposesPolymorphicRoleInformation() {
        User administrator = new Administrator("A1", "Asha");
        User instructor = new Instructor("I1", "Imran");
        User member = new Member("M1", "Maya");

        assertEquals(User.Role.ADMINISTRATOR, administrator.getRole());
        assertEquals(User.Role.INSTRUCTOR, instructor.getRole());
        assertEquals(User.Role.MEMBER, member.getRole());
        assertTrue(administrator.getRoleDescription().contains("maintenance"));
        assertTrue(instructor.getRoleDescription().contains("Schedules"));
        assertTrue(member.getRoleDescription().contains("sessions"));
    }

    @Test
    void validatesIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new Member(" ", "Maya"));
        assertThrows(IllegalArgumentException.class, () -> new Member("M1", " "));
    }

    @Test
    void deactivatesAccountWithoutChangingIdentity() {
        User member = new Member("M1", "Maya");
        member.deactivate();
        assertFalse(member.isActive());
        assertEquals("M1", member.getId());
    }

    @Test
    void storesTrimmedNotificationsInReadOnlyView() {
        User member = new Member("M1", "Maya");
        member.receiveNotification("  Yoga moved to Studio B  ");
        assertEquals("Yoga moved to Studio B", member.getNotifications().getFirst());
        assertThrows(UnsupportedOperationException.class,
                () -> member.getNotifications().add("Unexpected mutation"));
    }

    @Test
    void rejectsBlankNotification() {
        User member = new Member("M1", "Maya");
        assertThrows(IllegalArgumentException.class, () -> member.receiveNotification(" "));
    }
}
