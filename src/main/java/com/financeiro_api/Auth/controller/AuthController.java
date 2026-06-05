package com.financeiro_api.Auth.controller;

import com.financeiro_api.Auth.dto.EsqueciSenhaDTO;
import com.financeiro_api.Auth.dto.LoginDTO;
import com.financeiro_api.Auth.dto.RegisterDTO;
import com.financeiro_api.Auth.dto.ResetarSenhaDTO;
import com.financeiro_api.Auth.dto.TokenResponseDTO;
import com.financeiro_api.Auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Tag(name = "Autenticação", description = "Registro, login, verificação de email e reset de senha")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean cookieSecure;

    public AuthController(AuthService authService,
                          @Value("${app.cookie.secure:true}") boolean cookieSecure) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
    }

    @Operation(summary = "Registrar empresa e usuário CEO", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = ""))
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponseDTO registrar(@RequestBody @Valid RegisterDTO dto, HttpServletResponse response) {
        TokenResponseDTO result = authService.registrar(dto);
        setCookies(response, result);
        return result;
    }

    @Operation(summary = "Login — retorna JWT de acesso", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = ""))
    @SecurityRequirements
    @PostMapping("/login")
    public TokenResponseDTO login(@RequestBody @Valid LoginDTO dto, HttpServletResponse response) {
        TokenResponseDTO result = authService.login(dto);
        setCookies(response, result);
        return result;
    }

    @Operation(summary = "Verificar email via token enviado por e-mail", security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = ""))
    @SecurityRequirements
    @GetMapping("/verificar-email")
    public TokenResponseDTO verificarEmail(@RequestParam String token, HttpServletResponse response) {
        TokenResponseDTO result = authService.verificarEmail(token);
        setCookies(response, result);
        return result;
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
    public TokenResponseDTO refresh(
            @CookieValue(value = "financeiro_refresh", required = false) String refreshCookie,
            HttpServletResponse response) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token ausente");
        }
        TokenResponseDTO result = authService.refresh(refreshCookie);
        setCookies(response, result);
        return result;
    }

    @Operation(summary = "Trocar empresa ativa — reemite JWT para outra empresa do usuário")
    @PostMapping("/switch-empresa/{enterpriseId}")
    public TokenResponseDTO switchEmpresa(
            @PathVariable java.util.UUID enterpriseId,
            @CookieValue(value = "financeiro_refresh", required = false) String refreshCookie,
            @org.springframework.security.core.annotation.AuthenticationPrincipal String email,
            HttpServletResponse response) {
        TokenResponseDTO result = authService.switchEmpresa(email, enterpriseId, refreshCookie);
        setCookies(response, result);
        return result;
    }

    @Operation(summary = "Logout — revoga o refresh token ativo")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(value = "financeiro_refresh", required = false) String refreshCookie,
            HttpServletResponse response) {
        if (refreshCookie != null && !refreshCookie.isBlank()) {
            authService.logout(refreshCookie);
        }
        clearCookies(response);
    }

    private void setCookies(HttpServletResponse response, TokenResponseDTO data) {
        if (data.token() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("financeiro_token", data.token())
                            .httpOnly(true)
                            .secure(cookieSecure)
                            .sameSite("Strict")
                            .maxAge(Duration.ofSeconds(900))
                            .path("/")
                            .build().toString());
        }
        if (data.refreshToken() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("financeiro_refresh", data.refreshToken())
                            .httpOnly(true)
                            .secure(cookieSecure)
                            .sameSite("Strict")
                            .maxAge(Duration.ofDays(30))
                            .path("/api/v1/auth")
                            .build().toString());
        }
    }

    private void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from("financeiro_token", "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite("Strict")
                        .maxAge(0)
                        .path("/")
                        .build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from("financeiro_refresh", "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite("Strict")
                        .maxAge(0)
                        .path("/api/v1/auth")
                        .build().toString());
    }
}
