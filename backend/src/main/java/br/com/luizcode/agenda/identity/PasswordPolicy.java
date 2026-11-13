package br.com.luizcode.agenda.identity;

import br.com.luizcode.agenda.shared.BusinessException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static void validate(String password) {
        if (password == null || password.codePointCount(0,password.length())<15
                || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw BusinessException.invalid("A senha precisa ter pelo menos 15 caracteres e até 72 bytes em UTF-8. Acentos e emojis ocupam mais de um byte.");
    }
}
