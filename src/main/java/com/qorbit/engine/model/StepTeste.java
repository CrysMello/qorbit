package com.qorbit.engine.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "steps_teste")
@Data
@NoArgsConstructor
public class StepTeste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_de_teste_id")
    @JsonBackReference
    private CasoDeTeste casoDeTeste;

    @Column(name = "numero_step")
    private Integer numeroStep;

    @Column(name = "acao")
    private String acao;

    @Column(name = "nome_logico_elemento")
    private String nomeLogicoElemento;

    @Column(name = "valor_entrada", length = 500)
    private String valorEntrada;

    @Column(name = "resultado_esperado", length = 500)
    private String resultadoEsperado;

    @Column(name = "descricao_gherkin", length = 500)
    private String descricaoGherkin;

    // ── aliases usados pelo SeleniumWorker ────────────────────────────────────

    /** Alias para valorEntrada (usado em ações INPUT/SELECT/WAIT/OPEN) */
    public String getValor() {
        return this.valorEntrada;
    }

    /** Nome descritivo do step para log */
    public String getNomeStep() {
        if (this.descricaoGherkin != null && !this.descricaoGherkin.isBlank())
            return this.descricaoGherkin;
        if (this.acao != null && this.nomeLogicoElemento != null)
            return this.acao + " " + this.nomeLogicoElemento;
        return this.acao;
    }

    /**
     * Tipo de localizador derivado do Elemento cadastrado.
     * O SeleniumWorker resolve o localizador via nomeLogicoElemento.
     * Retorna null quando não houver elemento associado (ex: WAIT, OPEN).
     */
    public String getTipoLocalizador() {
        // Sem elemento vinculado direto no step — o worker usa nomeLogicoElemento
        // para buscar no ElementoRepository. Retornamos null aqui para indicar
        // que o worker deve consultar o repositório.
        return null;
    }

    /**
     * Valor do localizador CSS/XPATH/ID (derivado via ElementoRepository no worker).
     * Retorna null aqui; o SeleniumWorker faz a resolução real.
     */
    public String getValorLocalizador() {
        return null;
    }
}
