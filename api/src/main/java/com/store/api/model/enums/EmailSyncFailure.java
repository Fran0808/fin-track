package com.store.api.model.enums;

/** Safe diagnostic codes; never store provider bodies or exception messages. */
public enum EmailSyncFailure {
    AUTH_REQUIRED, ACCESS_DENIED, RATE_LIMITED, TEMPORARY, PROCESSING, UNKNOWN
}
