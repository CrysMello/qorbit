package com.qorbit.engine.repository;

import com.qorbit.engine.model.CasoDeTeste;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("CasoDeTesteRepository — Testes de repositório")
class CasoDeTesteRepositoryTest {

    @Autowired
    private CasoDeTesteRepository repo;

    private CasoDeTeste criarCaso(String nome, String modulo, String status) {
        CasoDeTeste c = new CasoDeTeste();
        c.setNome(nome);
        c.setModulo(modulo);
        c.setStatus(status);
        c.setCodigo("CT-" + nome.substring(0, 2).toUpperCase());
        return repo.save(c);
    }

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        criarCaso("CadastroCliente completo", "Cadastros", "ATIVO");
        criarCaso("Editar dados do cliente",  "Cadastros", "ATIVO");
        criarCaso("Excluir cliente",          "Cadastros", "INATIVO");
        criarCaso("Login com credenciais",    "Autenticacao", "ATIVO");
        criarCaso("Login senha incorreta",    "Autenticacao", "ATIVO");
        criarCaso("Emitir relatorio mensal",  "Relatorios", "ATIVO");
    }

    @Test
    @DisplayName("findByStatus — deve retornar apenas casos ATIVO")
    void findByStatus_deveRetornarAtivos() {
        List<CasoDeTeste> ativos = repo.findByStatus("ATIVO");

        assertThat(ativos).hasSize(5);
        assertThat(ativos).extracting(CasoDeTeste::getStatus)
            .allMatch(s -> s.equals("ATIVO"));
    }

    @Test
    @DisplayName("findByStatus — deve retornar apenas casos INATIVO")
    void findByStatus_deveRetornarInativos() {
        List<CasoDeTeste> inativos = repo.findByStatus("INATIVO");

        assertThat(inativos).hasSize(1);
        assertThat(inativos.get(0).getNome()).isEqualTo("Excluir cliente");
    }

    @Test
    @DisplayName("findByModuloIgnoreCase — deve retornar casos do módulo informado")
    void findByModulo_deveRetornarCasosDoModulo() {
        List<CasoDeTeste> cadastros = repo.findByModuloIgnoreCase("Cadastros");

        assertThat(cadastros).hasSize(3);
        assertThat(cadastros).extracting(CasoDeTeste::getModulo)
            .allMatch(m -> m.equalsIgnoreCase("Cadastros"));
    }

    @Test
    @DisplayName("findByModuloIgnoreCase — deve ser case-insensitive")
    void findByModulo_deveSercaseInsensitive() {
        List<CasoDeTeste> upper  = repo.findByModuloIgnoreCase("AUTENTICACAO");
        List<CasoDeTeste> lower  = repo.findByModuloIgnoreCase("autenticacao");

        assertThat(upper).hasSameElementsAs(lower);
        assertThat(upper).hasSize(2);
    }

    @Test
    @DisplayName("findByNomeContainingIgnoreCase — deve encontrar por parte do nome")
    void findByNome_deveEncontrarPorParteDoNome() {
        List<CasoDeTeste> resultado = repo.findByNomeContainingIgnoreCase("cliente");

        assertThat(resultado).hasSize(3);
    }

    @Test
    @DisplayName("findByNomeContainingIgnoreCase — deve retornar vazio quando não encontrar")
    void findByNome_deveRetornarVazioSemResultado() {
        List<CasoDeTeste> resultado = repo.findByNomeContainingIgnoreCase("xyznotfound");

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("save — @PrePersist deve definir status ATIVO por padrão")
    void save_deveDefinirStatusPadrao() {
        CasoDeTeste novo = new CasoDeTeste();
        novo.setNome("Novo caso sem status");
        novo.setCodigo("CT-99");

        CasoDeTeste salvo = repo.save(novo);

        assertThat(salvo.getStatus()).isEqualTo("ATIVO");
        assertThat(salvo.getCriadoEm()).isNotNull();
    }

    @Test
    @DisplayName("count — deve retornar a contagem correta")
    void count_deveRetornarContagemCorreta() {
        assertThat(repo.count()).isEqualTo(6);
    }
}
