package br.com.luizcode.agenda.identity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AgendaUser implements UserDetails, CredentialsContainer {
    private static final long serialVersionUID = 1L;
    private final UUID id, businessId;
    private final String name, email;
    private String passwordHash;
    public AgendaUser(UUID id, UUID businessId, String name, String email, String passwordHash) {
        this.id=id; this.businessId=businessId; this.name=name; this.email=email; this.passwordHash=passwordHash;
    }
    public UUID id() { return id; }
    public UUID businessId() { return businessId; }
    public String name() { return name; }
    public String email() { return email; }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public void eraseCredentials() { passwordHash = null; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_OWNER"));
    }
    @Override public String toString() { return "AgendaUser[id=" + id + "]"; }
}
