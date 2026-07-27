package br.com.edufeedback.auth.domain;

import java.time.Instant;
import java.util.UUID;

public class Admin {

    private final UUID id;
    private final String email;
    private final String senhaHash;
    private final Instant criadoEm;

    public Admin(UUID id, String email, String senhaHash, Instant criadoEm) {
        this.id = id;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
