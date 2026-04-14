package com.qorbit.engine.model;

import com.qorbit.engine.auth.model.QorbitUser;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SuiteTeste — Agrupa casos de teste validados num repositório permanente.
 *
 * Ao contrário dos casos de teste (temporários), as suites persistem
 * no banco mesmo após reiniciar a aplicação.
 *
 * Tabelas criadas automaticamente pelo Hibernate:
 *   suite_teste          — dados da suite
 *   suite_teste_casos    — relacionamento suite ↔ casos
 */
@Entity
@Table(name = "suite_teste")
@Data
@NoArgsConstructor
public class SuiteTeste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "cor")
    private String cor; // green | blue | amber | purple | coral

    @Column(name = "criado_em")
    private String criadoEm;

    @Column(name = "ultima_execucao")
    private String ultimaExecucao;

    @Column(name = "ultimo_resultado")
    private String ultimoResultado; // ex: "5/5 passou" | "3/5 passou"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private QorbitUser usuario;

    // ManyToMany — uma suite tem vários casos, um caso pode estar em várias suites
    // Usa EAGER para carregar os casos junto com a suite
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "suite_teste_casos",
        joinColumns = @JoinColumn(name = "suite_id"),
        inverseJoinColumns = @JoinColumn(name = "caso_id")
    )
    private List<CasoDeTeste> casos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now().toString();
        if (this.cor == null) this.cor = "green";
        if (this.casos == null) this.casos = new ArrayList<>();
    }
}
