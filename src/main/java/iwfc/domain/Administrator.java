package iwfc.domain;

public final class Administrator extends User {
    public Administrator(String id, String name) {
        super(id, name, Role.ADMINISTRATOR);
    }

    @Override
    public String getRoleDescription() {
        return "Manages users, equipment, and maintenance operations";
    }
}

