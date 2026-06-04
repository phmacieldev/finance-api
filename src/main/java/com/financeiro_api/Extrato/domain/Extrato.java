package com.financeiro_api.Extrato.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "extrato",
        uniqueConstraints = @UniqueConstraint(columnNames = {"enterprise_id", "import_hash"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Extrato {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "enterprise_id", nullable = false)
    private UUID enterpriseId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "tipo_pagamento", length = 50)
    private String tipoPagamento;

    @Column(name = "razao_social", length = 255)
    private String razaoSocial;

    @Column(name = "cpf_cnpj", length = 20)
    private String cpfCnpj;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(precision = 15, scale = 2)
    private BigDecimal saldo;

    @Column(name = "categoria_id")
    private UUID categoriaId;

    @Column(nullable = false)
    private boolean conciliado = false;

    @Column(nullable = false)
    private int mes;

    @Column(nullable = false)
    private int ano;

    @Column(name = "conta_bancaria_id")
    private UUID contaBancariaId;

    @Column(name = "import_hash", nullable = false, length = 64)
    private String importHash;

    @Column(name = "import_batch_id")
    private UUID importBatchId;

    @Column(name = "importado_em", nullable = false)
    private LocalDateTime importadoEm;

    @PrePersist
    public void prePersist() {
        if (data != null) {
            mes = data.getMonthValue();
            ano = data.getYear();
        }
        importadoEm = LocalDateTime.now();
    }
}
