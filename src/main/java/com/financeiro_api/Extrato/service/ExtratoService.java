package com.financeiro_api.Extrato.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.service.AuditLogService;
import com.financeiro_api.Categorias.domain.Categoria;
import com.financeiro_api.Categorias.domain.TipoCategoria;
import com.financeiro_api.Categorias.service.CategoriaService;
import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.dto.AtribuirCategoriaDTO;
import com.financeiro_api.Extrato.dto.AtribuirContaDTO;
import com.financeiro_api.Extrato.dto.CriarExtratoDTO;
import com.financeiro_api.Extrato.dto.EditarExtratoDTO;
import com.financeiro_api.Extrato.dto.ExtratoResponseDTO;
import com.financeiro_api.Extrato.repository.ExtratoRepository;
import com.financeiro_api.shared.TenantContext;
import com.financeiro_api.shared.exception.ConflitoException;
import com.financeiro_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ExtratoService {

    private final ExtratoRepository repository;
    private final CategoriaService categoriaService;
    private final AuditLogService auditLogService;

    public ExtratoService(ExtratoRepository repository, CategoriaService categoriaService,
                          AuditLogService auditLogService) {
        this.repository = repository;
        this.categoriaService = categoriaService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ExtratoResponseDTO> listarPorMes(int mes, int ano) {
        return repository.findAllByEnterpriseIdAndMesAndAnoOrderByDataAsc(TenantContext.get(), mes, ano)
                .stream().map(ExtratoResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ExtratoResponseDTO> listarPorPeriodo(LocalDate inicio, LocalDate fim) {
        return repository.findAllByEnterpriseIdAndDataBetweenOrderByDataAsc(TenantContext.get(), inicio, fim)
                .stream().map(ExtratoResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ExtratoResponseDTO> buscarComFiltro(LocalDate inicio, LocalDate fim, String razaoSocial) {
        return repository.buscarComFiltro(TenantContext.get(), inicio, fim, razaoSocial)
                .stream().map(ExtratoResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<ExtratoResponseDTO> listarPaginado(int mes, int ano, UUID categoriaId, UUID contaBancariaId, String tipo, Pageable pageable) {
        return repository.buscarPorMesAnoComFiltros(TenantContext.get(), mes, ano, categoriaId, contaBancariaId, tipo, pageable)
                .map(ExtratoResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public List<ExtratoResponseDTO> listarSemCategoria(int mes, int ano) {
        return repository.findSemCategoria(TenantContext.get(), mes, ano)
                .stream().map(ExtratoResponseDTO::from).toList();
    }

    @Transactional
    public ExtratoResponseDTO atribuirCategoria(UUID id, AtribuirCategoriaDTO dto) {
        UUID tenantId = TenantContext.get();
        Extrato extrato = repository.findByEnterpriseIdAndId(tenantId, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Extrato não encontrado: " + id));

        Categoria categoria = categoriaService.buscarPorId(dto.categoriaId());

        // Valor positivo → só RECEITA; valor negativo → só DESPESA
        BigDecimal valor = extrato.getValor();
        if (valor != null && valor.compareTo(BigDecimal.ZERO) != 0) {
            boolean valorPositivo = valor.compareTo(BigDecimal.ZERO) > 0;
            boolean categoriaReceita = categoria.getTipo() == TipoCategoria.RECEITA;
            if (valorPositivo != categoriaReceita) {
                String esperado = valorPositivo ? "RECEITA" : "DESPESA";
                throw new ConflitoException(
                        "Lançamento " + (valorPositivo ? "positivo" : "negativo") +
                        " só aceita categoria do tipo " + esperado);
            }
        }

        extrato.setCategoriaId(dto.categoriaId());
        return ExtratoResponseDTO.from(repository.save(extrato));
    }

    @Transactional
    public ExtratoResponseDTO atribuirConta(UUID id, AtribuirContaDTO dto) {
        Extrato extrato = repository.findByEnterpriseIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Extrato não encontrado: " + id));
        extrato.setContaBancariaId(dto.contaBancariaId());
        return ExtratoResponseDTO.from(repository.save(extrato));
    }

    @Transactional
    public void deletar(UUID id) {
        Extrato extrato = repository.findByEnterpriseIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Extrato não encontrado: " + id));
        repository.delete(extrato);
        auditLogService.log(AuditAction.EXTRATO_DELETED, "Extrato", id.toString());
    }

    @Transactional
    public void cancelarLote(UUID batchId) {
        repository.deleteByEnterpriseIdAndImportBatchId(TenantContext.get(), batchId);
        auditLogService.log(AuditAction.EXTRATO_BATCH_DELETED, "Extrato", batchId.toString());
    }

    @Transactional
    public ExtratoResponseDTO criarManual(CriarExtratoDTO dto) {
        UUID enterpriseId = TenantContext.get();
        boolean isReceita = "RECEITA".equalsIgnoreCase(dto.tipo());
        BigDecimal valor = isReceita ? dto.valor().abs() : dto.valor().abs().negate();

        Extrato extrato = Extrato.builder()
                .enterpriseId(enterpriseId)
                .data(dto.data())
                .razaoSocial(dto.descricao())
                .valor(valor)
                .categoriaId(dto.categoriaId())
                .contaBancariaId(dto.contaBancariaId())
                .importHash("manual-" + java.util.UUID.randomUUID())
                .build();

        ExtratoResponseDTO result = ExtratoResponseDTO.from(repository.save(extrato));
        auditLogService.log(AuditAction.EXTRATO_CREATED, "Extrato", result.id().toString());
        return result;
    }

    @Transactional
    public ExtratoResponseDTO editar(UUID id, EditarExtratoDTO dto) {
        Extrato extrato = repository.findByEnterpriseIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Extrato não encontrado: " + id));

        if (dto.data() != null) {
            extrato.setData(dto.data());
            extrato.setMes(dto.data().getMonthValue());
            extrato.setAno(dto.data().getYear());
        }
        if (dto.descricao() != null && !dto.descricao().isBlank()) {
            extrato.setRazaoSocial(dto.descricao());
        }
        if (dto.valor() != null) {
            extrato.setValor(dto.valor());
        }

        ExtratoResponseDTO result = ExtratoResponseDTO.from(repository.save(extrato));
        auditLogService.log(AuditAction.EXTRATO_UPDATED, "Extrato", id.toString());
        return result;
    }
}
