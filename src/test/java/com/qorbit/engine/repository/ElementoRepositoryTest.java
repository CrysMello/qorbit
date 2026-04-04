package com.qorbit.engine.repository;

import com.qorbit.engine.model.Elemento;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes de repositório usando H2 em memória.
 * Valida todas as queries customizadas do ElementoRepository.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ElementoRepository — Testes de repositório")
class ElementoRepositoryTest {

    @Autowired
    private ElementoRepository repo;

    private Elemento criarElemento(String nomeLogico, String pagina, String seletor, String status) {
        Elemento e = new Elemento();
        e.setNomeLogico(nomeLogico);
        e.setPagina(pagina);
        e.setSeletorTecnico(seletor);
        e.setTipoSeletor("CSS");
        e.setStatus(status);
        return repo.save(e);
    }

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        criarElemento("BotaoSalvar",     "CadastroCliente", "#btn-salvar",   "ATIVO");
        criarElemento("CampoNome",       "CadastroCliente", "input[name=n]", "ATIVO");
        criarElemento("CampoEmail",      "CadastroCliente", "input[type=e]", "PENDENTE");
        criarElemento("BotaoCancelar",   "CadastroCliente", "#btn-cancel",   "ATIVO");
        criarElemento("MenuNavegacao",   "PaginaInicial",   "#nav-main",     "ATIVO");
        criarElemento("BotaoPesquisar",  "Pesquisa",        "#btn-search",   "PENDENTE");
    }

    // ── Testes de busca por página ───────────────────────────────────────────

    @Test
    @DisplayName("findByPaginaIgnoreCase — deve retornar todos os elementos da página informada")
    void findByPagina_deveRetornarElementosDaPagina() {
        List<Elemento> resultado = repo.findByPaginaIgnoreCase("CadastroCliente");

        assertThat(resultado).hasSize(4);
        assertThat(resultado).extracting(Elemento::getPagina)
            .allMatch(p -> p.equalsIgnoreCase("CadastroCliente"));
    }

    @Test
    @DisplayName("findByPaginaIgnoreCase — deve ser case-insensitive")
    void findByPagina_deveSercaseInsensitive() {
        List<Elemento> comMaiuscula = repo.findByPaginaIgnoreCase("CADASTROCLIENTE");
        List<Elemento> comMinuscula = repo.findByPaginaIgnoreCase("cadastrocliente");

        assertThat(comMaiuscula).hasSameElementsAs(comMinuscula);
    }

    @Test
    @DisplayName("findByPaginaIgnoreCase — deve retornar lista vazia para página inexistente")
    void findByPagina_deveRetornarVazioParaPaginaInexistente() {
        List<Elemento> resultado = repo.findByPaginaIgnoreCase("PaginaQueNaoExiste");

        assertThat(resultado).isEmpty();
    }

    // ── Testes de busca por status ───────────────────────────────────────────

    @Test
    @DisplayName("findByStatus — deve retornar apenas elementos com status ATIVO")
    void findByStatus_deveRetornarAtivos() {
        List<Elemento> ativos = repo.findByStatus("ATIVO");

        assertThat(ativos).hasSize(4);
        assertThat(ativos).extracting(Elemento::getStatus)
            .allMatch(s -> s.equals("ATIVO"));
    }

    @Test
    @DisplayName("findByStatus — deve retornar apenas elementos com status PENDENTE")
    void findByStatus_deveRetornarPendentes() {
        List<Elemento> pendentes = repo.findByStatus("PENDENTE");

        assertThat(pendentes).hasSize(2);
        assertThat(pendentes).extracting(Elemento::getNomeLogico)
            .containsExactlyInAnyOrder("CampoEmail", "BotaoPesquisar");
    }

    // ── Testes de busca por nome lógico e seletor ────────────────────────────

    @Test
    @DisplayName("findByNomeLogicoContaining — deve encontrar por parte do nome")
    void findByNomeLogico_deveEncontrarPorParteDoNome() {
        List<Elemento> resultado = repo.findByNomeLogicoContainingIgnoreCaseOrSeletorTecnicoContainingIgnoreCase("Botao", "Botao");

        assertThat(resultado).hasSize(3);
        assertThat(resultado).extracting(Elemento::getNomeLogico)
            .containsExactlyInAnyOrder("BotaoSalvar", "BotaoCancelar", "BotaoPesquisar");
    }

    @Test
    @DisplayName("findByNomeLogicoContaining — deve buscar no seletor técnico também")
    void findByNomeLogico_deveBuscarNoSeletor() {
        List<Elemento> resultado = repo.findByNomeLogicoContainingIgnoreCaseOrSeletorTecnicoContainingIgnoreCase("xyz", "#btn");

        assertThat(resultado).hasSize(3);
    }

    @Test
    @DisplayName("findByNomeLogicoContaining — deve retornar vazio quando não encontrar")
    void findByNomeLogico_deveRetornarVazioSemResultado() {
        List<Elemento> resultado = repo.findByNomeLogicoContainingIgnoreCaseOrSeletorTecnicoContainingIgnoreCase("xyzabc", "xyzabc");

        assertThat(resultado).isEmpty();
    }

    // ── Testes de busca por nome + página ────────────────────────────────────

    @Test
    @DisplayName("findByNomeLogicoAndPagina — deve encontrar elemento específico")
    void findByNomeLogicoAndPagina_deveEncontrarElemento() {
        Optional<Elemento> resultado = repo.findByNomeLogicoAndPagina("BotaoSalvar", "CadastroCliente");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getSeletorTecnico()).isEqualTo("#btn-salvar");
    }

    @Test
    @DisplayName("findByNomeLogicoAndPagina — deve retornar vazio quando não existe")
    void findByNomeLogicoAndPagina_deveRetornarVazioSemResultado() {
        Optional<Elemento> resultado = repo.findByNomeLogicoAndPagina("BotaoSalvar", "PaginaErrada");

        assertThat(resultado).isEmpty();
    }

    // ── Testes de unicidade ──────────────────────────────────────────────────

    @Test
    @DisplayName("existsByNomeLogicoAndPaginaAndIdNot — deve detectar duplicata")
    void existsByNomeLogico_deveDetectarDuplicata() {
        Elemento existente = repo.findByNomeLogicoAndPagina("BotaoSalvar", "CadastroCliente").get();

        boolean existe = repo.existsByNomeLogicoAndPaginaAndIdNot(
            "BotaoSalvar", "CadastroCliente", existente.getId() + 999L
        );

        assertThat(existe).isTrue();
    }

    @Test
    @DisplayName("existsByNomeLogicoAndPaginaAndIdNot — deve ignorar o próprio registro ao editar")
    void existsByNomeLogico_deveIgnorarProprioRegistro() {
        Elemento existente = repo.findByNomeLogicoAndPagina("BotaoSalvar", "CadastroCliente").get();

        boolean existe = repo.existsByNomeLogicoAndPaginaAndIdNot(
            "BotaoSalvar", "CadastroCliente", existente.getId()
        );

        assertThat(existe).isFalse();
    }

    // ── Testes de ordenação ──────────────────────────────────────────────────

    @Test
    @DisplayName("findAllByOrderByPaginaAscNomeLogicoAsc — deve retornar ordenado por página e nome")
    void findAllOrdered_deveRetornarOrdenado() {
        List<Elemento> resultado = repo.findAllByOrderByPaginaAscNomeLogicoAsc();

        assertThat(resultado).isNotEmpty();
        // Verifica que a ordenação por página está correta
        for (int i = 0; i < resultado.size() - 1; i++) {
            String paginaAtual    = resultado.get(i).getPagina();
            String paginaProxima  = resultado.get(i + 1).getPagina();
            int comparacao = paginaAtual.compareTo(paginaProxima);
            assertThat(comparacao).isLessThanOrEqualTo(0);

            // Se mesma página, verifica ordenação por nome
            if (comparacao == 0) {
                assertThat(resultado.get(i).getNomeLogico())
                    .isLessThanOrEqualTo(resultado.get(i + 1).getNomeLogico());
            }
        }
    }

    // ── Testes de CRUD básico ────────────────────────────────────────────────

    @Test
    @DisplayName("save — deve aplicar @PrePersist e definir status PENDENTE por padrão")
    void save_deveAplicarPrePersist() {
        Elemento novo = new Elemento();
        novo.setNomeLogico("NovoBotao");
        novo.setPagina("TestePage");
        novo.setSeletorTecnico("#novo");
        novo.setTipoSeletor("CSS");

        Elemento salvo = repo.save(novo);

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getStatus()).isEqualTo("PENDENTE");
        assertThat(salvo.getCriadoEm()).isNotNull();
        assertThat(salvo.getAtualizadoEm()).isNotNull();
    }

    @Test
    @DisplayName("delete — deve remover elemento corretamente")
    void delete_deveRemoverElemento() {
        Elemento existente = repo.findByNomeLogicoAndPagina("BotaoSalvar", "CadastroCliente").get();
        Long id = existente.getId();

        repo.deleteById(id);

        assertThat(repo.findById(id)).isEmpty();
    }
}
