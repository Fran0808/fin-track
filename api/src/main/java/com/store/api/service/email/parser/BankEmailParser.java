package com.store.api.service.email.parser;

import com.store.api.model.dto.email.ParsedEmailTransaction;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Strategy interface for bank-specific email parsers
 * Implementations handle specific bank formats
 */
public interface BankEmailParser {

    /** Gmail search clauses for discovering candidate messages. supports still validates each sender. */
    List<String> gmailSearchTerms();

    /**
     * Checks whether this parser supports the given email sender and subject.
     *
     * @param sender  Email sender address or display name
     * @param subject Email subject line
     * @return true if this parser can handle the email
     */
    boolean supports(String sender, String subject);

    /**
     * Parses the email content into a structured transaction.
     *
     * @param subject         Email subject line
     * @param htmlOrTextBody  Body content (HTML or plain text)
     * @param receivedDate    Date when the email was sent/received
     * @return Structured transaction or null if not an eligible financial operation
     */
    ParsedEmailTransaction parse(String subject, String htmlOrTextBody, LocalDateTime receivedDate);

    /**
     * Returns the bank name for logging and auditing purposes.
     */
    String getBankName();
}
