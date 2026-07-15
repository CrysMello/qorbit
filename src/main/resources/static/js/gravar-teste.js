// gravar-teste.js
let stepsLocais  = [];
let wsGravacao   = null;
let gravando     = false;
let pollingTimer = null;
let tokenAtual   = null;

// ── Controles principais ────────────────────────────────────────────────────
// A captura roda via extensão de navegador instalada uma vez (browser-extension/).
// A página só avisa a extensão (via CustomEvent, repassado pelo content-bridge.js
// dela) qual token/URL usar; quem escuta cliques/preenchimentos na aba aberta e
// envia pro backend é o content-capture.js da extensão.

function extensaoInstalada() {
    return !!window.__qorbitExtensaoPresente;
}

function atualizarBadgeExtensao() {
    const badge = document.getElementById('badgeExtensao');
    if (!badge) return;
    if (extensaoInstalada()) {
        badge.textContent = '✓ Extensão detectada';
        badge.className = 'badge badge-success';
    } else {
        badge.textContent = '⚠ Extensão não instalada';
        badge.className = 'badge badge-danger';
    }
}

async function iniciarGravacao() {
    const url = document.getElementById('urlGravacao')?.value.trim();
    if (!url) { toast('Informe a URL da aplicação alvo', 'danger'); return; }

    if (!extensaoInstalada()) {
        toast('Instale a extensão do Qorbit antes de gravar (veja as instruções abaixo).', 'danger');
        document.getElementById('avisoExtensao')?.classList.remove('hidden');
        return;
    }

    // Abre a aba já aqui, de forma síncrona: se esperarmos a resposta do backend
    // (await) antes de chamar window.open, o navegador não reconhece mais isso
    // como originado de um clique direto do usuário e bloqueia o pop-up em silêncio.
    // Não usamos 'noopener' aqui porque precisamos manter a referência (abaAlvo)
    // para navegar essa aba assim que soubermos a URL validada pelo backend.
    const abaAlvo = window.open('about:blank', '_blank');
    if (abaAlvo) { try { abaAlvo.opener = null; } catch (e) {} }

    // Atualiza UI imediatamente
    gravando = true;
    stepsLocais = [];
    mostrarPainelGravando();
    renderizarSteps();

    try {
        const res = await api('POST', '/api/gravacao/iniciar', { url });
        if (res && res.erro) {
            gravando = false;
            mostrarPainelIniciar();
            toast(res.erro, 'danger');
            abaAlvo?.close();
            return;
        }
        tokenAtual = res.token;
        const eventoUrl = window.location.origin + '/captura-publica/evento';
        window.dispatchEvent(new CustomEvent('qorbit-iniciar-gravacao', { detail: { token: tokenAtual, eventoUrl } }));

        if (abaAlvo) {
            abaAlvo.location.href = url;
        } else {
            toast('O navegador bloqueou a nova aba. Abra manualmente: ' + url, 'danger');
        }
        iniciarPolling();
        conectarWsGravacao();
        carregarModulos();
        toast('Gravando — a extensão já está capturando na aba aberta.');
    } catch (e) {
        gravando = false;
        mostrarPainelIniciar();
        abaAlvo?.close();
        toast('Erro ao iniciar: ' + e.message, 'danger');
    }
}

async function pararGravacao() {
    const nomeCaso = document.getElementById('nomeCaso')?.value.trim();
    const modulo   = document.getElementById('moduloCaso')?.value.trim();

    if (!nomeCaso) { toast('Informe o nome do caso de teste', 'danger'); return; }

    // Consulta o backend — fonte de verdade dos steps
    try {
        const status = await api('GET', '/api/gravacao/status');
        const totalBackend = status.totalSteps || 0;

        if (totalBackend === 0 && stepsLocais.length === 0) {
            toast('Nenhum step gravado — interaja com a aba aberta antes de parar', 'danger');
            return;
        }
    } catch {}

    setBtnParar('Salvando...', true);

    try {
        pararPolling();
        const res = await api('POST', '/api/gravacao/parar', { nomeCaso, modulo });

        if (res && res.erro) {
            toast(res.erro, 'danger');
            setBtnParar('⏹ Parar e salvar', false);
            iniciarPolling(); // Reinicia polling se falhou
            return;
        }

        gravando = false;
        mostrarPainelIniciar();
        stepsLocais = [];
        renderizarSteps();
        atualizarContador();
        document.getElementById('cardGherkin')?.classList.add('hidden');
        toast('✓ ' + (res.totalSteps || 0) + ' steps salvos! Caso "' + nomeCaso + '" criado.');

        setTimeout(() => {
            if (confirm('Caso de teste criado!\nDeseja abrir a tela de Casos de Teste?'))
                window.location.href = '/casos';
        }, 600);
    } catch (e) {
        toast('Erro ao salvar: ' + e.message, 'danger');
        setBtnParar('⏹ Parar e salvar', false);
    }
}

