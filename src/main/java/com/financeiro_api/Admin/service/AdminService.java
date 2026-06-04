package com.financeiro_api.Admin.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.service.AuditLogService;
import com.financeiro_api.Admin.dto.AdminEnterpriseDTO;
import com.financeiro_api.Admin.dto.AdminUserDTO;
import com.financeiro_api.Enterprises.domain.Enterprise;
import com.financeiro_api.Enterprises.domain.EnterpriseStatus;
import com.financeiro_api.Enterprises.domain.Plan;
import com.financeiro_api.Enterprises.repository.EnterpriseRepository;
import com.financeiro_api.Users.domain.Role;
import com.financeiro_api.Users.domain.User;
import com.financeiro_api.Users.repository.UserRepository;
import com.financeiro_api.shared.exception.ConflitoException;
import com.financeiro_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final EnterpriseRepository enterpriseRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbc;

    public AdminService(EnterpriseRepository enterpriseRepository,
                        UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        AuditLogService auditLogService,
                        JdbcTemplate jdbc) {
        this.enterpriseRepository = enterpriseRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.jdbc = jdbc;
    }

    public List<AdminEnterpriseDTO> listarEmpresas(String status) {
        List<Enterprise> all;
        if (status != null && !status.isBlank()) {
            EnterpriseStatus s = EnterpriseStatus.valueOf(status.toUpperCase());
            all = enterpriseRepository.findAllClientEmpresasByStatus(s, Role.PLATFORM_ADMIN);
        } else {
            all = enterpriseRepository.findAllClientEmpresas(Role.PLATFORM_ADMIN);
        }
        return all.stream()
                .map(AdminEnterpriseDTO::from)
                .toList();
    }

    @Transactional
    public AdminEnterpriseDTO aprovar(UUID id) {
        Enterprise e = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada: " + id));
        e.setStatus(EnterpriseStatus.ATIVA);
        AdminEnterpriseDTO result = AdminEnterpriseDTO.from(enterpriseRepository.save(e));
        auditLogService.log(AuditAction.ENTERPRISE_APPROVED, "Enterprise", id.toString());
        return result;
    }

    @Transactional
    public AdminEnterpriseDTO rejeitar(UUID id) {
        Enterprise e = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada: " + id));
        e.setStatus(EnterpriseStatus.BLOQUEADA);
        AdminEnterpriseDTO result = AdminEnterpriseDTO.from(enterpriseRepository.save(e));
        auditLogService.log(AuditAction.ENTERPRISE_REJECTED, "Enterprise", id.toString());
        return result;
    }

    @Transactional
    public AdminEnterpriseDTO alterarPlano(UUID id, String plan) {
        Enterprise e = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada: " + id));
        e.setPlan(Plan.valueOf(plan.toUpperCase()));
        AdminEnterpriseDTO result = AdminEnterpriseDTO.from(enterpriseRepository.save(e));
        auditLogService.log(AuditAction.ENTERPRISE_PLAN_UPDATED, "Enterprise", id.toString());
        return result;
    }

    @Transactional
    public AdminUserDTO adicionarUsuario(UUID enterpriseId, String name, String email, String password, String role) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada: " + enterpriseId));
        if (userRepository.existsByEmail(email)) {
            throw new ConflitoException("E-mail já está em uso: " + email);
        }
        Role targetRole = (role != null && !role.isBlank()) ? Role.valueOf(role.toUpperCase()) : Role.USER;
        User user = User.builder()
                .enterprise(enterprise)
                .name(name.trim())
                .email(email.trim().toLowerCase())
                .password(passwordEncoder.encode(password))
                .role(targetRole)
                .emailVerificado(true)
                .build();
        AdminUserDTO result = AdminUserDTO.from(userRepository.save(user));
        auditLogService.log(AuditAction.USER_CREATED, "User", result.id().toString());
        return result;
    }

    @Transactional
    public AdminUserDTO criarAdmin(String name, String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflitoException("E-mail já está em uso: " + email);
        }
        User user = User.builder()
                .enterprise(null)
                .name(name.trim())
                .email(email.trim().toLowerCase())
                .password(passwordEncoder.encode(password))
                .role(Role.PLATFORM_ADMIN)
                .emailVerificado(true)
                .build();
        AdminUserDTO result = AdminUserDTO.from(userRepository.save(user));
        auditLogService.log(AuditAction.USER_CREATED, "User", result.id().toString());
        return result;
    }

    public List<AdminUserDTO> listarUsuariosEmpresa(UUID enterpriseId) {
        enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada: " + enterpriseId));
        return userRepository.findAllByEnterprise_IdOrderByNameAsc(enterpriseId)
                .stream().map(AdminUserDTO::from).toList();
    }

    @Transactional
    public AdminUserDTO alterarRoleUsuario(UUID enterpriseId, UUID userId, String role) {
        User user = userRepository.findByIdAndEnterprise_Id(userId, enterpriseId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
        user.setRole(Role.valueOf(role.toUpperCase()));
        AdminUserDTO result = AdminUserDTO.from(userRepository.save(user));
        auditLogService.log(AuditAction.USER_UPDATED, "User", userId.toString());
        return result;
    }

    @Transactional
    public void removerUsuarioEmpresa(UUID enterpriseId, UUID userId) {
        User user = userRepository.findByIdAndEnterprise_Id(userId, enterpriseId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
        auditLogService.log(AuditAction.USER_DELETED, "User", userId.toString());
        userRepository.delete(user);
    }

    @Transactional
    public void deletarEmpresa(UUID id) {
        enterpriseRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada: " + id));

        String eid = id.toString();
        jdbc.update("DELETE FROM extrato WHERE enterprise_id = ?::uuid", eid);
        jdbc.update("DELETE FROM previsao WHERE enterprise_id = ?::uuid", eid);
        jdbc.update("DELETE FROM saldo_anterior WHERE enterprise_id = ?::uuid", eid);
        jdbc.update("DELETE FROM conta_bancaria WHERE enterprise_id = ?::uuid", eid);
        jdbc.update("DELETE FROM categoria WHERE enterprise_id = ?::uuid", eid);
        jdbc.update("DELETE FROM audit_logs WHERE enterprise_id = ?::uuid", eid);
        // refresh_tokens tem ON DELETE CASCADE em user_id — auto-deletados com users
        jdbc.update("DELETE FROM users WHERE enterprise_id = ?::uuid", eid);
        jdbc.update("DELETE FROM enterprise WHERE id = ?::uuid", eid);

        auditLogService.log(AuditAction.ENTERPRISE_DELETED, "Enterprise", eid);
    }
}
