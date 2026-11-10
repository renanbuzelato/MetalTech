package br.com.metaltech.projeto.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.metaltech.projeto.dto.EsteiraRequest;
import br.com.metaltech.projeto.dto.EsteiraResponse;
import br.com.metaltech.projeto.service.EsteiraService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/esteiras")
public class EsteiraController {

    private final EsteiraService esteiraService;

    public EsteiraController(EsteiraService esteiraService) {
        this.esteiraService = esteiraService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EsteiraResponse cadastrar(@Valid @RequestBody EsteiraRequest request) {
        return EsteiraResponse.de(esteiraService.cadastrar(request));
    }

    @GetMapping
    public List<EsteiraResponse> listar() {
        return esteiraService.listar().stream().map(EsteiraResponse::de).toList();
    }

    @GetMapping("/{id}")
    public EsteiraResponse buscarPorId(@PathVariable Long id) {
        return EsteiraResponse.de(esteiraService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public EsteiraResponse atualizar(@PathVariable Long id, @Valid @RequestBody EsteiraRequest request) {
        return EsteiraResponse.de(esteiraService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        esteiraService.excluir(id);
    }
}
