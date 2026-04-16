// casos.js
let todosCasos = [];
let editandoId = null;
let steps = [];
let elementosDisponiveis = [];

async function carregarCasos() {
    try {
        todosCasos = await api('GET', '/api/casos');
        renderizarCasos(todosCasos);
        document.getElementById('totalLabel').textContent =
            `${todosCasos.length} caso${todosCasos.length !== 1 ? 's' : ''} de teste`;
        carregarModulos();
        carregarElementosDisponiveis();
    } catch (e) { toast('Erro ao carregar casos: ' + e.message, 'danger'); }
}

function renderizarCasos(lista) {
    const container = document.getElementById('listaCasos');
    if (!lista.length) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-title">Nenhum caso de teste</div>
                <div class="empty-state-text">Comece criando o seu primeiro teste automatizado</div>
                <button class="btn btn-primary btn-sm" onclick="abrirFormNovo()">+ Novo caso de teste</button>
            </div>`;
        return;
    }
    container.innerHTML = lista.map(c => `
        <div class="caso-card">
            <div class="caso-header">
                <div class="caso-info">
                    <span class="caso-id">${c.codigo || 'CT-' + c.id}</span>
                    <span class="caso-nome">${c.nome}</span>
                    <span class="badge-status ${c.status !== 'ATIVO' ? 'inativo' : ''}">${c.status}</span>
                </div>
                <div class="caso-actions">
                    <button class="btn-executar" onclick="window.location.href='/nova-execucao'">▶ Executar</button>
                    <button class="btn-editar" onclick="abrirFormEditar(${c.id})">✎ Editar</button>
                    <button class="btn-deletar" onclick="deletarCaso(${c.id})">✕</button>
                </div>
            </div>
            <div class="caso-metadata">
                ${c.modulo ? `<div class="metadata-item"><span>📁</span><span>${c.modulo}</span></div>` : ''}
                <div class="metadata-item"><span>📋</span><span>${c.steps?.length || 0} steps</span></div>
                ${c.urlAlvo ? `<div class="metadata-item"><span>🔗</span><a href="${c.urlAlvo}" target="_blank" style="color:inherit;text-decoration:underline">${c.urlAlvo.replace('https://','').substring(0,40)}</a></div>` : ''}
            </div>
            ${c.steps?.length ? `
            <div class="steps-list">
                ${c.steps.map(s => `
                    <div class="step-item">
                        <div class="step-number">${s.numeroStep}</div>
                        <div class="step-acao-badge">${s.acao}</div>
                        <div class="step-elemento-badge">${s.nomeLogicoElemento || '—'}</div>
                        <div class="step-descricao">${s.descricaoGherkin || '—'}</div>
                    </div>
                `).join('')}
            </div>` : ''}
        </div>
    `).join('');
}

async function carregarModulos() {
    try {
        const mods = await api('GET', '/api/casos/modulos');
        const sel = document.getElementById('filtroModulo');
        const dl  = document.getElementById('listaModulos');
        mods.forEach(m => {
            const o = document.createElement('option');
            o.value = m; o.textContent = m;
            sel?.appendChild(o.cloneNode(true));
            dl?.appendChild(o);
        });
    } catch {}
}

async function carregarElementosDisponiveis() {
    try {
        elementosDisponiveis = await api('GET', '/api/elementos');
        const dl = document.getElementById('listaElementos');
        if (dl) {
            dl.innerHTML = '';
            elementosDisponiveis.forEach(e => {
                const o = document.createElement('option');
                o.value = e.nomeLogico;
                dl.appendChild(o);
            });
        }
    } catch {}
}

function filtrar() {
    const busca  = document.getElementById('filtroBusca')?.value.toLowerCase() || '';
    const modulo = document.getElementById('filtroModulo')?.value || '';
    const filtrados = todosCasos.filter(c =>
        (!busca  || c.nome.toLowerCase().includes(busca)) &&
        (!modulo || c.modulo === modulo)
    );
    renderizarCasos(filtrados);
}

function limparFiltros() {
    document.getElementById('filtroBusca').value  = '';
    document.getElementById('filtroModulo').value = '';
    renderizarCasos(todosCasos);
}

function abrirFormNovo() {
    editandoId = null; steps = [];
    document.getElementById('formTitulo').textContent = 'Novo caso de teste';
    ['fNome','fModulo','fUrl','fDescricao'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    document.getElementById('containerSteps').innerHTML =
        '<p class="text-muted text-sm text-center" style="padding:16px" id="semSteps">Nenhum step ainda. Clique em "+ Adicionar step" para começar.</p>';
    document.getElementById('formCaso').classList.remove('hidden');
    document.getElementById('fNome').focus();
    document.getElementById('formCaso').scrollIntoView({ behavior: 'smooth' });
}

function abrirFormEditar(id) {
    const caso = todosCasos.find(c => c.id === id);
    if (!caso) return;
    editandoId = id;
    steps = caso.steps ? [...caso.steps] : [];
    document.getElementById('formTitulo').textContent = 'Editar: ' + caso.nome;
    document.getElementById('fNome').value      = caso.nome || '';
    document.getElementById('fModulo').value    = caso.modulo || '';
    document.getElementById('fUrl').value       = caso.urlAlvo || '';
    document.getElementById('fDescricao').value = caso.descricao || '';
    renderizarSteps();
    document.getElementById('formCaso').classList.remove('hidden');
    document.getElementById('formCaso').scrollIntoView({ behavior: 'smooth' });
}

function fecharForm() {
    document.getElementById('formCaso').classList.add('hidden');
    editandoId = null; steps = [];
}

function adicionarStep() {
    steps.push({ acao: 'CLICAR', nomeLogicoElemento: '', valorEntrada: '', descricaoGherkin: '' });
    renderizarSteps();
}

function removerStep(btn) {
    const row = btn.closest('.step-form-row');
    const idx = parseInt(row.dataset.index);
    steps.splice(idx, 1);
    renderizarSteps();
}

function renderizarSteps() {
    const container = document.getElementById('containerSteps');
    const tmpl = document.getElementById('stepTemplate');

    if (!steps.length) {
        container.innerHTML = '<p class="text-muted text-sm text-center" style="padding:16px">Nenhum step ainda.</p>';
        return;
    }

    container.innerHTML = '';
    steps.forEach((s, i) => {
        const clone = tmpl.content.cloneNode(true);
        const row   = clone.querySelector('.step-form-row');
        row.dataset.index = i;

        // Número do step
        const badge = row.querySelector('.step-num-badge');
        badge.textContent = i + 1;
        Object.assign(badge.style, {
            width:'24px', height:'24px', borderRadius:'50%', background:'#1F4E79',
            color:'white', display:'flex', alignItems:'center', justifyContent:'center',
            fontSize:'11px', fontWeight:'bold', flexShrink:'0'
        });

        row.querySelector('.step-acao').value    = s.acao || 'CLICAR';
        row.querySelector('.step-elemento').value = s.nomeLogicoElemento || '';
        row.querySelector('.step-valor').value    = s.valorEntrada || '';
        row.querySelector('.step-gherkin').value  = s.descricaoGherkin || '';

        Object.assign(row.style, {
            display:'flex', alignItems:'center', gap:'8px',
            marginBottom:'6px', padding:'6px 0'
        });

        container.appendChild(clone);
    });
}

function coletarSteps() {
    const rows = document.querySelectorAll('.step-form-row');
    return Array.from(rows).map((row, i) => ({
        numeroStep: i + 1,
        acao:               row.querySelector('.step-acao').value,
        nomeLogicoElemento: row.querySelector('.step-elemento').value.trim(),
        valorEntrada:       row.querySelector('.step-valor').value.trim(),
        descricaoGherkin:   row.querySelector('.step-gherkin').value.trim()
    }));
}

async function salvarCaso() {
    const nome = document.getElementById('fNome').value.trim();
    if (!nome) { toast('Nome é obrigatório', 'danger'); return; }

    const dados = {
        nome,
        modulo:    document.getElementById('fModulo').value.trim(),
        urlAlvo:   document.getElementById('fUrl').value.trim(),
        descricao: document.getElementById('fDescricao').value.trim(),
        steps:     coletarSteps()
    };

    try {
        if (editandoId) {
            await api('PUT', '/api/casos/' + editandoId, dados);
            toast('Caso atualizado com sucesso');
        } else {
            await api('POST', '/api/casos', dados);
            toast('Caso criado com sucesso');
        }
        fecharForm();
        carregarCasos();
    } catch (e) { toast(e.message, 'danger'); }
}

async function deletarCaso(id) {
    if (!confirm('Remover este caso de teste?')) return;
    try {
        await api('DELETE', '/api/casos/' + id);
        toast('Caso removido');
        carregarCasos();
    } catch (e) { toast(e.message, 'danger'); }
}

// Inicia
carregarCasos();
checkSeleniumStatus();
