package com.store.api.service.email.parser;

import com.store.api.model.dto.email.ParsedEmailTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Dispatcher component that selects the appropriate BankEmailParser
 * based on the email sender and subject line.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BankEmailParserDispatcher {

    private final List<BankEmailParser> parsers;

    public Optional<ParsedEmailTransaction> dispatchAndParse(String sender, String subject, String body, LocalDateTime receivedDate) {
        for (BankEmailParser parser : parsers) {
            if (parser.supports(sender, subject)) {
                log.debug("Dispatching email to a supported parser");
                ParsedEmailTransaction parsed = parser.parse(subject, body, receivedDate);
                if (parsed != null) {
                    return Optional.of(parsed);
                }
            }
        }
        log.debug("No supported email parser matched");
        return Optional.empty();
    }
}
