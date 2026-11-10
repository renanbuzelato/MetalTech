package br.com.metaltech.projeto.dto;

import br.com.metaltech.projeto.model.PerfilUsuario;
import br.com.metaltech.projeto.model.Usuario;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        PerfilUsuario perfil) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil());
    }
}
