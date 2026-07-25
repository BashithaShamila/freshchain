package com.freshchain.events;

/** Outcome of an allocation attempt, as seen by every service in the saga. */
public enum AllocationStatus {
    FULL,
    PARTIAL,
    REJECTED
}
