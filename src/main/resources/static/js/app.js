// ── Utilitários ────────────────────────────────────────────────────────────

async function api(method, url, body) {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);
    if (!res.ok) {
        const err = await res.json().catch(() => ({ erro: 'Erro desconhecido' }));
        throw new Error(err.erro || 'Erro ' + res.status);
    }
    return res.json().catch(() => null);
}

function toast(msg, tipo = 'success') {
    const t = document.createElement('div');
    t.className = 'toast toast-' + tipo;
    t.textContent = msg;
    Object.assign(t.style, {
        position: 'fixed', bottom: '20px', right: '20px', zIndex: 9999,
        padding: '10px 16px', borderRadius: '6px', fontSize: '12px',
        background: tipo === 'success' ? '#DCFCE7' : (tipo === 'danger' ? '#FEE2E2' : '#DBEAFE'),
        color: tipo === 'success' ? '#166534' : (tipo === 'danger' ? '#DC2626' : '#1E40AF'),
        border: '1px solid', borderColor: tipo === 'success' ? '#86EFAC' : (tipo === 'danger' ? '#FECACA' : '#93C5FD'),
        boxShadow: '0 2px 8px rgba(0,0,0,.12)'
    });
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 3000);
}

// ── Status do Selenium ──────────────────────────────────────────────────────

async function checkSeleniumStatus() {
    try {
        const res = await fetch('/api/selenium/status');
        const data = await res.json();
        const dot   = document.getElementById('seleniumStatus');
        const label = document.getElementById('seleniumLabel');
        const badge = document.getElementById('seleniumBadge');

        if (data.ativo) {
            dot?.classList.remove('offline');
            dot?.classList.add('online');
            if (label) label.textContent = 'Selenium ativo';
            if (badge) { badge.textContent = '● Selenium conectado'; badge.className = 'badge badge-success'; }
        } else {
            dot?.classList.remove('online');
            dot?.classList.add('offline');
            if (label) label.textContent = 'Selenium inativo';
            if (badge) { badge.textContent = '● Selenium desconectado'; badge.className = 'badge badge-danger'; }
        }
    } catch {
        const label = document.getElementById('seleniumLabel');
        if (label) label.textContent = 'Sem conexão';
    }
}

// ── WebSocket (atualizações em tempo real) ──────────────────────────────────

let stompClient = null;
let execucaoAtiva = null;
let wsConectado = false;
let wsConectando = false;
let wsFilaAssinaturas = [];
let pollingExecucao = null;
let ultimoStepRenderizado = null;
let ultimoEventoFinal = null;

function initWebSocket() {
    if (window.SockJS && window.Stomp) {
        conectarWS();
        return;
    }

    const carregarStomp = () => {
        if (window.Stomp) {
            conectarWS();
            return;
        }
        const s2 = document.createElement('script');
        s2.src = 'https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js';
        s2.onload = conectarWS;
        document.head.appendChild(s2);
    };

    if (window.SockJS) {
        carregarStomp();
        return;
    }

    const s1 = document.createElement('script');
    s1.src = 'https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js';
    s1.onload = carregarStomp;
    document.head.appendChild(s1);
}

function conectarWS() {
    if (wsConectado || wsConectando) return;

    try {
        wsConectando = true;
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;
        stompClient.connect({}, () => {
            wsConectando = false;
            wsConectado = true;
            console.log('WebSocket conectado');
            flushAssinaturasPendentes();
        }, (erro) => {
            wsConectando = false;
            wsConectado = false;
            console.warn('Falha ao conectar WebSocket:', erro);
        });
    } catch (e) {
        wsConectando = false;
        wsConectado = false;
        console.warn('WebSocket não disponível:', e);
    }
}

function flushAssinaturasPendentes() {
    const pendentes = [...wsFilaAssinaturas];
    wsFilaAssinaturas = [];
    pendentes.forEach(({ execucaoId, callback }) => assinarExecucao(execucaoId, callback));
}

function assinarExecucao(execucaoId, callback) {
    if (!stompClient || !wsConectado) {
        wsFilaAssinaturas.push({ execucaoId, callback });
        if (!wsConectando) conectarWS();
        return;
    }

    if (execucaoAtiva) {
        try { execucaoAtiva.unsubscribe(); } catch (_) {}
    }

    execucaoAtiva = stompClient.subscribe('/topic/execucao/' + execucaoId, msg => {
        const data = JSON.parse(msg.body);
        callback(data);
    });
}

