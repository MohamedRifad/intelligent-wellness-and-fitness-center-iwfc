package iwfc.repository;

import iwfc.domain.Equipment;
import iwfc.domain.Member;
import iwfc.domain.User;
import iwfc.exception.DuplicateDataException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericRepositoryTest {
    @Test
    void addsFindsAndListsEntity() throws DuplicateDataException {
        GenericRepository<Equipment> repository = new GenericRepository<>(Equipment::getId);
        Equipment equipment = new Equipment("EQ1", "Bike", "Studio A");
        repository.add(equipment);
        assertTrue(repository.containsId("EQ1"));
        assertSame(equipment, repository.findById("EQ1").orElseThrow());
        assertEquals(1, repository.findAll().size());
        assertFalse(repository.findById("EQ2").isPresent());
    }

    @Test
    void rejectsDuplicateId() throws DuplicateDataException {
        GenericRepository<Equipment> repository = new GenericRepository<>(Equipment::getId);
        repository.add(new Equipment("EQ1", "Bike", "Studio A"));
        assertThrows(DuplicateDataException.class,
                () -> repository.add(new Equipment("EQ1", "Treadmill", "Cardio Zone")));
    }

    @Test
    void genericImplementationSupportsDifferentEntityTypes() throws DuplicateDataException {
        GenericRepository<User> users = new GenericRepository<>(User::getId);
        GenericRepository<Equipment> equipment = new GenericRepository<>(Equipment::getId);
        users.add(new Member("M1", "Maya"));
        equipment.add(new Equipment("EQ1", "Bike", "Studio A"));
        assertEquals(User.Role.MEMBER, users.findById("M1").orElseThrow().getRole());
        assertEquals("Bike", equipment.findById("EQ1").orElseThrow().getName());
    }

    @Test
    void returnsUnmodifiableSnapshot() throws DuplicateDataException {
        GenericRepository<Equipment> repository = new GenericRepository<>(Equipment::getId);
        repository.add(new Equipment("EQ1", "Bike", "Studio A"));
        var snapshot = repository.findAll();
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(new Equipment("EQ2", "Rower", "Cardio Zone")));
        repository.add(new Equipment("EQ2", "Rower", "Cardio Zone"));
        assertEquals(1, snapshot.size());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void rejectsNullItemAndExtractor() {
        assertThrows(NullPointerException.class, () -> new GenericRepository<Equipment>(null));
        GenericRepository<Equipment> repository = new GenericRepository<>(Equipment::getId);
        assertThrows(NullPointerException.class, () -> repository.add(null));
    }

    @Test
    void rejectsMissingExtractedId() {
        GenericRepository<String> nullIds = new GenericRepository<>(value -> null);
        GenericRepository<String> blankIds = new GenericRepository<>(value -> " ");
        assertThrows(IllegalArgumentException.class, () -> nullIds.add("value"));
        assertThrows(IllegalArgumentException.class, () -> blankIds.add("value"));
    }

    @Test
    void validatesLookupId() {
        GenericRepository<String> repository = new GenericRepository<>(value -> value);
        assertThrows(IllegalArgumentException.class, () -> repository.findById(" "));
        assertThrows(IllegalArgumentException.class, () -> repository.containsId(null));
    }
}
