// elementos.js — Biblioteca de Elementos (POM)

let elementos = [];
let editandoId = null;

async function carregarElementos() {
    try {
        elementos = await api('GET', '/api/elementos');
        renderizarTabela(elementos);
        atualizarTotal(elementos.length);
        carregarPaginas();
    } catch (e) {
        toast('Erro ao carregar elementos: ' + e.message, 'danger');
    }
}

function renderizarTabela(lista) {
    const tbody = document.getElementById('tbodyElementos');
    if (!lista.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted" style="padding:30px">Nenhum elemento encontrado</td></tr>';
        return;
    }

    tbody.innerHTML = lista.map(el => `
        <tr>
            <td>
                <span id="nomeLogico-${el.id}" style="font-weight:bold;color:#1F4E79">${el.nomeLogico}</span>
                <span class="badge ${el.status === 'ATIVO' ? 'badge-success' : 'badge-warning'}" style="margin-left:6px">
                    ${el.status === 'ATIVO' ? 'Ativo' : 'Pendente'}
                </span>
            </td>
            <td>${el.pagina}</td>
            <td>${el.tipoSeletor || 'CSS'}</td>
            <td style="font-family:monospace;font-size:11px;color:#9CA3AF;max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${el.seletorTecnico}">
                ${el.seletorTecnico}
            </td>
            <td>
                <span class="badge ${el.status === 'ATIVO' ? 'badge-success' : 'badge-warning'}">
                    ${el.status === 'ATIVO' ? 'Ativo' : 'Pendente'}
                </span>
            </td>
            <td>
                <button class="btn btn-sm" onclick="abrirFormEditar(${el.id})">✎ Editar</button>
                <button class="btn btn-sm" style="color:#DC2626;margin-left:4px" onclick="deletar(${el.id})">✕</button>
            </td>
        </tr>
    `).join('');
}

function atualizarTotal(n) {
    const label = document.getElementById('totalLabel');
    if (label) label.textContent = `${n} elementos · ${new Set(elementos.map(e => e.pagina)).size} páginas`;
}

async function carregarPaginas() {
    try {
        const paginas = await api('GET', '/api/elementos/paginas');
        const sel = document.getElementById('filtroPagina');
        const dl  = document.getElementById('listaPaginas');
        paginas.forEach(p => {
            const opt = document.createElement('option');
            opt.value = p; opt.textContent = p;
            sel?.appendChild(opt.cloneNode(true));
            dl?.appendChild(opt);
        });
    } catch {}
}

function filtrar() {
    const busca  = document.getElementById('filtroBusca')?.value.toLowerCase() || '';
    const pagina = document.getElementById('filtroPagina')?.value || '';
    const status = document.getElementById('filtroStatus')?.value || '';

    const filtrados = elementos.filter(el => {
        const matchBusca  = !busca  || el.nomeLogico.toLowerCase().includes(busca) || el.seletorTecnico.toLowerCase().includes(busca);
        const matchPagina = !pagina || el.pagina === pagina;
        const matchStatus = !status || el.status === status;
        return matchBusca && matchPagina && matchStatus;
    });

    renderizarTabela(filtrados);
}

function limparFiltros() {
    document.getElementById('filtroBusca').value  = '';
    document.getElementById('filtroPagina').value = '';
    document.getElementById('filtroStatus').value = '';
    renderizarTabela(elementos);
}

function abrirFormAdicionar() {
    editandoId = null;
    document.getElementById('formTitulo').textContent = 'Adicionar elemento manualmente (Canal 2)';
    ['fNomeLogico','fPagina','fSeletor','fDescricao'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    document.getElementById('fTipoSeletor').value = 'CSS';
    document.getElementById('formAdicionar').classList.remove('hidden');
    document.getElementById('fNomeLogico').focus();
}

function abrirFormEditar(id) {
    const el = elementos.find(e => e.id === id);
    if (!el) return;
    editandoId = id;
    document.getElementById('formTitulo').textContent = 'Editar elemento';
    document.getElementById('fNomeLogico').value   = el.nomeLogico;
    document.getElementById('fPagina').value        = el.pagina;
    document.getElementById('fTipoSeletor').value   = el.tipoSeletor || 'CSS';
    document.getElementById('fSeletor').value        = el.seletorTecnico;
    document.getElementById('fDescricao').value      = el.descricao || '';
    document.getElementById('formAdicionar').classList.remove('hidden');
    document.getElementById('fNomeLogico').focus();
}

function fecharForm() {
    document.getElementById('formAdicionar').classList.add('hidden');
    editandoId = null;
}

async function salvarElemento() {
    const nomeLogico    = document.getElementById('fNomeLogico').value.trim();
    const pagina        = document.getElementById('fPagina').value.trim();
    const tipoSeletor   = document.getElementById('fTipoSeletor').value;
    const seletorTecnico= document.getElementById('fSeletor').value.trim();
    const descricao     = document.getElementById('fDescricao').value.trim();

    if (!nomeLogico || !pagina || !seletorTecnico) {
        toast('Preencha todos os campos obrigatórios', 'danger'); return;
    }

    const dados = { nomeLogico, pagina, tipoSeletor, seletorTecnico, descricao };

    try {
        if (editandoId) {
            await api('PUT', '/api/elementos/' + editandoId, dados);
            toast('Elemento atualizado com sucesso');
        } else {
            await api('POST', '/api/elementos', dados);
            toast('Elemento adicionado com sucesso');
        }
        fecharForm();
        carregarElementos();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

async function deletar(id) {
    if (!confirm('Remover este elemento da biblioteca?')) return;
    try {
        await api('DELETE', '/api/elementos/' + id);
        toast('Elemento removido');
        carregarElementos();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

async function capturarElementos() {
    const url = document.getElementById('urlCaptura')?.value.trim();
    if (!url) { toast('Informe a URL', 'danger'); return; }

    toast('Capturando elementos... aguarde', 'info');
    try {
        const res = await api('POST', '/api/elementos/capturar', { url });
        toast(res.mensagem);
        carregarElementos();
        document.getElementById('formCaptura').classList.add('hidden');
    } catch (e) {
        toast('Falha na captura: ' + e.message, 'danger');
    }
}

// Inicia
carregarElementos();
checkSeleniumStatus();