// ── Dashboard: execução em andamento ───────────────────────────────────────

function mostrarExecucaoEmAndamento(execucaoId) {
    const card = document.getElementById('execucaoCard');
    if (card) card.style.display = 'block';

    ultimoStepRenderizado = null;
    ultimoEventoFinal = null;

    // Persiste execucaoId para sobreviver à navegação entre páginas
    localStorage.setItem('qorbit_execucaoId', execucaoId);
    mostrarBarraGlobal();

    consultarStatusExecucao(execucaoId, false);
    iniciarPollingExecucao(execucaoId);
    refreshExecucoesViews();

    assinarExecucao(execucaoId, (data) => {
        aplicarEventoExecucao(data);
        refreshExecucoesViews();
    });
}

function mostrarBarraGlobal() {
    const bar = document.getElementById('globalExecBar');
    if (bar) bar.style.display = 'block';
}

function ocultarBarraGlobal() {
    const bar = document.getElementById('globalExecBar');
    if (bar) bar.style.display = 'none';
    localStorage.removeItem('qorbit_execucaoId');
}

function atualizarBarraGlobal(pct, passou, falhou, concluidos, status) {
    const progress = document.getElementById('globalExecProgress');
    const label    = document.getElementById('globalExecLabel');
    const badge    = document.getElementById('globalExecStatus');
    if (progress) progress.style.width = Math.max(0, Math.min(100, pct || 0)) + '%';
    if (label) label.textContent = `${concluidos || 0} steps — ${passou || 0} passou · ${falhou || 0} falhou`;
    if (badge) {
        if (status === 'CONCLUIDO') {
            badge.textContent = '✓ Concluído';
            badge.style.color = 'var(--green)';
            if (progress) progress.style.background = 'var(--green)';
        } else if (status === 'ERRO') {
            badge.textContent = '✗ Erro';
            badge.style.color = 'var(--red)';
            if (progress) progress.style.background = 'var(--red)';
        } else {
            badge.textContent = '● Rodando';
            badge.style.color = 'var(--blue-light)';
            if (progress) progress.style.background = 'var(--blue)';
        }
    }
}

// Retoma acompanhamento de execução ativa ao navegar para outra página
async function retormarExecucaoAtiva() {
    const execucaoId = localStorage.getItem('qorbit_execucaoId');
    if (!execucaoId) return;

    try {
        const data = await fetch('/api/execucoes/' + execucaoId).then(r => r.json());
        const status = (data.status || '').toUpperCase();

        if (status === 'RODANDO' || status === 'PENDENTE') {
            mostrarBarraGlobal();
            const pct = data.percentualSucesso ?? (data.totalSteps > 0 ? Math.round((data.stepsConcluidos / data.totalSteps) * 100) : 0);
            atualizarBarraGlobal(pct, data.stepsPAssou, data.stepsFalhou, data.stepsConcluidos, status);
            iniciarPollingExecucaoGlobal(execucaoId);
        } else {
            // Execução já terminou — limpa
            ocultarBarraGlobal();
        }
    } catch (_) {
        ocultarBarraGlobal();
    }
}

function iniciarPollingExecucaoGlobal(execucaoId) {
    if (pollingExecucao) clearInterval(pollingExecucao);
    pollingExecucao = setInterval(async () => {
        try {
            const data = await fetch('/api/execucoes/' + execucaoId).then(r => r.json());
            const status = (data.status || '').toUpperCase();
            const pct = data.percentualSucesso ?? (data.totalSteps > 0 ? Math.round((data.stepsConcluidos / data.totalSteps) * 100) : 0);
            atualizarBarraGlobal(pct, data.stepsPAssou, data.stepsFalhou, data.stepsConcluidos, status);
            if (status !== 'RODANDO' && status !== 'PENDENTE') {
                clearInterval(pollingExecucao);
                pollingExecucao = null;
                setTimeout(() => ocultarBarraGlobal(), 2500);
            }
        } catch (_) {}
    }, 2000);
}

function iniciarPollingExecucao(execucaoId) {
    if (pollingExecucao) clearInterval(pollingExecucao);
    pollingExecucao = setInterval(() => consultarStatusExecucao(execucaoId, true), 2000);
}

