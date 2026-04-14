package com.qorbit.engine.controller;

import com.qorbit.engine.auth.model.QorbitUser;
import com.qorbit.engine.auth.repository.QorbitUserRepository;
import com.qorbit.engine.model.Execucao;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import com.qorbit.engine.repository.ElementoRepository;
import com.qorbit.engine.repository.ExecucaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    @Autowired private ElementoRepository elementoRepo;
    @Autowired private CasoDeTesteRepository casoRepo;
    @Autowired private ExecucaoRepository execucaoRepo;
    @Autowired private QorbitUserRepository userRepo;

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

    @GetMapping("/")
    public String dashboard(Model model) {
        QorbitUser usuario = getUsuarioAutenticado();
        
        long totalElementos = usuario != null ? elementoRepo.countByUsuario(usuario) : 0;
        long totalCasos     = usuario != null ? casoRepo.findByUsuario(usuario).size() : 0;

        List<Execucao> execucoes = usuario != null 
            ? execucaoRepo.findByUsuarioOrderByIdDesc(usuario)
            : List.of();
        Execucao ultima = execucoes.isEmpty() ? null : execucoes.get(0);

        model.addAttribute("totalElementos", totalElementos);
        model.addAttribute("totalCasos",     totalCasos);
        model.addAttribute("ultimaExecucao", ultima);
        model.addAttribute("execucoes",      execucoes.stream().limit(5).toList());

        return "dashboard";
    }

    @GetMapping("/elementos")
    public String elementos() { return "elementos"; }

    @GetMapping("/nova-execucao")
    public String novaExecucao() { return "nova-execucao"; }

    @GetMapping("/evidencias")
    public String evidencias() { return "evidencias"; }

    @GetMapping("/exportar-codigo")
    public String exportarCodigo() { return "gerar-codigo"; }

    @GetMapping("/gerar-codigo")
    public String gerarCodigoLegacy() { return "redirect:/exportar-codigo"; }

    @GetMapping("/casos")
    public String casos() { return "casos"; }

    @GetMapping("/gravar-teste")
    public String gravarTeste() { return "gravar-teste"; }

    @GetMapping("/execucoes")
    public String execucoes(org.springframework.ui.Model model) {
        QorbitUser usuario = getUsuarioAutenticado();
        List<Execucao> minhasExecucoes = usuario != null 
            ? execucaoRepo.findByUsuarioOrderByIdDesc(usuario)
            : List.of();
        model.addAttribute("execucoes", minhasExecucoes);
        return "execucoes";
    }
}
