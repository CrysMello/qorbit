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
                <p class="empty-title">Nenhum caso de teste ainda</p>
                <p class="empty-sub">Crie o primeiro caso de teste para começar a automatizar.</p>
                <button class="btn btn-primary" onclick="abrirFormNovo()">+ Novo caso de teste</button>
            </div>`;
        return;
    }
    container.innerHTML = lista.map(c => `
        <div class="card" style="margin-bottom:10px">
            <div class="card-header">
                <div>
                    <span style="font-weight:bold;color:#1F4E79">${c.codigo || ('CT-' + c.id)}</span>
                    <span style="font-size:13px;margin-left:8px">${c.nome}</span>
                    <span class="badge ${c.status === 'ATIVO' ? 'badge-success' : 'badge-warning'}" style="margin-left:8px">${c.status}</span>
                </div>
                <div style="display:flex;gap:6px">
                    <a href="/nova-execucao" class="btn btn-sm btn-success">▶ Executar</a>
                    <button class="btn btn-sm" onclick="abrirFormEditar(${c.id})">✎ Editar</button>
                    <button class="btn btn-sm" style="color:#DC2626" onclick="deletarCaso(${c.id})">✕</button>
                </div>
            </div>
            <div style="display:flex;gap:16px;font-size:11px;color:#6B7280;margin-top:4px">
                ${c.modulo ? `<span>📁 ${c.modulo}</span>` : ''}
                <span>📋 ${c.steps?.length || 0} steps</span>
                ${c.urlAlvo ? `<span>🔗 ${c.urlAlvo}</span>` : ''}
            </div>
            ${c.steps?.length ? `
            <div style="margin-top:10px;display:flex;flex-direction:column;gap:3px">
                ${c.steps.map(s => `
                <div style="display:flex;align-items:center;gap:8px;font-size:11px;padding:4px 8px;background:#F9FAFB;border-radius:4px">
                    <span style="width:20px;height:20px;border-radius:50%;background:#1F4E79;color:white;display:flex;align-items:center;justify-content:center;font-size:9px;font-weight:bold;flex-shrink:0">${s.numeroStep}</span>
                    <span style="color:#6B7280;min-width:70px">${s.acao}</span>
                    <span style="color:#1F4E79;font-weight:bold">${s.nomeLogicoElemento || ''}</span>
                    ${s.valorEntrada ? `<span style="color:#9CA3AF">→ "${s.valorEntrada}"</span>` : ''}
                    ${s.descricaoGherkin ? `<span style="color:#9CA3AF;font-style:italic;margin-left:auto">${s.descricaoGherkin}</span>` : ''}
                </div>`).join('')}
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
