package com.financeiro_api.Auth.controller;

import com.financeiro_api.Auth.dto.EsqueciSenhaDTO;
import com.financeiro_api.Auth.dto.LoginDTO;
import com.financeiro_api.Auth.dto.RefreshTokenRequestDTO;
import com.financeiro_api.Auth.dto.RegisterDTO;
import com.financeiro_api.Auth.dto.ResetarSenhaDTO;
import com.financeiro_api.Auth.dto.TokenResponseDTO;
import com.financeiro_api.Auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Autenticação", description = "Registro, login, verificação de email e reset de senha")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Registrar empresa e usuário CEO", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = ""))
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponseDTO registrar(@RequestBody @Valid RegisterDTO dto) {
        return authService.registrar(dto);
    }

    @Operation(summary = "Login — retorna JWT de acesso", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = ""))
    @SecurityRequirements
    @PostMapping("/login")
    public TokenResponseDTO login(@RequestBody @Valid LoginDTO dto) {
        return authService.login(dto);
    }

    @Operation(summary = "Verificar email via token enviado por e-mail", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = ""))
    @SecurityRequirements
    @GetMapping("/verificar-email")
    public TokenResponseDTO verificarEmail(@RequestParam String token) {
        return authService.verificarEmail(token);
    }

    @Operation(summary = "Reenviar e-mail de verificação", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = ""))
    @SecurityRequirements
    @PostMapping("/reenviar-verificacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reenviarVerificacao(@RequestParam String email) {
        authService.reenviarVerificacao(email);
    }

    @Operation(summary = "Solicitar reset de senha", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = ""))
    @SecurityRequirements
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void esqueciSenha(@RequestBody @Valid EsqueciSenhaDTO dto) {
        authService.esqueciSenha(dto);
    }

    @Operation(summary = "Confirmar nova senha via token de reset", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = ""))
    @SecurityRequirements
    @PostMapping("/resetar-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetarSenha(@RequestBody @Valid ResetarSenhaDTO dto) {
        authService.resetarSenha(dto);
    }

    @PostMapping("/refresh")
    public TokenResponseDTO refresh(@RequestBody @Valid RefreshTokenRequestDTO dto) {
        return authService.refresh(dto.refreshToken());
    }

    @Operation(summary = "Logout — revoga o refresh token ativo")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Valid RefreshTokenRequestDTO dto) {
        authService.logout(dto.refreshToken());
    }
}