async function consultarStatusExecucao(execucaoId, silencioso = false) {
    try {
        const data = await fetch('/api/execucoes/' + execucaoId).then(r => r.json());
        aplicarSnapshotExecucao(data);
    } catch (e) {
        if (!silencioso) {
            console.warn('Não foi possível consultar execução:', e);
        }
    }
}

function aplicarSnapshotExecucao(data) {
    if (!data) return;

    const status = (data.status || '').toUpperCase();
    const totalSteps = data.totalSteps || 0;
    const passou = data.stepsPAssou || 0;
    const falhou = data.stepsFalhou || 0;
    const concluidos = passou + falhou;
    const pct = totalSteps > 0 ? Math.round((concluidos / totalSteps) * 100) : 0;

    atualizarCabecalhoExecucao(status, pct, passou, falhou, concluidos);

    if (status === 'CONCLUIDO' || status === 'ERRO') {
        finalizarAcompanhamento(status, data.erro || data.detalhe || null);
    }
}

function aplicarEventoExecucao(data) {
    if (!data) return;

    const { evento, step, totalSteps, passou, falhou, nomeStep } = data;
    const status = (evento || '').toUpperCase();
    const concluidos = (passou || 0) + (falhou || 0);
    const pct = totalSteps ? Math.round((concluidos / totalSteps) * 100) : 0;

    if (status === 'INICIADO') {
        atualizarCabecalhoExecucao('RODANDO', 0, passou || 0, falhou || 0, concluidos);
        return;
    }

    if (status === 'CONCLUIDO' || status === 'ERRO') {
        atualizarCabecalhoExecucao(status, status === 'CONCLUIDO' ? 100 : pct, passou || 0, falhou || 0, concluidos);
        finalizarAcompanhamento(status, data.detalhe || null);
        return;
    }

    if (step != null) {
        atualizarCabecalhoExecucao('RODANDO', pct, passou || 0, falhou || 0, concluidos);
        atualizarStepNaLista(step, nomeStep, status, data.detalhe);
    }
}

function atualizarCabecalhoExecucao(status, pct, passou, falhou, concluidos) {
    const badge = document.getElementById('execStatus');
    const progressFill = document.getElementById('progressFill');
    const progressLabel = document.getElementById('progressLabel');

    if (badge) {
        if (status === 'CONCLUIDO') {
            badge.textContent = '✓ Concluído';
            badge.className = 'badge badge-success';
        } else if (status === 'ERRO') {
            badge.textContent = '✗ Erro';
            badge.className = 'badge badge-danger';
        } else {
            badge.textContent = '● Rodando';
            badge.className = 'badge badge-info';
        }
    }

    if (progressFill) {
        progressFill.style.width = Math.max(0, Math.min(100, pct || 0)) + '%';
    }

    if (progressLabel) {
        progressLabel.textContent = `Executados ${concluidos || 0} steps — ${passou || 0} passou · ${falhou || 0} falhou`;
    }

    atualizarBarraGlobal(pct, passou, falhou, concluidos, status);
}

function finalizarAcompanhamento(status, detalhe) {
    if (pollingExecucao) {
        clearInterval(pollingExecucao);
        pollingExecucao = null;
    }

    if (ultimoEventoFinal === status) return;
    ultimoEventoFinal = status;

    refreshExecucoesViews();

    if (status === 'CONCLUIDO') {
        toast('Execução concluída!');
    } else if (status === 'ERRO') {
        toast('Erro na execução: ' + (detalhe || 'erro não informado'), 'danger');
    }

    limparExecucaoDaUrl();
    setTimeout(() => {
        refreshExecucoesViews();
        ocultarCardExecucao();
        ocultarBarraGlobal();
        location.reload();
    }, 1800);
}

function atualizarStepNaLista(num, nome, status, detalhe) {
    const lista = document.getElementById('stepsList');
    if (!lista) return;

    if (ultimoStepRenderizado === num) {
        const itemAtual = lista.querySelector(`.step-item[data-step="${num}"]`);
        if (itemAtual) {
            aplicarStatusVisualStep(itemAtual, status, detalhe);
        }
        return;
    }

    if (ultimoStepRenderizado != null) {
        const anterior = lista.querySelector(`.step-item[data-step="${ultimoStepRenderizado}"]`);
        if (anterior) aplicarStatusVisualStep(anterior, status, detalhe);
    }

    const item = document.createElement('div');
    item.className = 'step-item executando';
    item.dataset.step = num;

    const numDiv = document.createElement('div');
    numDiv.className = 'step-num azul';
    numDiv.textContent = num;

    const label = document.createElement('span');
    label.className = 'step-label';
    label.textContent = nome || 'Step ' + num;

    const statusSpan = document.createElement('span');
    statusSpan.className = 'step-status text-info';
    statusSpan.textContent = '● executando';

    item.appendChild(numDiv);
    item.appendChild(label);
    item.appendChild(statusSpan);
    lista.appendChild(item);
    item.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    ultimoStepRenderizado = num;
    aplicarStatusVisualStep(item, status, detalhe);
}

