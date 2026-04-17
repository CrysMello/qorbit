// nova-execucao.js

let casosSelecionados = new Set();
let todosCasos = [];
let authMetodoAtual = 'cookie';
let cookiesCapturados = false;

async function carregarCasos() {
    try {
        todosCasos = await api('GET', '/api/casos');

        if (!todosCasos.length) {
            document.getElementById('listaCasos').innerHTML =
                `<p class="text-center text-muted" style="padding:20px">Nenhum caso de teste cadastrado ainda.<br><a href="/casos" style="color:#1F4E79">Crie ou grave um caso de teste primeiro</a></p>`;
            atualizarWizard();
            return;
        }

        renderizarCasos(todosCasos);
    } catch (e) {
        document.getElementById('listaCasos').innerHTML =
            '<p class="text-center text-muted" style="padding:20px">Erro ao carregar casos de teste.</p>';
        atualizarWizard();
    }
}

function renderizarCasos(lista) {
    const container = document.getElementById('listaCasos');

    if (!lista.length) {
        container.innerHTML = '<p class="text-center text-muted" style="padding:20px">Nenhum caso encontrado</p>';
        atualizarResumo();
        atualizarWizard();
        return;
    }

    container.innerHTML = lista.map(c => `
        <div class="test-item ${casosSelecionados.has(c.id) ? 'selected' : ''}"
             onclick="toggleCaso(${c.id}, this)" id="caso-${c.id}">
            <div class="test-check ${casosSelecionados.has(c.id) ? 'checked' : ''}" id="check-${c.id}">
                ${casosSelecionados.has(c.id) ? '✓' : ''}
            </div>
            <div style="flex:1">
                <div class="test-info-code">
                    ${c.codigo || ('CT-' + c.id)} · ${c.nome}
                </div>
                <div class="test-info-meta">${c.steps?.length || 0} steps · ${c.modulo || 'Geral'}</div>
            </div>
        </div>
    `).join('');

    atualizarResumo();
    atualizarWizard();
}

function toggleCaso(id, el) {
    if (casosSelecionados.has(id)) {
        casosSelecionados.delete(id);
        el.classList.remove('selected');
        const check = document.getElementById('check-' + id);
        if (check) {
            check.classList.remove('checked');
            check.textContent = '';
        }
    } else {
        casosSelecionados.add(id);
        el.classList.add('selected');
        const check = document.getElementById('check-' + id);
        if (check) {
            check.classList.add('checked');
            check.textContent = '✓';
        }
    }

    atualizarResumo();
    atualizarWizard();
}

function selecionarTodos() {
    todosCasos.forEach(c => casosSelecionados.add(c.id));
    renderizarCasos(todosCasos);
}

function filtrarCasos() {
    const busca = document.getElementById('filtroCasos')?.value.toLowerCase() || '';
    const filtrados = todosCasos.filter(c =>
        c.nome.toLowerCase().includes(busca) ||
        (c.modulo || '').toLowerCase().includes(busca)
    );
    renderizarCasos(filtrados);
}

function atualizarResumo() {
    const sel = casosSelecionados.size;
    const totalSteps = todosCasos
        .filter(c => casosSelecionados.has(c.id))
        .reduce((acc, c) => acc + (c.steps?.length || 0), 0);

    const resumo = document.getElementById('resumoSelecao');
    const tempo = document.getElementById('tempoEstimado');

    if (resumo) {
        resumo.textContent = `${sel} selecionado${sel !== 1 ? 's' : ''} · ${totalSteps} steps`;
    }

    if (tempo) {
        const minutos = Math.ceil(totalSteps * 0.3);
        tempo.textContent = totalSteps > 0 ? `~${minutos}min estimado` : '';
    }
}

function trocarAba(aba, btn) {
    authMetodoAtual = aba;

    ['cookie', 'senha', 'token'].forEach(a => {
        document.getElementById('aba-' + a)?.classList.add('hidden');
    });

    document.getElementById('aba-' + aba)?.classList.remove('hidden');
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');

    atualizarWizard();
}

function capturarCookies() {
    const badge = document.getElementById('cookiesBadge');
    const div = document.getElementById('cookiesCapturados');

    cookiesCapturados = true;

    if (badge && div) {
        badge.textContent = '3 cookies capturados (sessão ativa)';
        div.classList.remove('hidden');
        toast('Cookies capturados com sucesso!');
    }

    atualizarWizard();
}

function getAuthConfigurada() {
    if (authMetodoAtual === 'cookie') {
        return cookiesCapturados;
    }

    if (authMetodoAtual === 'senha') {
        const usuario = document.getElementById('authUsuario')?.value.trim();
        const senha = document.getElementById('authSenha')?.value.trim();
        return !!usuario && !!senha;
    }

    if (authMetodoAtual === 'token') {
        const token = document.getElementById('authToken')?.value.trim();
        return !!token;
    }

    return false;
}

function limparWizard() {
    ['wiz1', 'wiz2', 'wiz3', 'wiz4'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.classList.remove('done', 'active');
    });
}

function atualizarWizard() {
    limparWizard();

    const url = document.getElementById('urlAlvo')?.value.trim();
    const authOk = getAuthConfigurada();
    const testesOk = casosSelecionados.size > 0;

    const wiz1 = document.getElementById('wiz1');
    const wiz2 = document.getElementById('wiz2');
    const wiz3 = document.getElementById('wiz3');
    const wiz4 = document.getElementById('wiz4');

    if (!url) {
        wiz1?.classList.add('active');
        return;
    }

    wiz1?.classList.add('done');

    if (!authOk) {
        wiz2?.classList.add('active');
        return;
    }

    wiz2?.classList.add('done');

    if (!testesOk) {
        wiz3?.classList.add('active');
        return;
    }

    wiz3?.classList.add('done');
    wiz4?.classList.add('active');
}

async function iniciarExecucao() {
    const url = document.getElementById('urlAlvo')?.value.trim();
    const browser = document.getElementById('browser')?.value || 'chrome';

    if (!url) {
        toast('Informe a URL da aplicação alvo', 'danger');
        atualizarWizard();
        return;
    }

    if (!getAuthConfigurada()) {
        toast('Configure a autenticação antes de continuar', 'danger');
        atualizarWizard();
        return;
    }

    if (!casosSelecionados.size) {
        toast('Selecione ao menos um caso de teste', 'danger');
        atualizarWizard();
        return;
    }

    try {
        const res = await api('POST', '/api/execucoes/iniciar', {
            url,
            browser,
            metodoAuth: authMetodoAtual.toUpperCase(),
            idsCasos: Array.from(casosSelecionados)
        });

        toast('Execução iniciada! Redirecionando...');

        setTimeout(() => {
            window.location.href = '/?execucaoId=' + res.execucaoId;
        }, 1000);

    } catch (e) {
        toast('Erro ao iniciar: ' + e.message, 'danger');
    }
}

function registrarEventosWizard() {
    document.getElementById('urlAlvo')?.addEventListener('input', atualizarWizard);
    document.getElementById('authUsuario')?.addEventListener('input', atualizarWizard);
    document.getElementById('authSenha')?.addEventListener('input', atualizarWizard);
    document.getElementById('authToken')?.addEventListener('input', atualizarWizard);
}

// Início
registrarEventosWizard();
carregarCasos();
checkSeleniumStatus();
atualizarWizard();

// Checa se há execucaoId na URL para mostrar execução em andamento
const params = new URLSearchParams(window.location.search);
if (params.get('execucaoId')) {
    mostrarExecucaoEmAndamento(params.get('execucaoId'));
}