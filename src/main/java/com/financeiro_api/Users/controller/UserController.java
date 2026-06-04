package com.financeiro_api.Users.controller;

import com.financeiro_api.Users.dto.UserInviteDTO;
import com.financeiro_api.Users.dto.UserResponseDTO;
import com.financeiro_api.Users.dto.UserUpdateDTO;
import com.financeiro_api.Users.service.UserService;
import com.financeiro_api.shared.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Usuários", description = "Gestão de usuários da empresa (requer role CEO ou OWNER)")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponseDTO> listar() {
        return userService.listarPorEmpresa(TenantContext.get());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO convidar(@RequestBody UserInviteDTO dto,
                                    @AuthenticationPrincipal String requesterEmail) {
        return userService.convidar(TenantContext.get(), dto, requesterEmail);
    }

    @PatchMapping("/{id}")
    public UserResponseDTO editar(@PathVariable UUID id,
                                  @RequestBody UserUpdateDTO dto,
                                  @AuthenticationPrincipal String requesterEmail) {
        return userService.editarUsuario(TenantContext.get(), id, dto, requesterEmail);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable UUID id,
                        @AuthenticationPrincipal String requesterEmail) {
        userService.deletarUsuario(TenantContext.get(), id, requesterEmail);
    }
}
