package com.suabhi.cabbooking;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {
    @Test
    void verifiesModuleDependencies() {
        ApplicationModules.of(SuabhiCabBookingApplication.class).verify();
    }
}
