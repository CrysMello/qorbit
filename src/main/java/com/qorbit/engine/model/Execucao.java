package com.qorbit.engine.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.qorbit.engine.auth.model.QorbitUser;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Table(name = "execucoes")
@Data
@NoArgsConstructor
public class Execucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_alvo", length = 500)
    private String urlAlvo;

    @Column(name = "browser")
    private String browser;

    @Column(name = "metodo_auth")
    private String metodoAuth;

    @Column(name = "status")
    private String status;

    @Column(name = "total_steps")
    private Integer totalSteps;

    @Column(name = "steps_passou")
    private Integer stepsPAssou;

    @Column(name = "steps_falhou")
    private Integer stepsFalhou;

    @Column(name = "percentual_sucesso")
    private Double percentualSucesso;

    @Column(name = "tempo_execucao_segundos")
    private Long tempoExecucaoSegundos;

    @Column(name = "caminho_relatorio", length = 500)
    private String caminhoRelatorio;

    @Column(name = "erro", length = 1000)
    private String erro;

    @OneToMany(mappedBy = "execucao", cascade = CascadeType.ALL,
               fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonManagedReference
    private List<Evidencia> evidencias;

    @Column(name = "iniciado_em")
    private String iniciadoEm;

    @Column(name = "finalizado_em")
    private String finalizadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private QorbitUser usuario;

    @PrePersist
    public void prePersist() {
        this.iniciadoEm = java.time.LocalDateTime.now().toString();
        if (this.status == null)      this.status      = "AGUARDANDO";
        if (this.stepsPAssou == null) this.stepsPAssou = 0;
        if (this.stepsFalhou == null) this.stepsFalhou = 0;
    }
}
