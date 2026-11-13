package com.suabhi.cabbooking.journey.provider;

@org.springframework.modulith.NamedInterface("types")
/** Customer-facing journey types, independent of any maps vendor. */
public enum ServiceType {
    AIRPORT_TRANSFER,
    LOCAL_RENTAL,
    OUTSTATION
}