async function descartarGravacao() {
    if (!confirm('Descartar todos os steps gravados?')) return;
    pararPolling();
    try { await api('POST', '/api/gravacao/descartar', {}); } catch {}
    gravando = false;
    stepsLocais = [];
    renderizarSteps();
    atualizarContador();
    document.getElementById('cardGherkin')?.classList.add('hidden');
    mostrarPainelIniciar();
    toast('Gravação descartada');
}

async function removerStep(numero) {
    try {
        await api('DELETE', '/api/gravacao/step/' + numero);
        stepsLocais = stepsLocais.filter(s => s.numeroStep !== numero);
        stepsLocais.forEach((s, i) => s.numeroStep = i + 1);
        renderizarSteps();
        atualizarContador();
        atualizarGherkin();
    } catch (e) { toast('Erro ao remover: ' + e.message, 'danger'); }
}

// ── Polling ─────────────────────────────────────────────────────────────────

function iniciarPolling() {
    pararPolling();
    pollingTimer = setInterval(async () => {
        if (!gravando) { pararPolling(); return; }
        try {
            const status = await api('GET', '/api/gravacao/status');
            const serverSteps = status.steps || [];
            sincronizarStepsComBackend(serverSteps);

            // Atualiza contador mesmo sem novos steps
            atualizarContador();
        } catch {}
    }, 800);
}

function pararPolling() {
    if (pollingTimer) { clearInterval(pollingTimer); pollingTimer = null; }
}

// ── WebSocket ────────────────────────────────────────────────────────────────

function conectarWsGravacao() {
    // Garante SockJS e STOMP carregados via CDN
    function tentar() {
        if (typeof SockJS === 'undefined') {
            const s1 = document.createElement('script');
            s1.src = 'https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js';
            s1.onload = () => setTimeout(tentar, 200);
            document.head.appendChild(s1);
            return;
        }
        if (typeof Stomp === 'undefined') {
            const s2 = document.createElement('script');
            s2.src = 'https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js';
            s2.onload = () => setTimeout(tentar, 200);
            document.head.appendChild(s2);
            return;
        }
        try {
            const socket = new SockJS('/ws');
            const client = Stomp.over(socket);
            client.debug = null;
            client.connect({}, () => {
                client.subscribe('/topic/gravacao', (msg) => {
                    adicionarStepNaTela(JSON.parse(msg.body));
                });
            });
            wsGravacao = client;
        } catch {}
    }
    tentar();
}

// ── Renderização ─────────────────────────────────────────────────────────────

function adicionarStepNaTela(step) {
    if (stepsLocais.find(s => s.numeroStep === step.numeroStep)) return;
    stepsLocais.push(step);
    renderizarSteps();
    atualizarContador();
    atualizarGherkin();
}

function sincronizarStepsComBackend(serverSteps) {
    stepsLocais = serverSteps.map(s => ({
        numeroStep: s.numero,
        acao:       s.acao,
        nomeLogico: s.elemento,
        valor:      s.valor,
        gherkin:    s.gherkin
    }));
    renderizarSteps();
    atualizarGherkin();
}

