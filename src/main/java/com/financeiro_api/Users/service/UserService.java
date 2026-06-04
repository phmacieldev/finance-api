package com.financeiro_api.Users.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.service.AuditLogService;
import com.financeiro_api.Enterprises.domain.Enterprise;
import com.financeiro_api.Enterprises.dto.EnterpriseUpdateDTO;
import com.financeiro_api.Enterprises.repository.EnterpriseRepository;
import com.financeiro_api.Users.domain.Role;
import com.financeiro_api.Users.domain.User;
import com.financeiro_api.Users.dto.AlterarSenhaDTO;
import com.financeiro_api.Users.dto.AtualizarPerfilDTO;
import com.financeiro_api.Users.dto.PerfilResponseDTO;
import com.financeiro_api.Users.dto.UserInviteDTO;
import com.financeiro_api.Users.dto.UserResponseDTO;
import com.financeiro_api.Users.dto.UserUpdateDTO;
import com.financeiro_api.Users.repository.UserRepository;
import com.financeiro_api.shared.exception.AcessoNegadoException;
import com.financeiro_api.shared.exception.ConflitoException;
import com.financeiro_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(UserRepository userRepository,
                       EnterpriseRepository enterpriseRepository,
                       PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public PerfilResponseDTO buscarPerfil(String email) {
        User user = userRepository.findByEmailWithEnterprise(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
        return PerfilResponseDTO.from(user);
    }

    @Transactional
    public PerfilResponseDTO atualizarPerfil(String email, AtualizarPerfilDTO dto) {
        User user = userRepository.findByEmailWithEnterprise(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        if (dto.name() != null && !dto.name().isBlank()) {
            user.setName(dto.name().trim());
        }

        if (dto.email() != null && !dto.email().isBlank() && !dto.email().equalsIgnoreCase(email)) {
            if (userRepository.existsByEmail(dto.email())) {
                throw new ConflitoException("E-mail já está em uso: " + dto.email());
            }
            user.setEmail(dto.email().trim().toLowerCase());
        }

        return PerfilResponseDTO.from(userRepository.save(user));
    }

    @Transactional
    public void alterarSenha(String email, AlterarSenhaDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        if (!passwordEncoder.matches(dto.senhaAtual(), user.getPassword())) {
            throw new BadCredentialsException("Senha atual incorreta");
        }

        if (dto.novaSenha() == null || dto.novaSenha().length() < 6) {
            throw new IllegalArgumentException("Nova senha deve ter ao menos 6 caracteres");
        }

        user.setPassword(passwordEncoder.encode(dto.novaSenha()));
        userRepository.save(user);
        auditLogService.log(AuditAction.PASSWORD_CHANGED, "User", user.getId().toString());
    }

    public List<UserResponseDTO> listarPorEmpresa(UUID enterpriseId) {
        return userRepository.findAllByEnterprise_IdOrderByNameAsc(enterpriseId)
                .stream()
                .map(u -> new UserResponseDTO(u.getId(), u.getName(), u.getEmail(), u.getRole(), enterpriseId))
                .toList();
    }

    @Transactional
    public UserResponseDTO convidar(UUID enterpriseId, UserInviteDTO dto, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário requisitante não encontrado"));

        Role targetRole = dto.role() != null ? dto.role() : Role.USER;

        // OWNER só pode convidar USER ou OWNER — não pode criar CEO
        if (requester.getRole() == Role.OWNER && targetRole == Role.CEO) {
            throw new AcessoNegadoException("OWNER não pode criar usuários com papel CEO");
        }

        if (userRepository.existsByEmail(dto.email())) {
            throw new ConflitoException("E-mail já está em uso: " + dto.email());
        }
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));

        User user = User.builder()
                .enterprise(enterprise)
                .name(dto.name().trim())
                .email(dto.email().trim().toLowerCase())
                .password(passwordEncoder.encode(dto.password()))
                .role(targetRole)
                .build();
        User saved = userRepository.save(user);
        auditLogService.log(AuditAction.USER_CREATED, "User", saved.getId().toString());
        return new UserResponseDTO(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole(), enterpriseId);
    }

    @Transactional
    public UserResponseDTO editarUsuario(UUID enterpriseId, UUID id, UserUpdateDTO dto, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário requisitante não encontrado"));
        User user = userRepository.findByIdAndEnterprise_Id(id, enterpriseId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + id));

        // OWNER não pode alterar CEO
        if (requester.getRole() == Role.OWNER && user.getRole() == Role.CEO) {
            throw new AcessoNegadoException("OWNER não pode alterar um usuário CEO");
        }
        // OWNER não pode promover alguém a CEO
        if (requester.getRole() == Role.OWNER && dto.role() == Role.CEO) {
            throw new AcessoNegadoException("OWNER não pode promover um usuário a CEO");
        }

        if (dto.name() != null && !dto.name().isBlank()) {
            user.setName(dto.name().trim());
        }
        if (dto.email() != null && !dto.email().isBlank() && !dto.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(dto.email())) {
                throw new ConflitoException("E-mail já está em uso: " + dto.email());
            }
            user.setEmail(dto.email().trim().toLowerCase());
        }
        if (dto.password() != null && !dto.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(dto.password()));
        }
        if (dto.role() != null) {
            user.setRole(dto.role());
        }
        User saved = userRepository.save(user);
        auditLogService.log(AuditAction.USER_UPDATED, "User", saved.getId().toString());
        return new UserResponseDTO(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole(), enterpriseId);
    }

    @Transactional
    public void deletarUsuario(UUID enterpriseId, UUID id, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário requisitante não encontrado"));
        User user = userRepository.findByIdAndEnterprise_Id(id, enterpriseId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + id));

        if (user.getEmail().equalsIgnoreCase(requesterEmail)) {
            throw new ConflitoException("Não é possível remover seu próprio usuário");
        }
        // OWNER não pode deletar CEO
        if (requester.getRole() == Role.OWNER && user.getRole() == Role.CEO) {
            throw new AcessoNegadoException("OWNER não pode remover um usuário CEO");
        }

        auditLogService.log(AuditAction.USER_DELETED, "User", user.getId().toString());
        userRepository.delete(user);
    }

    @Transactional
    public PerfilResponseDTO atualizarEmpresa(UUID enterpriseId, String email, EnterpriseUpdateDTO dto) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));

        if (dto.name() != null && !dto.name().isBlank()) {
            enterprise.setName(dto.name().trim());
        }

        if (dto.cnpj() != null && !dto.cnpj().isBlank() &&
                !dto.cnpj().equals(enterprise.getCnpj())) {
            if (enterpriseRepository.existsByCnpj(dto.cnpj())) {
                throw new ConflitoException("CNPJ já cadastrado: " + dto.cnpj());
            }
            enterprise.setCnpj(dto.cnpj().trim());
        }

        enterpriseRepository.save(enterprise);

        User user = userRepository.findByEmailWithEnterprise(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
        return PerfilResponseDTO.from(user);
    }
}
