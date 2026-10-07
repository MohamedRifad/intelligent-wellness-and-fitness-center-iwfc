package iwfc.pattern;

import iwfc.domain.Administrator;
import iwfc.domain.Equipment;
import iwfc.domain.Instructor;
import iwfc.domain.Member;
import iwfc.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityFactoryTest {
    private final EntityFactory factory = new EntityFactory();

    @ParameterizedTest
    @EnumSource(User.Role.class)
    void createsEveryUserRole(User.Role role) {
        User user = factory.createUser(role, role.name() + "-1", "Test User");
        assertEquals(role, user.getRole());
        switch (role) {
            case ADMINISTRATOR -> assertInstanceOf(Administrator.class, user);
            case INSTRUCTOR -> assertInstanceOf(Instructor.class, user);
            case MEMBER -> assertInstanceOf(Member.class, user);
        }
    }

    @Test
    void createsOperationalEquipment() {
        Equipment equipment = factory.createEquipment("EQ1", "Spin Bike", "Studio A");
        assertEquals("EQ1", equipment.getId());
        assertEquals(Equipment.Status.OPERATIONAL, equipment.getStatus());
    }

    @Test
    void delegatesUserValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> factory.createUser(User.Role.MEMBER, " ", "Maya"));
        assertThrows(IllegalArgumentException.class,
                () -> factory.createUser(User.Role.MEMBER, "M1", " "));
    }

    @Test
    void rejectsNullRoleClearly() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> factory.createUser(null, "M1", "Maya"));
        assertEquals("Role is required", exception.getMessage());
    }
}
