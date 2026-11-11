package com.gimnasio.sistemagym.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Environment-supplied credentials used only when no administrator exists. */
@Component
public class InitialAdminProperties {

    private final String name;
    private final String lastName;
    private final String email;
    private final String password;

    public InitialAdminProperties(
            @Value("${INITIAL_ADMIN_NAME:}") String name,
            @Value("${INITIAL_ADMIN_LAST_NAME:}") String lastName,
            @Value("${INITIAL_ADMIN_EMAIL:}") String email,
            @Value("${INITIAL_ADMIN_PASSWORD:}") String password) {
        this.name = name;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}
