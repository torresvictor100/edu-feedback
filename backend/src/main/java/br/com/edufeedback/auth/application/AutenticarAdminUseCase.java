package br.com.edufeedback.auth.application;

import br.com.edufeedback.auth.domain.Admin;
import br.com.edufeedback.auth.domain.AdminRepository;
import br.com.edufeedback.auth.domain.TokenGerador;
import br.com.edufeedback.shared.exception.CredenciaisInvalidasException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AutenticarAdminUseCase {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenGerador tokenGerador;

    public AutenticarAdminUseCase(
            AdminRepository adminRepository, PasswordEncoder passwordEncoder, TokenGerador tokenGerador) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGerador = tokenGerador;
    }

    public String autenticar(String email, String senha) {
        Admin admin = adminRepository.buscarPorEmail(email)
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!passwordEncoder.matches(senha, admin.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

        return tokenGerador.gerarToken(admin.getEmail());
    }
}
