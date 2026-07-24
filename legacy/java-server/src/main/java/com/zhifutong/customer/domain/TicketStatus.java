package com.zhifutong.customer.domain;

import java.util.Set;

public enum TicketStatus {
    OPEN,
    PROCESSING,
    RESOLVED,
    CLOSED;

    public boolean canTransitTo(TicketStatus next) {
        if (this == next) {
            return true;
        }
        return switch (this) {
            case OPEN -> Set.of(PROCESSING, CLOSED).contains(next);
            case PROCESSING -> Set.of(RESOLVED, CLOSED).contains(next);
            case RESOLVED -> next == CLOSED;
            case CLOSED -> false;
        };
    }
}
