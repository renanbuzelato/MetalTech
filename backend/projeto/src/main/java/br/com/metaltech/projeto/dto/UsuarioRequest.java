package br.com.metaltech.projeto.dto;

import br.com.metaltech.projeto.model.PerfilUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UsuarioRequest(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotNull PerfilUsuario perfil) {
}
