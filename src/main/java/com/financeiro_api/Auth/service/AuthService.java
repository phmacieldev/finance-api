package com.financeiro_api.Auth.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.service.AuditLogService;
import com.financeiro_api.Auth.dto.EsqueciSenhaDTO;
import com.financeiro_api.Auth.dto.LoginDTO;
import com.financeiro_api.Auth.dto.RegisterDTO;
import com.financeiro_api.Auth.dto.ResetarSenhaDTO;
import com.financeiro_api.Auth.dto.TokenResponseDTO;
import com.financeiro_api.Enterprises.domain.Enterprise;
import com.financeiro_api.Enterprises.domain.EnterpriseStatus;
import com.financeiro_api.Enterprises.repository.EnterpriseRepository;
import com.financeiro_api.Users.domain.Role;
import com.financeiro_api.Users.domain.User;
import com.financeiro_api.Users.repository.UserRepository;
import com.financeiro_api.shared.TenantContext;
import com.financeiro_api.shared.exception.ConflitoException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    public AuthService(UserRepository userRepository,
                       EnterpriseRepository enterpriseRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailService emailService,
                       AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public TokenResponseDTO registrar(RegisterDTO dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new ConflitoException("E-mail já cadastrado: " + dto.email());
        }
        if (enterpriseRepository.existsByCnpj(dto.cnpj())) {
            throw new ConflitoException("CNPJ já cadastrado: " + dto.cnpj());
        }

        Enterprise enterprise = enterpriseRepository.save(
                Enterprise.builder()
                        .name(dto.enterpriseName())
                        .cnpj(dto.cnpj())
                        .status(EnterpriseStatus.PENDENTE)
                        .build()
        );

        String token = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.save(
                User.builder()
                        .name(dto.userName())
                        .email(dto.email())
                        .password(passwordEncoder.encode(dto.password()))
                        .role(Role.CEO)
                        .enterprise(enterprise)
                        .emailVerificado(false)
                        .tokenVerificacao(token)
                        .tokenVerificacaoExpiracao(LocalDateTime.now().plusHours(24))
                        .build()
        );

        emailService.enviarVerificacaoEmail(user.getEmail(), token);
        emailService.notificarNovaEmpresa(enterprise.getName(), enterprise.getCnpj(), user.getEmail());

        TenantContext.setUserId(user.getId());
        TenantContext.setEmail(user.getEmail());
        auditLogService.log(AuditAction.USER_REGISTER, "User", user.getId().toString());

        return new TokenResponseDTO(null, user.getEmail(), user.getRole().name());
    }

    @Transactional
    public TokenResponseDTO verificarEmail(String token) {
        User user = userRepository.findByTokenVerificacao(token)
                .orElseThrow(() -> new BadCredentialsException("Token de verificação inválido ou expirado"));

        if (user.getTokenVerificacaoExpiracao() != null &&
                user.getTokenVerificacaoExpiracao().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Token de verificação inválido ou expirado");
        }

        user.setEmailVerificado(true);
        user.setTokenVerificacao(null);
        user.setTokenVerificacaoExpiracao(null);
        userRepository.save(user);

        TenantContext.setUserId(user.getId());
        TenantContext.setEmail(user.getEmail());
        TenantContext.set(user.getEnterprise().getId());
        auditLogService.log(AuditAction.USER_LOGIN, "User", user.getId().toString());

        String jwt = jwtService.gerarToken(user.getEmail(), user.getId(),
                user.getEnterprise().getId(), user.getRole().name());
        return new TokenResponseDTO(jwt, user.getEmail(), user.getRole().name());
    }

    public TokenResponseDTO login(LoginDTO dto) {
        User user = userRepository.findByEmailWithEnterprise(dto.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        if (user.getRole() == Role.PLATFORM_ADMIN) {
            TenantContext.setUserId(user.getId());
            TenantContext.setEmail(user.getEmail());
            auditLogService.log(AuditAction.USER_LOGIN, "User", user.getId().toString());
            String token = jwtService.gerarToken(user.getEmail(), user.getId(), null, user.getRole().name());
            return new TokenResponseDTO(token, user.getEmail(), user.getRole().name());
        }

        if (!user.isEmailVerificado()) {
            throw new DisabledException("Email não verificado. Verifique sua caixa de entrada.");
        }

        Enterprise enterprise = user.getEnterprise();
        if (enterprise.getStatus() == EnterpriseStatus.PENDENTE) {
            throw new DisabledException("Empresa aguardando aprovação do administrador da plataforma.");
        }
        if (enterprise.getStatus() == EnterpriseStatus.BLOQUEADA) {
            throw new DisabledException("Empresa bloqueada. Entre em contato com o suporte.");
        }

        TenantContext.setUserId(user.getId());
        TenantContext.setEmail(user.getEmail());
        TenantContext.set(enterprise.getId());
        auditLogService.log(AuditAction.USER_LOGIN, "User", user.getId().toString());

        String token = jwtService.gerarToken(user.getEmail(), user.getId(),
                enterprise.getId(), user.getRole().name());
        return new TokenResponseDTO(token, user.getEmail(), user.getRole().name());
    }

    public void reenviarVerificacao(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuário não encontrado"));
        if (user.isEmailVerificado()) return;

        String token = UUID.randomUUID().toString().replace("-", "");
        user.setTokenVerificacao(token);
        user.setTokenVerificacaoExpiracao(LocalDateTime.now().plusHours(24));
        userRepository.save(user);
        emailService.enviarVerificacaoEmail(email, token);
    }

    @Transactional
    public void esqueciSenha(EsqueciSenhaDTO dto) {
        userRepository.findByEmail(dto.email()).ifPresent(user -> {
            String token = UUID.randomUUID().toString().replace("-", "");
            user.setTokenResetSenha(token);
            user.setTokenResetExpiracao(LocalDateTime.now().plusHours(1));
            userRepository.save(user);
            emailService.enviarResetSenha(user.getEmail(), token);
            TenantContext.setUserId(user.getId());
            TenantContext.setEmail(user.getEmail());
            auditLogService.log(AuditAction.PASSWORD_RESET_REQUESTED, "User", user.getId().toString());
        });
        // sempre retorna 204 para não revelar se o email existe
    }

    @Transactional
    public void resetarSenha(ResetarSenhaDTO dto) {
        User user = userRepository.findByTokenResetSenha(dto.token())
                .orElseThrow(() -> new BadCredentialsException("Token inválido ou expirado"));

        if (user.getTokenResetExpiracao() == null || user.getTokenResetExpiracao().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Token inválido ou expirado");
        }

        user.setPassword(passwordEncoder.encode(dto.novaSenha()));
        user.setTokenResetSenha(null);
        user.setTokenResetExpiracao(null);
        userRepository.save(user);
        TenantContext.setUserId(user.getId());
        TenantContext.setEmail(user.getEmail());
        auditLogService.log(AuditAction.PASSWORD_RESET_COMPLETED, "User", user.getId().toString());
    }
}
