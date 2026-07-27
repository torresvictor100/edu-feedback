package br.com.edufeedback.auth.infrastructure.web;

import br.com.edufeedback.auth.application.AutenticarAdminUseCase;
import br.com.edufeedback.auth.infrastructure.web.dto.LoginRequest;
import br.com.edufeedback.auth.infrastructure.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AutenticarAdminUseCase autenticarAdminUseCase;

    public AuthController(AutenticarAdminUseCase autenticarAdminUseCase) {
        this.autenticarAdminUseCase = autenticarAdminUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = autenticarAdminUseCase.autenticar(request.email(), request.senha());
        return ResponseEntity.ok(LoginResponse.deToken(token));
    }
}
