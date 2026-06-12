package com.cargotrack.common;

import org.springframework.http.HttpStatus;

/** Запрещённый переход машины состояний → 409 Conflict (SDP, раздел 7.7). */
public class IllegalStateTransitionException extends ApiException {

    public IllegalStateTransitionException(String entity, Enum<?> from, Enum<?> to) {
        super(HttpStatus.CONFLICT,
                "Недопустимый переход статуса %s: %s → %s".formatted(entity, from, to));
    }
}
