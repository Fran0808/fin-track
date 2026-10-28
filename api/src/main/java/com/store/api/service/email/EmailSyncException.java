package com.store.api.service.email;

import com.store.api.model.enums.EmailSyncFailure;
import lombok.Getter;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Getter
public class EmailSyncException extends IllegalStateException {
    private final EmailSyncFailure failure;

    public EmailSyncException(EmailSyncFailure failure) {
        super("Email synchronization could not finish");
        this.failure = failure;
    }

    public static EmailSyncFailure classify(Exception error) {
        if (error instanceof EmailSyncException sync) return sync.getFailure();
        if (error instanceof ResourceAccessException) return EmailSyncFailure.TEMPORARY;
        if (error instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 401) return EmailSyncFailure.AUTH_REQUIRED;
            if (status == 403) return EmailSyncFailure.ACCESS_DENIED;
            if (status == 429) return EmailSyncFailure.RATE_LIMITED;
            if (status >= 500) return EmailSyncFailure.TEMPORARY;
        }
        return EmailSyncFailure.UNKNOWN;
    }
}
