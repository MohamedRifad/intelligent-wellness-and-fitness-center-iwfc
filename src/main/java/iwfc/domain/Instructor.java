package iwfc.domain;

public final class Instructor extends User {
    public Instructor(String id, String name) {
        super(id, name, Role.INSTRUCTOR);
    }

    @Override
    public String getRoleDescription() {
        return "Schedules sessions, records usage, and reports faults";
    }
}

