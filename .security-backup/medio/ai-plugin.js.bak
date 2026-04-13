// ai-plugin.js — Plugin IA

let contadores = { renomeados: 0, abas: 0, falhas: 0 };

document.addEventListener('DOMContentLoaded', () => {
    carregarStatus();
});

function carregarStatus() {
    fetch('/api/ai/status')
        .then(r => r.json())
        .then(data => {
            if (data.habilitado && data.endpoint) {
                mostrarTelaAtivo(data.endpoint, data.modelo);
            }
        })
        .catch(() => {});
}

function validarFormulario() {
    const ep = document.getElementById('inputEndpoint').value.trim();
    const mod = document.getElementById('inputModelo').value.trim();
    document.getElementById('btnAtivar').disabled = !(ep && mod);
    document.getElementById('btnTestarForm').disabled = !(ep && mod);
}

function trocarAuth() {
    const tipo = document.getElementById('inputAuthTipo').value;
    document.getElementById('groupApiKey').style.display = tipo === 'bearer' ? 'block' : 'none';
}

function testarConexao() {
    const msg = document.getElementById('msgTeste');
    if (msg) { msg.style.display = 'block'; msg.style.background = '#FEF3C7'; msg.style.color = '#D97706'; msg.textContent = 'A testar ligação...'; }

    const ep = document.getElementById('inputEndpoint') ? document.getElementById('inputEndpoint').value : '';
    const mod = document.getElementById('inputModelo') ? document.getElementById('inputModelo').value : '';
    const auth = document.getElementById('inputAuthTipo') ? document.getElementById('inputAuthTipo').value : 'bearer';
    const key = document.getElementById('inputApiKey') ? document.getElementById('inputApiKey').value : '';

    if (ep) {
        fetch('/api/ai/configurar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ endpoint: ep, modelo: mod, authTipo: auth, apiKey: key })
        }).then(() => {
            fetch('/api/ai/testar')
                .then(r => r.json())
                .then(data => {
                    if (msg) {
                        if (data.ok) {
                            msg.style.background = '#DCFCE7'; msg.style.color = '#166534';
                            msg.textContent = 'Ligação OK — ' + data.mensagem;
                        } else {
                            msg.style.background = '#FEE2E2'; msg.style.color = '#DC2626';
                            msg.textContent = data.erro || 'Falha na ligação.';
                        }
                    }
                })
                .catch(() => { if (msg) { msg.style.background = '#FEE2E2'; msg.style.color = '#DC2626'; msg.textContent = 'Erro ao testar.'; } });
        });
    } else {
        fetch('/api/ai/testar')
            .then(r => r.json())
            .then(data => {
                addLog(data.ok ? 'ok' : 'err', data.ok ? 'Ligação OK' : (data.erro || 'Falha'));
            });
    }
}

function ativarPlugin() {
    const ep = document.getElementById('inputEndpoint').value.trim();
    const mod = document.getElementById('inputModelo').value.trim();
    const auth = document.getElementById('inputAuthTipo').value;
    const key = document.getElementById('inputApiKey') ? document.getElementById('inputApiKey').value : '';

    fetch('/api/ai/configurar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ endpoint: ep, modelo: mod, authTipo: auth, apiKey: key })
    })
    .then(r => r.json())
    .then(() => mostrarTelaAtivo(ep, mod))
    .catch(() => alert('Erro ao guardar configuração.'));
}

function mostrarTelaAtivo(ep, mod) {
    document.getElementById('screen-config').style.display = 'none';
    document.getElementById('screen-ativo').style.display = 'block';
    document.getElementById('lblModelo').textContent = mod || '—';
    document.getElementById('lblEndpoint').textContent = ep.length > 40 ? ep.substring(0, 40) + '...' : ep;
    document.getElementById('topSub').textContent = 'Activo · ' + (mod || '');
    document.getElementById('topBadge').textContent = '● Activo';
    document.getElementById('topBadge').className = 'badge badge-success';
    document.getElementById('btnTestar').style.display = 'inline-block';
}

function reconfigurar() {
    document.getElementById('screen-config').style.display = 'block';
    document.getElementById('screen-ativo').style.display = 'none';
    document.getElementById('topSub').textContent = 'Configure a IA para começar';
    document.getElementById('topBadge').textContent = '● Não configurado';
    document.getElementById('topBadge').className = 'badge badge-warning';
    document.getElementById('btnTestar').style.display = 'none';
}

function melhorarNomes() {
    addLog('info', 'A melhorar nomes de elementos...');
    fetch('/api/ai/melhorar-nomes', { method: 'POST' })
        .then(r => r.json())
        .then(data => {
            if (data.erro) { addLog('err', data.erro); return; }
            contadores.renomeados += data.renomeados || 0;
            document.getElementById('mRenomeados').textContent = contadores.renomeados;
            addLog('ok', data.renomeados + ' elementos renomeados de ' + data.total);
            (data.log || []).forEach(l => addLog('info', l));
        })
        .catch(() => addLog('err', 'Erro ao chamar a IA.'));
}

function detectarAbas() {
    const url = document.getElementById('inputUrlAbas').value.trim();
    if (!url) { addLog('warn', 'Preenche o URL da página primeiro.'); return; }
    addLog('info', 'A detectar abas em: ' + url);
    fetch('/api/ai/detectar-abas', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ html: '', url: url })
    })
    .then(r => r.json())
    .then(data => {
        if (data.erro) { addLog('err', data.erro); return; }
        const total = data.total || 0;
        contadores.abas += total;
        document.getElementById('mAbas').textContent = contadores.abas;
        addLog('ok', total + ' componentes detectados');
        const abas = data.abas;
        if (Array.isArray(abas)) {
            abas.forEach(a => addLog('info', '[' + a.tipo + '] ' + a.descricao + ' → ' + a.seletor));
        }
    })
    .catch(() => addLog('err', 'Erro ao detectar abas.'));
}

function diagnosticar() {
    const seletor = document.getElementById('inputSeletor').value.trim();
    const erro = document.getElementById('inputErro').value.trim();
    if (!seletor || !erro) { addLog('warn', 'Preenche o seletor e o erro.'); return; }
    addLog('info', 'A diagnosticar falha...');
    fetch('/api/ai/diagnosticar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ seletor, erro, pagina: '' })
    })
    .then(r => r.json())
    .then(data => {
        if (data.erro) { addLog('err', data.erro); return; }
        contadores.falhas++;
        document.getElementById('mFalhas').textContent = contadores.falhas;
        addLog('warn', 'Diagnóstico: ' + data.diagnostico);
    })
    .catch(() => addLog('err', 'Erro ao diagnosticar.'));
}

function limparLog() {
    document.getElementById('logBox').innerHTML = '<span style="color:#9CA3AF">Log limpo.</span>';
}

function addLog(tipo, msg) {
    const box = document.getElementById('logBox');
    if (!box) return;
    const t = new Date().toLocaleTimeString('pt-BR');
    const cls = tipo === 'ok' ? 'log-ok' : tipo === 'warn' ? 'log-warn' : tipo === 'err' ? 'log-err' : 'log-info';
    box.innerHTML += '<br><span class="' + cls + '">[' + t + '] ' + msg + '</span>';
    box.scrollTop = box.scrollHeight;
}
