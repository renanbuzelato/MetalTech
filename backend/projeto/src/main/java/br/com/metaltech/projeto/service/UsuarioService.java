package br.com.metaltech.projeto.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.metaltech.projeto.dto.UsuarioRequest;
import br.com.metaltech.projeto.exception.RecursoNaoEncontradoException;
import br.com.metaltech.projeto.model.Usuario;
import br.com.metaltech.projeto.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario cadastrar(UsuarioRequest request) {
        Usuario usuario = new Usuario(request.nome(), request.email(), request.perfil());
        return usuarioRepository.save(usuario);
    }

    public List<Usuario> listar() {
        return usuarioRepository.findAll();
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + id));
    }

    public Usuario atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = buscarPorId(id);
        usuario.atualizarDados(request.nome(), request.email(), request.perfil());
        return usuarioRepository.save(usuario);
    }

    public void excluir(Long id) {
        usuarioRepository.delete(buscarPorId(id));
    }
}
