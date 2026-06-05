package com.financeiro_api.Demo;

import com.financeiro_api.Categorias.domain.Categoria;
import com.financeiro_api.Categorias.domain.DreCategoria;
import com.financeiro_api.Categorias.domain.TipoCategoria;
import com.financeiro_api.Categorias.repository.CategoriaRepository;
import com.financeiro_api.ContaBancaria.domain.ContaBancaria;
import com.financeiro_api.ContaBancaria.domain.TipoConta;
import com.financeiro_api.ContaBancaria.repository.ContaBancariaRepository;
import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.repository.ExtratoRepository;
import com.financeiro_api.Previsao.domain.Frequencia;
import com.financeiro_api.Previsao.domain.Previsao;
import com.financeiro_api.Previsao.domain.TipoPrevisao;
import com.financeiro_api.Previsao.repository.PrevisaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class DemoSeedService {

    private final CategoriaRepository categoriaRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final ExtratoRepository extratoRepository;
    private final PrevisaoRepository previsaoRepository;

    public DemoSeedService(CategoriaRepository categoriaRepository,
                           ContaBancariaRepository contaBancariaRepository,
                           ExtratoRepository extratoRepository,
                           PrevisaoRepository previsaoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.extratoRepository = extratoRepository;
        this.previsaoRepository = previsaoRepository;
    }

    @Transactional
    public void seed(UUID enterpriseId) {
        List<Categoria> categorias = criarCategorias(enterpriseId);
        ContaBancaria conta = criarConta(enterpriseId);
        criarExtratos(enterpriseId, categorias, conta.getId());
        criarPrevisoes(enterpriseId, categorias);
    }

    private List<Categoria> criarCategorias(UUID enterpriseId) {
        record CatDef(String nome, TipoCategoria tipo, DreCategoria dre) {}

        List<CatDef> defs = List.of(
            new CatDef("Receita Operacional",   TipoCategoria.RECEITA,  DreCategoria.RECEITA_BRUTA),
            new CatDef("Serviços Prestados",    TipoCategoria.RECEITA,  DreCategoria.RECEITA_BRUTA),
            new CatDef("Folha de Pagamento",    TipoCategoria.DESPESA,  DreCategoria.DESPESA_ADMINISTRATIVA),
            new CatDef("Aluguel",               TipoCategoria.DESPESA,  DreCategoria.DESPESA_ADMINISTRATIVA),
            new CatDef("Marketing",             TipoCategoria.DESPESA,  DreCategoria.DESPESA_VENDAS),
            new CatDef("Outras Despesas",       TipoCategoria.DESPESA,  DreCategoria.OUTROS)
        );

        List<Categoria> result = new ArrayList<>();
        for (CatDef def : defs) {
            if (!categoriaRepository.existsByEnterpriseIdAndName(enterpriseId, def.nome())) {
                result.add(categoriaRepository.save(
                    Categoria.builder()
                        .enterpriseId(enterpriseId)
                        .name(def.nome())
                        .tipo(def.tipo())
                        .dreCategoria(def.dre())
                        .build()
                ));
            } else {
                categoriaRepository.findAllByEnterpriseId(enterpriseId).stream()
                    .filter(c -> c.getName().equals(def.nome()))
                    .findFirst()
                    .ifPresent(result::add);
            }
        }
        return result;
    }

    private ContaBancaria criarConta(UUID enterpriseId) {
        return contaBancariaRepository.findAllByEnterpriseIdAndAtivaTrue(enterpriseId)
            .stream()
            .filter(c -> c.getNome().equals("Conta Principal"))
            .findFirst()
            .orElseGet(() -> contaBancariaRepository.save(
                ContaBancaria.builder()
                    .enterpriseId(enterpriseId)
                    .nome("Conta Principal")
                    .banco("Nubank")
                    .tipo(TipoConta.CORRENTE)
                    .ativa(true)
                    .build()
            ));
    }

    private void criarExtratos(UUID enterpriseId, List<Categoria> categorias, UUID contaId) {
        Random rng = new Random(42);

        List<Categoria> receitas = categorias.stream()
            .filter(c -> c.getTipo() == TipoCategoria.RECEITA).toList();
        List<Categoria> despesas = categorias.stream()
            .filter(c -> c.getTipo() == TipoCategoria.DESPESA).toList();

        String[][] receitaDescricoes = {
            {"Cliente ABC Ltda", "Nota fiscal 001"},
            {"Cliente XYZ S.A.", "Nota fiscal 002"},
            {"Consultoria mensal", "Pagamento recebido"},
            {"Projeto Alpha", "Parcela 1/3"},
            {"Retorno de investimento", "Rendimento"},
        };

        String[][] despesaDescricoes = {
            {"Aluguel escritório", "Competência"},
            {"Folha de pagamento", "Salários"},
            {"Google Ads", "Campanha mensal"},
            {"Fornecedor Suprimentos", "Nota 045"},
            {"Energia elétrica", "Fatura"},
            {"Internet e telefone", "Mensal"},
            {"Contador", "Honorários"},
            {"Material de escritório", "Compra"},
        };

        LocalDate hoje = LocalDate.now();

        for (int m = 5; m >= 0; m--) {
            YearMonth ym = YearMonth.from(hoje).minusMonths(m);
            int totalEntradas = 3 + rng.nextInt(3);
            int totalSaidas = 5 + rng.nextInt(6);

            for (int i = 0; i < totalEntradas; i++) {
                int dia = 1 + rng.nextInt(ym.lengthOfMonth());
                BigDecimal valor = BigDecimal.valueOf(5000 + rng.nextInt(10001));
                Categoria cat = receitas.get(rng.nextInt(receitas.size()));
                String[] desc = receitaDescricoes[rng.nextInt(receitaDescricoes.length)];
                String hash = "demo-" + UUID.randomUUID();

                if (!extratoRepository.existsByEnterpriseIdAndImportHash(enterpriseId, hash)) {
                    extratoRepository.save(Extrato.builder()
                        .enterpriseId(enterpriseId)
                        .data(LocalDate.of(ym.getYear(), ym.getMonthValue(), dia))
                        .razaoSocial(desc[0])
                        .tipoPagamento(desc[1])
                        .valor(valor)
                        .categoriaId(cat.getId())
                        .contaBancariaId(contaId)
                        .conciliado(m > 0)
                        .importHash(hash)
                        .build()
                    );
                }
            }

            for (int i = 0; i < totalSaidas; i++) {
                int dia = 1 + rng.nextInt(ym.lengthOfMonth());
                BigDecimal valor = BigDecimal.valueOf(-(500 + rng.nextInt(7501)));
                Categoria cat = despesas.get(rng.nextInt(despesas.size()));
                String[] desc = despesaDescricoes[rng.nextInt(despesaDescricoes.length)];
                String hash = "demo-" + UUID.randomUUID();

                if (!extratoRepository.existsByEnterpriseIdAndImportHash(enterpriseId, hash)) {
                    extratoRepository.save(Extrato.builder()
                        .enterpriseId(enterpriseId)
                        .data(LocalDate.of(ym.getYear(), ym.getMonthValue(), dia))
                        .razaoSocial(desc[0])
                        .tipoPagamento(desc[1])
                        .valor(valor)
                        .categoriaId(cat.getId())
                        .contaBancariaId(contaId)
                        .conciliado(m > 0)
                        .importHash(hash)
                        .build()
                    );
                }
            }
        }
    }

    private void criarPrevisoes(UUID enterpriseId, List<Categoria> categorias) {
        Categoria aluguel = categorias.stream()
            .filter(c -> c.getName().equals("Aluguel")).findFirst().orElse(null);
        Categoria folha = categorias.stream()
            .filter(c -> c.getName().equals("Folha de Pagamento")).findFirst().orElse(null);
        Categoria receita = categorias.stream()
            .filter(c -> c.getTipo() == TipoCategoria.RECEITA).findFirst().orElse(null);

        List<Previsao> previsoes = List.of(
            Previsao.builder()
                .enterpriseId(enterpriseId)
                .descricao("Aluguel do escritório")
                .tipo(TipoPrevisao.DESPESA)
                .valor(BigDecimal.valueOf(3500))
                .frequencia(Frequencia.MENSAL)
                .dataInicio(LocalDate.now().withDayOfMonth(1))
                .diaRecorrencia(5)
                .categoriaId(aluguel != null ? aluguel.getId() : null)
                .ativa(true)
                .build(),
            Previsao.builder()
                .enterpriseId(enterpriseId)
                .descricao("Folha de pagamento")
                .tipo(TipoPrevisao.DESPESA)
                .valor(BigDecimal.valueOf(8000))
                .frequencia(Frequencia.MENSAL)
                .dataInicio(LocalDate.now().withDayOfMonth(1))
                .diaRecorrencia(1)
                .categoriaId(folha != null ? folha.getId() : null)
                .ativa(true)
                .build(),
            Previsao.builder()
                .enterpriseId(enterpriseId)
                .descricao("Recebimento mensal de serviços")
                .tipo(TipoPrevisao.RECEITA)
                .valor(BigDecimal.valueOf(12000))
                .frequencia(Frequencia.MENSAL)
                .dataInicio(LocalDate.now().withDayOfMonth(1))
                .diaRecorrencia(15)
                .categoriaId(receita != null ? receita.getId() : null)
                .ativa(true)
                .build()
        );

        previsaoRepository.saveAll(previsoes);
    }
}
