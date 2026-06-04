package com.financeiro_api.Auth.controller;

import com.financeiro_api.Auth.dto.EsqueciSenhaDTO;
import com.financeiro_api.Auth.dto.LoginDTO;
import com.financeiro_api.Auth.dto.RefreshTokenRequestDTO;
import com.financeiro_api.Auth.dto.RegisterDTO;
import com.financeiro_api.Auth.dto.ResetarSenhaDTO;
import com.financeiro_api.Auth.dto.TokenResponseDTO;
import com.financeiro_api.Auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponseDTO registrar(@RequestBody @Valid RegisterDTO dto) {
        return authService.registrar(dto);
    }

    @PostMapping("/login")
    public TokenResponseDTO login(@RequestBody @Valid LoginDTO dto) {
        return authService.login(dto);
    }

    @GetMapping("/verificar-email")
    public TokenResponseDTO verificarEmail(@RequestParam String token) {
        return authService.verificarEmail(token);
    }

    @PostMapping("/reenviar-verificacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reenviarVerificacao(@RequestParam String email) {
        authService.reenviarVerificacao(email);
    }

    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void esqueciSenha(@RequestBody @Valid EsqueciSenhaDTO dto) {
        authService.esqueciSenha(dto);
    }

    @PostMapping("/resetar-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetarSenha(@RequestBody @Valid ResetarSenhaDTO dto) {
        authService.resetarSenha(dto);
    }

    @PostMapping("/refresh")
    public TokenResponseDTO refresh(@RequestBody @Valid RefreshTokenRequestDTO dto) {
        return authService.refresh(dto.refreshToken());
    }
}
