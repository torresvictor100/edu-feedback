package br.com.edufeedback.auth.infrastructure.persistence;

import br.com.edufeedback.auth.domain.Admin;
import br.com.edufeedback.auth.domain.AdminRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class AdminRepositoryAdapter implements AdminRepository {

    private final SpringDataAdminRepository springDataAdminRepository;

    AdminRepositoryAdapter(SpringDataAdminRepository springDataAdminRepository) {
        this.springDataAdminRepository = springDataAdminRepository;
    }

    @Override
    public Optional<Admin> buscarPorEmail(String email) {
        return springDataAdminRepository.findByEmail(email).map(AdminRepositoryAdapter::paraDomain);
    }

    private static Admin paraDomain(AdminJpaEntity entity) {
        return new Admin(entity.getId(), entity.getEmail(), entity.getSenhaHash(), entity.getCriadoEm());
    }
}
