package com.qorbit.engine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import com.qorbit.engine.model.CasoDeTeste;
import com.qorbit.engine.model.StepTeste;
import com.qorbit.engine.model.SuiteTeste;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import com.qorbit.engine.repository.SuiteTesteRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

import java.util.*;

@RestController
@RequestMapping("/api/db")
public class DbDiagnosticoController {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CasoDeTesteRepository casoRepo;

    @Autowired
    private SuiteTesteRepository suiteRepo;

    @Autowired
    private QorbitUserRepository userRepo;

    /**
     * Obtém o usuário autenticado do contexto de segurança
     */
    private QorbitUser getUsuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            return userRepo.findByEmailIgnoreCase(email).orElse(null);
        }
        return null;
    }

    @GetMapping("/tabelas")
    public Map<String, Object> tabelas() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        try {
            // Lista todas as tabelas
            List<String> tabelas = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
                String.class
            );
            resultado.put("tabelas", tabelas);

            // Para cada tabela, mostra estrutura e contagem
            Map<String, Object> detalhes = new LinkedHashMap<>();
            for (String tabela : tabelas) {
                // Valida o nome da tabela contra a lista retornada pelo próprio SQLite
                // para evitar SQL injection por concatenação
                if (!tabela.matches("[a-zA-Z0-9_]+")) continue;

                Map<String, Object> info = new LinkedHashMap<>();
                List<Map<String, Object>> colunas = jdbc.queryForList("PRAGMA table_info(" + tabela + ")");
                info.put("colunas", colunas.stream().map(c -> c.get("name") + " (" + c.get("type") + ")").toList());
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Integer.class);
                info.put("totalRegistros", count);
                detalhes.put(tabela, info);
            }
            resultado.put("detalhes", detalhes);
        } catch (Exception e) {
            resultado.put("erro", e.getMessage());
        }
        return resultado;
    }

    @GetMapping("/casos")
    public Map<String, Object> casos() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> casos = jdbc.queryForList("SELECT * FROM casos_de_teste");
            resultado.put("total", casos.size());
            resultado.put("casos", casos);
        } catch (Exception e) {
            resultado.put("erro", e.getMessage());
        }
        return resultado;
    }

    @GetMapping("/steps")
    public Map<String, Object> steps() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> steps = jdbc.queryForList(
                "SELECT s.*, c.nome as caso_nome FROM steps_teste s " +
                "LEFT JOIN casos_de_teste c ON s.caso_de_teste_id = c.id " +
                "ORDER BY s.caso_de_teste_id, s.numero_step"
            );
            resultado.put("total", steps.size());
            resultado.put("steps", steps);
        } catch (Exception e) {
            resultado.put("erro", e.getMessage());
        }
        return resultado;
    }

    @GetMapping("/testar-save")
    @Transactional
    public Map<String, Object> testarSave() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        try {
            QorbitUser usuario = getUsuarioAutenticado();
            if (usuario == null) {
                resultado.put("sucesso", false);
                resultado.put("erro", "Usuário não autenticado");
                return resultado;
            }

            CasoDeTeste caso = new CasoDeTeste();
            caso.setNome("Teste Diagnostico " + System.currentTimeMillis());
            caso.setModulo("Diagnostico");
            caso.setCodigo("CT-DIAG");
            caso.setStatus("ATIVO");
            caso.setUsuario(usuario);

            StepTeste step = new StepTeste();
            step.setNumeroStep(1);
            step.setAcao("NAVEGAR");
            step.setValorEntrada("https://exemplo.com");
            step.setDescricaoGherkin("acesso a URL exemplo");
            step.setCasoDeTeste(caso);

            caso.setSteps(new ArrayList<>(List.of(step)));
            CasoDeTeste salvo = casoRepo.save(caso);

            resultado.put("sucesso", true);
            resultado.put("casoId", salvo.getId());
            resultado.put("totalCasosNoBanco", casoRepo.count());
        } catch (Exception e) {
            resultado.put("sucesso", false);
            resultado.put("erro", "Erro interno ao executar diagnóstico");
            System.err.println("Erro testar-save: " + e.getMessage());
            if (e.getCause() != null) System.err.println("Causa: " + e.getCause().getMessage());
        }
        return resultado;
    }

    /**
     * Migração: Atribui todos os testes orphãos (sem usuário) ao usuário autenticado
     * Resolve o problema de testes fantasmas que ninguém consegue ver
     */
    @PostMapping("/migrar-orfaos")
    @Transactional
    public Map<String, Object> migrarOrfaos() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        try {
            QorbitUser usuario = getUsuarioAutenticado();
            if (usuario == null) {
                resultado.put("sucesso", false);
                resultado.put("erro", "Usuário não autenticado");
                return resultado;
            }

            // Buscar todos os casos sem usuário
            List<CasoDeTeste> casosOrfaos = jdbc.query(
                "SELECT id, codigo, nome, modulo, descricao, url_alvo, status, criado_em FROM casos_de_teste WHERE usuario_id IS NULL",
                (rs, rowNum) -> {
                    CasoDeTeste c = new CasoDeTeste();
                    c.setId(rs.getLong("id"));
                    c.setCodigo(rs.getString("codigo"));
                    c.setNome(rs.getString("nome"));
                    c.setModulo(rs.getString("modulo"));
                    c.setDescricao(rs.getString("descricao"));
                    c.setUrlAlvo(rs.getString("url_alvo"));
                    c.setStatus(rs.getString("status"));
                    c.setCriadoEm(rs.getString("criado_em"));
                    c.setUsuario(usuario);
                    return c;
                }
            );

            // Buscar todas as suites sem usuário
            List<SuiteTeste> suitesOrfas = jdbc.query(
                "SELECT id, nome, descricao, cor, criado_em, ultima_execucao, ultimo_resultado FROM suite_teste WHERE usuario_id IS NULL",
                (rs, rowNum) -> {
                    SuiteTeste s = new SuiteTeste();
                    s.setId(rs.getLong("id"));
                    s.setNome(rs.getString("nome"));
                    s.setDescricao(rs.getString("descricao"));
                    s.setCor(rs.getString("cor"));
                    s.setCriadoEm(rs.getString("criado_em"));
                    s.setUltimaExecucao(rs.getString("ultima_execucao"));
                    s.setUltimoResultado(rs.getString("ultimo_resultado"));
                    s.setUsuario(usuario);
                    return s;
                }
            );

            // Atualizar os testes no banco
            int casosAtualizados = 0;
            for (CasoDeTeste caso : casosOrfaos) {
                casoRepo.save(caso);
                casosAtualizados++;
            }

            int suitesAtualizadas = 0;
            for (SuiteTeste suite : suitesOrfas) {
                suiteRepo.save(suite);
                suitesAtualizadas++;
            }

            resultado.put("sucesso", true);
            resultado.put("casosAtribuidos", casosAtualizados);
            resultado.put("suitesAtribuidas", suitesAtualizadas);
            resultado.put("mensagem", casosAtualizados + " casos e " + suitesAtualizadas + " suites atribuidos ao usuário");
        } catch (Exception e) {
            resultado.put("sucesso", false);
            resultado.put("erro", e.getMessage());
            System.err.println("Erro migrar-orfaos: " + e.getMessage());
            e.printStackTrace();
        }
        return resultado;
    }
}
