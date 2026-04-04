package com.qorbit.engine.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "evidencias")
@Data
@NoArgsConstructor
public class Evidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execucao_id")
    @JsonBackReference
    private Execucao execucao;

    @Column(name = "numero_step")
    private Integer numeroStep;

    @Column(name = "nome_step", length = 300)
    private String nomeStep;

    @Column(name = "status_step")
    private String statusStep;

    @Column(name = "caminho_arquivo", length = 500)
    private String caminhoArquivo;

    @Column(name = "nome_arquivo", length = 300)
    private String nomeArquivo;

    @Column(name = "motivo_falha", length = 500)
    private String motivoFalha;

    @Column(name = "capturado_em")
    private String capturadoEm;

    @PrePersist
    public void prePersist() {
        this.capturadoEm = java.time.LocalDateTime.now().toString();
    }
}
