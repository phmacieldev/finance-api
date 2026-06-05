package com.financeiro_api.Admin.controller;

import com.financeiro_api.Admin.dto.AdminCriarEmpresaDTO;
import com.financeiro_api.Admin.dto.AdminEnterpriseDTO;
import com.financeiro_api.Admin.dto.AdminUserDTO;
import com.financeiro_api.Admin.service.AdminService;
import com.financeiro_api.Demo.DemoSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Admin — Plataforma", description = "Gestão de empresas e usuários (requer role PLATFORM_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService service;
    private final DemoSeedService demoSeedService;

    public AdminController(AdminService service, DemoSeedService demoSeedService) {
        this.service = service;
        this.demoSeedService = demoSeedService;
    }

    @Operation(summary = "Criar empresa com usuário CEO inicial")
    @PostMapping("/empresas")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminEnterpriseDTO criarEmpresa(@RequestBody @Valid AdminCriarEmpresaDTO dto) {
        return service.criarEmpresa(dto);
    }

    @Operation(summary = "Listar empresas (filtrável por status: PENDENTE, ATIVA, BLOQUEADA)")
    @GetMapping("/empresas")
    public List<AdminEnterpriseDTO> listar(@RequestParam(required = false) String status) {
        return service.listarEmpresas(status);
    }

    @Operation(summary = "Aprovar empresa")
    @PatchMapping("/empresas/{id}/aprovar")
    public AdminEnterpriseDTO aprovar(@PathVariable UUID id) {
        return service.aprovar(id);
    }

    @Operation(summary = "Rejeitar / bloquear empresa")
    @PatchMapping("/empresas/{id}/rejeitar")
    public AdminEnterpriseDTO rejeitar(@PathVariable UUID id) {
        return service.rejeitar(id);
    }

    @Operation(summary = "Alterar plano da empresa")
    @PatchMapping("/empresas/{id}/plano")
    public AdminEnterpriseDTO alterarPlano(@PathVariable UUID id,
                                           @RequestBody Map<String, String> body) {
        return service.alterarPlano(id, body.get("plan"));
    }

    @Operation(summary = "Listar usuários de uma empresa")
    @GetMapping("/empresas/{id}/usuarios")
    public List<AdminUserDTO> listarUsuarios(@PathVariable UUID id) {
        return service.listarUsuariosEmpresa(id);
    }

    @Operation(summary = "Adicionar usuário a uma empresa")
    @PostMapping("/empresas/{id}/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDTO adicionarUsuario(@PathVariable UUID id,
                                         @RequestBody Map<String, String> body) {
        return service.adicionarUsuario(
                id,
                body.get("name"),
                body.get("email"),
                body.get("password"),
                body.get("role")
        );
    }

    @Operation(summary = "Editar dados da empresa (nome)")
    @PatchMapping("/empresas/{id}")
    public AdminEnterpriseDTO editarEmpresa(@PathVariable UUID id,
                                             @RequestBody Map<String, String> body) {
        return service.editarEmpresa(id, body.get("name"));
    }

    @Operation(summary = "Editar usuário (nome, email e/ou role)")
    @PatchMapping("/empresas/{enterpriseId}/usuarios/{userId}")
    public AdminUserDTO editarUsuario(@PathVariable UUID enterpriseId,
                                      @PathVariable UUID userId,
                                      @RequestBody Map<String, String> body) {
        return service.editarUsuario(enterpriseId, userId, body.get("name"), body.get("email"), body.get("role"));
    }

    @Operation(summary = "Remover usuário de uma empresa")
    @DeleteMapping("/empresas/{enterpriseId}/usuarios/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerUsuario(@PathVariable UUID enterpriseId,
                               @PathVariable UUID userId) {
        service.removerUsuarioEmpresa(enterpriseId, userId);
    }

    @Operation(summary = "Criar um novo usuário PLATFORM_ADMIN")
    @PostMapping("/plataforma/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDTO criarAdmin(@RequestBody Map<String, String> body) {
        return service.criarAdmin(body.get("name"), body.get("email"), body.get("password"));
    }

    @Operation(summary = "Deletar empresa e todos os seus dados")
    @DeleteMapping("/empresas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletarEmpresa(@PathVariable UUID id) {
        service.deletarEmpresa(id);
    }

    @Operation(summary = "Popular empresa com dados de demonstração (6 meses de extratos, categorias e previsões)")
    @PostMapping("/empresas/{id}/seed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seedDemo(@PathVariable UUID id) {
        demoSeedService.seed(id);
    }
}
