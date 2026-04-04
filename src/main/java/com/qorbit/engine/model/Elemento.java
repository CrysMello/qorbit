package com.qorbit.engine.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "elementos")
@Data
@NoArgsConstructor
public class Elemento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_logico", nullable = false)
    private String nomeLogico;

    @Column(name = "pagina", nullable = false)
    private String pagina;

    @Column(name = "tipo_seletor")
    private String tipoSeletor; // CSS, XPATH, ID, NAME, LINK_TEXT

    @Column(name = "seletor_tecnico", nullable = false, length = 500)
    private String seletorTecnico;

    @Column(name = "descricao", length = 200)
    private String descricao;

    @Column(name = "status")
    private String status; // ATIVO, PENDENTE

    @Column(name = "criado_em")
    private String criadoEm;

    @Column(name = "atualizado_em")
    private String atualizadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = java.time.LocalDateTime.now().toString();
        this.atualizadoEm = java.time.LocalDateTime.now().toString();
        if (this.status == null) this.status = "PENDENTE";
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = java.time.LocalDateTime.now().toString();
    }
}
