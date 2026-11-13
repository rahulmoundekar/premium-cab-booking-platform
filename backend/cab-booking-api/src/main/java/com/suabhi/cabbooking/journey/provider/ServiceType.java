package com.suabhi.cabbooking.journey.provider;

/** Customer-facing journey types, independent of any maps vendor. */
@org.springframework.modulith.NamedInterface("types")
public enum ServiceType {
    AIRPORT_TRANSFER,
    LOCAL_RENTAL,
    OUTSTATION
}
