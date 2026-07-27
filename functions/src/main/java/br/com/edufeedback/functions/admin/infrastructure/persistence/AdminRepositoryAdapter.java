package br.com.edufeedback.functions.admin.infrastructure.persistence;

import br.com.edufeedback.functions.admin.domain.AdminRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
class AdminRepositoryAdapter implements AdminRepository {

    @Inject
    AdminPanacheRepository adminPanacheRepository;

    @Override
    public List<String> listarEmails() {
        return adminPanacheRepository.listAll().stream().map(admin -> admin.email).toList();
    }
}
