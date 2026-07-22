package br.com.edufeedback.auth.dto;

public record LoginResponse(String token, String tipo) {

    public static LoginResponse deToken(String token) {
        return new LoginResponse(token, "Bearer");
    }
}
