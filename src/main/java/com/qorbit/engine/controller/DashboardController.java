package com.qorbit.engine.controller;

import com.qorbit.engine.model.Execucao;
import com.qorbit.engine.repository.CasoDeTesteRepository;
import com.qorbit.engine.repository.ElementoRepository;
import com.qorbit.engine.repository.ExecucaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    @Autowired private ElementoRepository elementoRepo;
    @Autowired private CasoDeTesteRepository casoRepo;
    @Autowired private ExecucaoRepository execucaoRepo;

    @GetMapping("/")
    public String dashboard(Model model) {
        long totalElementos = elementoRepo.count();
        long totalCasos     = casoRepo.count();

        List<Execucao> execucoes = execucaoRepo.findAllByOrderByIniciadoEmDesc();
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

    @GetMapping("/gerar-codigo")
    public String gerarCodigo() { return "gerar-codigo"; }

    @GetMapping("/casos")
    public String casos() { return "casos"; }

    @GetMapping("/gravar-teste")
    public String gravarTeste() { return "gravar-teste"; }

    @GetMapping("/execucoes")
    public String execucoes(org.springframework.ui.Model model) {
        model.addAttribute("execucoes", execucaoRepo.findAllByOrderByIniciadoEmDesc());
        return "execucoes";
    }
}
