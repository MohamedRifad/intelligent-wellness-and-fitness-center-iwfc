package iwfc.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class IWFCFacadeSmokeTest {
    @Test
    void createsApplicationFoundation() {
        IWFCFacade facade = new IWFCFacade();

        assertNotNull(facade.getEntityFactory());
        assertNotNull(facade.getBookingService());
        assertNotNull(facade.getMaintenanceService());
    }
}

