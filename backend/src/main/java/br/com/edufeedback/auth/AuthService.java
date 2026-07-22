package br.com.edufeedback.auth;

import br.com.edufeedback.shared.exception.CredenciaisInvalidasException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String autenticar(String email, String senha) {
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!passwordEncoder.matches(senha, admin.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

        return jwtService.gerarToken(admin.getEmail());
    }
}