function aplicarStatusVisualStep(item, status, detalhe) {
    if (!item) return;

    item.classList.remove('executando', 'passou', 'falhou');

    const numEl = item.querySelector('.step-num');
    const statusEl = item.querySelector('.step-status');

    if (status === 'PASSOU') {
        item.classList.add('passou');
        if (numEl) numEl.className = 'step-num verde';
        if (statusEl) statusEl.textContent = '✓ ok';
    } else if (status === 'FALHOU') {
        item.classList.add('falhou');
        if (numEl) numEl.className = 'step-num vermelho';
        if (statusEl) statusEl.textContent = '✗ falhou' + (detalhe ? ' — ' + detalhe : '');
    } else {
        item.classList.add('executando');
        if (numEl) numEl.className = 'step-num azul';
        if (statusEl) statusEl.textContent = '● executando';
    }
}


// ── Atualização das tabelas de execuções ───────────────────────────────────

async function refreshExecucoesViews() {
    try {
        const execucoes = await fetch('/api/execucoes').then(r => r.json());
        atualizarTabelasExecucoes(execucoes || []);
        atualizarResumoDashboard(execucoes || []);
    } catch (e) {
        console.warn('Falha ao atualizar lista de execuções:', e);
    }
}

function atualizarTabelasExecucoes(execucoes) {
    document.querySelectorAll('tr[data-execucao-id]').forEach(tr => {
        const id = Number(tr.dataset.execucaoId);
        const exec = execucoes.find(e => Number(e.id) === id);
        if (!exec) return;

        const cells = tr.querySelectorAll('[data-col]');
        cells.forEach(cell => {
            const col = cell.dataset.col;
            if (col === 'status') {
                cell.textContent = exec.status || '—';
                cell.className = 'badge ' + badgeClass(exec.status);
            } else if (col === 'passou') {
                cell.textContent = exec.stepsPAssou ?? 0;
            } else if (col === 'falhou') {
                cell.textContent = exec.stepsFalhou ?? 0;
            } else if (col === 'duracao') {
                cell.textContent = exec.tempoExecucaoSegundos != null ? (exec.tempoExecucaoSegundos + 's') : '—';
            } else if (col === 'percentualSucesso') {
                cell.textContent = exec.percentualSucesso != null ? (Math.round(exec.percentualSucesso) + '%') : '—';
            }
        });
    });
}

function atualizarResumoDashboard(execucoes) {
    if (!document.body) return;
    const ultima = execucoes && execucoes.length ? execucoes[0] : null;
    const ultimaExecEl = document.getElementById('metricUltimaExecucao');
    const ultimaPctEl = document.getElementById('metricPercentual');

    if (ultimaExecEl) {
        ultimaExecEl.textContent = ultima ? `${ultima.stepsPAssou ?? 0}/${ultima.totalSteps ?? 0}` : '—';
    }
    if (ultimaPctEl) {
        ultimaPctEl.textContent = ultima && ultima.percentualSucesso != null ? `${Math.round(ultima.percentualSucesso)}%` : '—';
    }
}

function badgeClass(status) {
    const s = (status || '').toUpperCase();
    if (s === 'CONCLUIDO') return 'badge-success';
    if (s === 'ERRO') return 'badge-danger';
    if (s === 'RODANDO') return 'badge-info';
    return 'badge-warning';
}

function limparExecucaoDaUrl() {
    try {
        const url = new URL(window.location.href);
        if (!url.searchParams.has('execucaoId')) return;
        url.searchParams.delete('execucaoId');
        window.history.replaceState({}, '', url.pathname + (url.search ? url.search : ''));
    } catch (_) {}
}

function ocultarCardExecucao() {
    const card = document.getElementById('execucaoCard');
    if (card) card.style.display = 'none';
}

// Ao carregar qualquer página, retoma exibição se houver execução ativa
document.addEventListener('DOMContentLoaded', () => retormarExecucaoAtiva());
