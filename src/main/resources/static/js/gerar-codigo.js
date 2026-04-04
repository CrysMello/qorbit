// gerar-codigo.js
let todosCasos = [];
let selecionados = new Set();

async function carregarCasos() {
    try {
        todosCasos = await api('GET', '/api/casos');
        renderizarCasos(todosCasos);
    } catch (e) { toast('Erro: ' + e.message, 'danger'); }
}

function renderizarCasos(lista) {
    const container = document.getElementById('listaCasos');
    if (!lista.length) {
        container.innerHTML = '<p class="text-muted text-center" style="padding:20px">Nenhum caso de teste.<br><a href="/casos">Crie casos de teste primeiro</a></p>';
        return;
    }
    container.innerHTML = lista.map(c => `
        <div class="test-item ${selecionados.has(c.id) ? 'selected' : ''}"
             onclick="toggleCaso(${c.id}, this)" id="gc-${c.id}">
            <div class="test-check ${selecionados.has(c.id) ? 'checked' : ''}" id="ck-${c.id}">
                ${selecionados.has(c.id) ? '✓' : ''}
            </div>
            <div style="flex:1">
                <div style="font-size:12px;font-weight:bold;color:${selecionados.has(c.id) ? '#1F4E79' : '#374151'}">
                    ${c.codigo || 'CT-' + c.id} · ${c.nome}
                </div>
                <div class="text-muted text-sm">${c.steps?.length || 0} steps · ${c.modulo || 'Geral'}</div>
            </div>
        </div>
    `).join('');
    atualizarResumo();
}

function filtrarCasos(busca) {
    const f = todosCasos.filter(c => c.nome.toLowerCase().includes(busca.toLowerCase()));
    renderizarCasos(f);
}

function toggleCaso(id, el) {
    if (selecionados.has(id)) {
        selecionados.delete(id);
        el.classList.remove('selected');
        const ck = document.getElementById('ck-' + id);
        if (ck) { ck.classList.remove('checked'); ck.textContent = ''; }
    } else {
        selecionados.add(id);
        el.classList.add('selected');
        const ck = document.getElementById('ck-' + id);
        if (ck) { ck.classList.add('checked'); ck.textContent = '✓'; }
    }
    atualizarResumo();
}

function selecionarTodos() {
    todosCasos.forEach(c => selecionados.add(c.id));
    renderizarCasos(todosCasos);
}

function atualizarResumo() {
    document.getElementById('resumo').textContent =
        `${selecionados.size} caso${selecionados.size !== 1 ? 's' : ''} selecionado${selecionados.size !== 1 ? 's' : ''}`;
}

async function gerarDownload() {
    if (!selecionados.size) { toast('Selecione ao menos um caso de teste', 'danger'); return; }

    toast('Gerando projeto... aguarde', 'info');
    try {
        const res = await fetch('/api/codigo/gerar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ idsCasos: Array.from(selecionados) })
        });

        if (!res.ok) {
            const err = await res.json();
            toast(err.erro || 'Erro ao gerar', 'danger');
            return;
        }

        const blob = await res.blob();
        const url  = URL.createObjectURL(blob);
        const a    = document.createElement('a');
        a.href     = url;
        a.download = 'scanner-tests-export.zip';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        toast('Download iniciado!');
    } catch (e) { toast('Erro: ' + e.message, 'danger'); }
}

// Inicia
carregarCasos();
checkSeleniumStatus();