function renderizarSteps() {
    const lista = document.getElementById('listaSteps');
    if (!lista) return;

    if (!stepsLocais.length) {
        lista.innerHTML = '<div style="text-align:center;padding:40px;color:#9CA3AF;font-size:12px">'
            + (gravando
                ? '● Aguardando interações...<br><br>Clique em algo no Chrome que foi aberto.'
                : 'Os steps aparecerão aqui conforme você interage.')
            + '</div>';
        return;
    }

    lista.innerHTML = stepsLocais.map(s => `
        <div class="step-live" id="step-rec-${s.numeroStep}">
            <div class="step-num-rec">${s.numeroStep}</div>
            <div class="step-info">
                <div>
                    <span class="step-acao-badge acao-${s.acao}">${s.acao || '—'}</span>
                    <span style="font-size:12px;font-weight:bold;color:#1F4E79">${s.nomeLogico || ''}</span>
                    ${s.valor ? `<span style="font-size:11px;color:#9CA3AF"> → "${s.valor}"</span>` : ''}
                </div>
                <div class="gherkin-text">${s.gherkin || ''}</div>
            </div>
            ${gravando ? `<button class="step-delete" onclick="removerStep(${s.numeroStep})" title="Remover">✕</button>` : ''}
        </div>
    `).join('');

    lista.lastElementChild?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function atualizarContador() {
    const total = Math.max(stepsLocais.length, 0);
    const el = document.getElementById('contadorSteps');
    if (el) el.textContent = total + ' step' + (total !== 1 ? 's' : '');
}

function atualizarGherkin() {
    const card = document.getElementById('cardGherkin');
    const pre  = document.getElementById('previewGherkin');
    if (!stepsLocais.length) { card?.classList.add('hidden'); return; }
    card?.classList.remove('hidden');

    const nomeCaso = document.getElementById('nomeCaso')?.value || 'Cenario Gravado';
    let gherkin = `# language: pt\n\nFuncionalidade: ${nomeCaso}\n\n  Cenário: ${nomeCaso}\n`;
    stepsLocais.forEach((s, i) => {
        const palavra = i === 0 ? '    Dado' : (s.acao === 'VALIDAR' ? '    Então' : '    E');
        gherkin += palavra + ' ' + (s.gherkin || '') + '\n';
    });
    if (pre) pre.textContent = gherkin;
}

function copiarGherkin() {
    const texto = document.getElementById('previewGherkin')?.textContent || '';
    navigator.clipboard.writeText(texto).then(() => toast('Gherkin copiado!'));
}

// ── Painéis ───────────────────────────────────────────────────────────────────

function mostrarPainelGravando() {
    document.getElementById('formIniciar')?.classList.add('hidden');
    document.getElementById('painelGravando')?.classList.remove('hidden');
    atualizarStatusBar(true);
}

function mostrarPainelIniciar() {
    document.getElementById('formIniciar')?.classList.remove('hidden');
    document.getElementById('painelGravando')?.classList.add('hidden');
    if (tokenAtual) window.dispatchEvent(new CustomEvent('qorbit-parar-gravacao'));
    tokenAtual = null;
    setBtnIniciar('⏺ Iniciar gravação', false);
    const n = document.getElementById('nomeCaso');
    const m = document.getElementById('moduloCaso');
    if (n) n.value = '';
    if (m) m.value = '';
    atualizarStatusBar(false);
}

function atualizarStatusBar(ativo) {
    const bar   = document.getElementById('statusBar');
    const texto = document.getElementById('statusTexto');
    const badge = document.getElementById('badgeStatus');
    if (ativo) {
        if (bar)   bar.className = 'status-bar gravando-ativo';
        if (texto) { texto.textContent = 'Gravando — interaja com a aba aberta'; texto.style.color = '#DC2626'; }
        if (badge) { badge.textContent = '⏺ Gravando'; badge.className = 'badge badge-danger'; }
    } else {
        if (bar)   bar.className = 'status-bar inativo';
        if (texto) { texto.textContent = 'Nenhuma gravação em andamento'; texto.style.color = '#6B7280'; }
        if (badge) { badge.textContent = '● Inativo'; badge.className = 'badge badge-gray'; }
    }
}

function setBtnIniciar(texto, desabilitado) {
    const btn = document.querySelector('.record-btn.start');
    if (btn) { btn.textContent = texto; btn.disabled = desabilitado; }
}

function setBtnParar(texto, desabilitado) {
    const btn = document.querySelector('.record-btn.stop');
    if (btn) { btn.textContent = texto; btn.disabled = desabilitado; }
}

async function carregarModulos() {
    try {
        const mods = await api('GET', '/api/casos/modulos');
        const dl = document.getElementById('listaModulos');
        if (dl) {
            dl.innerHTML = '';
            mods.forEach(m => { const o = document.createElement('option'); o.value = m; dl.appendChild(o); });
        }
    } catch {}
}

document.getElementById('nomeCaso')?.addEventListener('input', atualizarGherkin);

// ── Inicialização ─────────────────────────────────────────────────────────────

async function inicializar() {
    mostrarPainelIniciar();

    try {
        const status = await api('GET', '/api/gravacao/status');
        if (status.gravando) {
            gravando = true;
            stepsLocais = (status.steps || []).map(s => ({
                numeroStep: s.numero,
                acao:       s.acao,
                nomeLogico: s.elemento,
                valor:      s.valor,
                gherkin:    s.gherkin
            }));
            renderizarSteps();
            atualizarContador();
            atualizarGherkin();
            mostrarPainelGravando();
            if (status.token) tokenAtual = status.token;
            iniciarPolling();
            conectarWsGravacao();
        }
    } catch {}

    atualizarBadgeExtensao();
    checkSeleniumStatus();
}

inicializar();
