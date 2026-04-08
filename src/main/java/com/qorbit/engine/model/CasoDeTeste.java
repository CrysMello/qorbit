package com.qorbit.engine.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "casos_de_teste")
@Data
@NoArgsConstructor
public class CasoDeTeste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", unique = true)
    private String codigo;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "modulo")
    private String modulo;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "url_alvo", length = 500)
    private String urlAlvo;

    @Column(name = "status")
    private String status;

    @OneToMany(mappedBy = "casoDeTeste", cascade = CascadeType.ALL,
            fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonManagedReference
    private List<StepTeste> steps = new ArrayList<>();

    @Column(name = "criado_em")
    private String criadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now().toString();
        if (this.status == null) this.status = "ATIVO";
        if (this.steps == null) this.steps = new ArrayList<>();
    }
}