package com.financeiro_api.Auth.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.service.AuditLogService;
import com.financeiro_api.Auth.domain.RefreshToken;
import com.financeiro_api.Auth.dto.EsqueciSenhaDTO;
import com.financeiro_api.Auth.dto.LoginDTO;
import com.financeiro_api.Auth.dto.RegisterDTO;
import com.financeiro_api.Auth.dto.ResetarSenhaDTO;
import com.financeiro_api.Auth.dto.TokenResponseDTO;
import com.financeiro_api.Enterprises.domain.Enterprise;
import com.financeiro_api.Enterprises.domain.EnterpriseStatus;
import com.financeiro_api.Enterprises.domain.TipoPessoa;
import com.financeiro_api.Enterprises.repository.EnterpriseRepository;
import com.financeiro_api.shared.validation.CnpjValidator;
import com.financeiro_api.shared.validation.CpfValidator;
import com.financeiro_api.UserEnterprise.domain.UserEnterprise;
import com.financeiro_api.UserEnterprise.repository.UserEnterpriseRepository;
import com.financeiro_api.Users.domain.Role;
import com.financeiro_api.Users.domain.User;
import com.financeiro_api.Users.repository.UserRepository;
import com.financeiro_api.shared.TenantContext;
import com.financeiro_api.shared.exception.AcessoNegadoException;
import com.financeiro_api.shared.exception.ConflitoException;
import com.financeiro_api.shared.exception.RecursoNaoEncontradoException;
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
    private final UserEnterpriseRepository userEnterpriseRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       EnterpriseRepository enterpriseRepository,
                       UserEnterpriseRepository userEnterpriseRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailService emailService,
                       AuditLogService auditLogService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.userEnterpriseRepository = userEnterpriseRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TokenResponseDTO registrar(RegisterDTO dto) {
        TipoPessoa tipo = dto.tipoPessoaEfetiva();
        String cnpj = null;
        String cpf = null;

        if (tipo == TipoPessoa.JURIDICA) {
            String digits = dto.cnpj() != null ? dto.cnpj().replaceAll("[.\\-/]", "") : "";
            if (!new CnpjValidator().isValid(digits, null)) {
                throw new com.financeiro_api.shared.exception.ValidacaoException("CNPJ inválido");
            }
            if (enterpriseRepository.existsByCnpj(digits)) {
                throw new ConflitoException("CNPJ já cadastrado: " + digits);
            }
            cnpj = digits;
        } else {
            String digits = dto.cpf() != null ? dto.cpf().replaceAll("[.\\-]", "") : "";
            if (!new CpfValidator().isValid(digits, null)) {
                throw new com.financeiro_api.shared.exception.ValidacaoException("CPF inválido");
            }
            if (enterpriseRepository.existsByCpf(digits)) {
                throw new ConflitoException("CPF já cadastrado: " + digits);
            }
            cpf = digits;
        }

        // Usuário já existe → adicionar nova empresa ao cadastro existente
        if (userRepository.existsByEmail(dto.email())) {
            User user = userRepository.findByEmail(dto.email())
                    .orElseThrow(() -> new ConflitoException("E-mail já cadastrado: " + dto.email()));

            if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
                throw new ConflitoException("Senha incorreta para o e-mail informado");
            }

            Enterprise enterprise = enterpriseRepository.save(
                    Enterprise.builder()
                            .name(dto.enterpriseName())
                            .cnpj(cnpj)
                            .cpf(cpf)
                            .tipoPessoa(tipo)
                            .status(EnterpriseStatus.PENDENTE)
                            .build()
            );

            userEnterpriseRepository.save(UserEnterprise.builder()
                    .user(user)
                    .enterprise(enterprise)
                    .role(Role.CEO)
                    .build());

            emailService.notificarNovaEmpresa(enterprise.getName(),
                    cnpj != null ? cnpj : cpf, user.getEmail());

            TenantContext.setUserId(user.getId());
            TenantContext.setEmail(user.getEmail());
            auditLogService.log(AuditAction.USER_REGISTER, "Enterprise", enterprise.getId().toString());

            // E-mail já verificado — retorna sem emailPendente para o front exibir mensagem de aprovação
            return new TokenResponseDTO(null, null, user.getEmail(), user.getRole().name());
        }

        // Novo usuário → fluxo normal
        Enterprise enterprise = enterpriseRepository.save(
                Enterprise.builder()
                        .name(dto.enterpriseName())
                        .cnpj(cnpj)
                        .cpf(cpf)
                        .tipoPessoa(tipo)
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

        userEnterpriseRepository.save(UserEnterprise.builder()
                .user(user)
                .enterprise(enterprise)
                .role(Role.CEO)
                .build());

        emailService.enviarVerificacaoEmail(user.getEmail(), token);
        emailService.notificarNovaEmpresa(enterprise.getName(), enterprise.getCnpj(), user.getEmail());

        TenantContext.setUserId(user.getId());
        TenantContext.setEmail(user.getEmail());
        auditLogService.log(AuditAction.USER_REGISTER, "User", user.getId().toString());

        return new TokenResponseDTO(user.getEmail(), user.getRole().name());
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

        UUID enterpriseId = user.getEnterprise().getId();
        TenantContext.setUserId(user.getId());
        TenantContext.setEmail(user.getEmail());
        TenantContext.set(enterpriseId);
        auditLogService.log(AuditAction.USER_LOGIN, "User", user.getId().toString());

        String jwt = jwtService.gerarToken(user.getEmail(), user.getId(), enterpriseId, user.getRole().name());
        RefreshToken rt = refreshTokenService.criar(user, enterpriseId);
        return new TokenResponseDTO(jwt, rt.getToken(), user.getEmail(), user.getRole().name());
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
            RefreshToken rt = refreshTokenService.criar(user, null);
            return new TokenResponseDTO(token, rt.getToken(), user.getEmail(), user.getRole().name());
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

        UUID enterpriseId = enterprise.getId();
        TenantContext.setUserId(user.getId());
        TenantContext.setEmail(user.getEmail());
        TenantContext.set(enterpriseId);
        auditLogService.log(AuditAction.USER_LOGIN, "User", user.getId().toString());

        String token = jwtService.gerarToken(user.getEmail(), user.getId(), enterpriseId, user.getRole().name());
        RefreshToken rt = refreshTokenService.criar(user, enterpriseId);
        return new TokenResponseDTO(token, rt.getToken(), user.getEmail(), user.getRole().name());
    }

    @Transactional
    public TokenResponseDTO refresh(String refreshTokenValue) {
        RefreshToken rt = refreshTokenService.validarEObter(refreshTokenValue);
        User user = rt.getUser();
        UUID enterpriseId = rt.getEnterpriseId();

        String role;
        if (enterpriseId != null) {
            role = userEnterpriseRepository
                    .findByUser_IdAndEnterprise_Id(user.getId(), enterpriseId)
                    .map(ue -> ue.getRole().name())
                    .orElse(user.getRole().name());
        } else {
            role = user.getRole().name();
        }

        String jwt = jwtService.gerarToken(user.getEmail(), user.getId(), enterpriseId, role);
        RefreshToken newRt = refreshTokenService.criar(user, enterpriseId);

        TenantContext.setUserId(user.getId());
        TenantContext.setEmail(user.getEmail());
        auditLogService.log(AuditAction.USER_LOGIN, "User", user.getId().toString());

        return new TokenResponseDTO(jwt, newRt.getToken(), user.getEmail(), role);
    }

    @Transactional
    public TokenResponseDTO switchEmpresa(String email, UUID targetEnterpriseId, String currentRefreshToken) {
        User user = userRepository.findByEmailWithEnterprise(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        UserEnterprise membership = userEnterpriseRepository
                .findByUserIdAndEnterpriseIdFetchEnterprise(user.getId(), targetEnterpriseId)
                .orElseThrow(() -> new AcessoNegadoException("Você não é membro desta empresa"));

        Enterprise enterprise = membership.getEnterprise();
        if (enterprise.getStatus() == EnterpriseStatus.PENDENTE) {
            throw new DisabledException("Empresa aguardando aprovação.");
        }
        if (enterprise.getStatus() == EnterpriseStatus.BLOQUEADA) {
            throw new DisabledException("Empresa bloqueada. Entre em contato com o suporte.");
        }

        if (currentRefreshToken != null && !currentRefreshToken.isBlank()) {
            refreshTokenService.revogarPorToken(currentRefreshToken);
        }

        String role = membership.getRole().name();
        String jwt = jwtService.gerarToken(email, user.getId(), targetEnterpriseId, role);
        RefreshToken newRt = refreshTokenService.criar(user, targetEnterpriseId);

        TenantContext.setUserId(user.getId());
        TenantContext.setEmail(email);
        TenantContext.set(targetEnterpriseId);
        auditLogService.log(AuditAction.ENTERPRISE_SWITCH, "Enterprise", targetEnterpriseId.toString());

        return new TokenResponseDTO(jwt, newRt.getToken(), email, role);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenService.revogarPorToken(refreshTokenValue);
        auditLogService.log(AuditAction.USER_LOGOUT, "User", null);
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
