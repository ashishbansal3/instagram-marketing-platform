package com.rozpost.api.domain.port;

/**
 * Factory: returns Meta Graph / WhatsApp adapters for the active {@code META_MODE}.
 * Open/Closed — new providers register without editing callers.
 */
public interface MetaModeFactory {
    /** Live Graph HTTP client or fixture-backed mock. */
    MetaGraphClient graphClient();

    /** True when {@code META_MODE=mock} (simulate endpoints allowed). */
    boolean isMock();

    /** Current mode string: {@code mock} or {@code live}. */
    String mode();
}
