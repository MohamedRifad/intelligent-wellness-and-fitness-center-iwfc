package iwfc.pattern;

import iwfc.domain.Administrator;
import iwfc.domain.Equipment;
import iwfc.domain.Instructor;
import iwfc.domain.Member;
import iwfc.domain.User;

import java.util.Objects;

public final class EntityFactory {
    public User createUser(User.Role role, String id, String name) {
        return switch (Objects.requireNonNull(role, "Role is required")) {
            case ADMINISTRATOR -> new Administrator(id, name);
            case INSTRUCTOR -> new Instructor(id, name);
            case MEMBER -> new Member(id, name);
        };
    }

    public Equipment createEquipment(String id, String name, String location) {
        return new Equipment(id, name, location);
    }
}
