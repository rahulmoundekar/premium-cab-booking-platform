@org.springframework.modulith.ApplicationModule(
        displayName = "Booking",
        allowedDependencies = {"common::exception", "fleet", "identity::principal",
                "journey::preview", "journey::types", "pricing::quotes"}
)
package com.suabhi.cabbooking.booking;
