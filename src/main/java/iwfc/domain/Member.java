package iwfc.domain;

public final class Member extends User {
    public Member(String id, String name) {
        super(id, name, Role.MEMBER);
    }

    @Override
    public String getRoleDescription() {
        return "Views available sessions and requests bookings";
    }
}

