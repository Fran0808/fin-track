package com.store.api.service.email.parser;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.util.Locale;
import java.util.Set;

/** Matches the actual mailbox domain, never the display name or subject. */
final class BankEmailSenderPolicy {

    private BankEmailSenderPolicy() {
    }

    static boolean matchesDomain(String sender, Set<String> allowedDomains) {
        if (sender == null || sender.isBlank()) {
            return false;
        }
        try {
            InternetAddress[] addresses = InternetAddress.parse(sender, true);
            if (addresses.length != 1 || addresses[0].isGroup()) {
                return false;
            }
            addresses[0].validate();
            String mailbox = addresses[0].getAddress();
            int separator = mailbox.lastIndexOf('@');
            return separator > 0 && allowedDomains.contains(
                    mailbox.substring(separator + 1).toLowerCase(Locale.ROOT));
        } catch (AddressException exception) {
            return false;
        }
    }
}
