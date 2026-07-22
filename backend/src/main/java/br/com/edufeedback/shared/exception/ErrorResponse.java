package br.com.edufeedback.shared.exception;

import java.time.Instant;

public record ErrorResponse(String mensagem, Instant timestamp) {

    public static ErrorResponse de(String mensagem) {
        return new ErrorResponse(mensagem, Instant.now());
    }
}
