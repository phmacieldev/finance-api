package com.financeiro_api.Users.controller;

import com.financeiro_api.Enterprises.dto.EnterpriseUpdateDTO;
import com.financeiro_api.Users.dto.AlterarSenhaDTO;
import com.financeiro_api.Users.dto.AtualizarPerfilDTO;
import com.financeiro_api.Users.dto.PerfilResponseDTO;
import com.financeiro_api.Users.service.UserService;
import com.financeiro_api.shared.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class PerfilController {

    private final UserService userService;

    public PerfilController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PerfilResponseDTO perfil(@AuthenticationPrincipal String email) {
        return userService.buscarPerfil(email);
    }

    @PatchMapping
    public PerfilResponseDTO atualizar(@AuthenticationPrincipal String email,
                                       @RequestBody AtualizarPerfilDTO dto) {
        return userService.atualizarPerfil(email, dto);
    }

    @PatchMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@AuthenticationPrincipal String email,
                             @RequestBody AlterarSenhaDTO dto) {
        userService.alterarSenha(email, dto);
    }

    @PatchMapping("/empresa")
    public PerfilResponseDTO atualizarEmpresa(@AuthenticationPrincipal String email,
                                              @RequestBody EnterpriseUpdateDTO dto) {
        return userService.atualizarEmpresa(TenantContext.get(), email, dto);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletarConta(@AuthenticationPrincipal String email) {
        userService.deletarPropriaConta(email);
    }
}
