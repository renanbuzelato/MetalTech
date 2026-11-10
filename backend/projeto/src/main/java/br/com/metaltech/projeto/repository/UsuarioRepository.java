package br.com.metaltech.projeto.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.metaltech.projeto.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
}
