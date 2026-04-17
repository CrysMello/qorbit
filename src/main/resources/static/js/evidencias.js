// evidencias.js
let execucaoAtualId = null;
let todasEvidencias = [];

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#x27;');
}

async function carregarExecucoes() {
    try {
        const execs = await api('GET', '/api/execucoes');
        const sel = document.getElementById('selectExecucao');
        execs.forEach(e => {
            const o = document.createElement('option');
            o.value = e.id;
            o.textContent = `#${e.id} — ${e.urlAlvo} (${e.status}) — ${e.iniciadoEm ? e.iniciadoEm.substring(0,10) : ''}`;
            sel.appendChild(o);
        });
        const params = new URLSearchParams(window.location.search);
        const idParam = params.get('execucaoId');
        if (idParam) { sel.value = idParam; carregarEvidencias(idParam); }
    } catch (e) { toast('Erro: ' + e.message, 'danger'); }
}

async function carregarEvidencias(execucaoId) {
    if (!execucaoId) return;
    execucaoAtualId = execucaoId;
    try {
        const exec = await api('GET', '/api/execucoes/' + execucaoId);
        const passou  = exec.stepsPAssou  || 0;
        const falhou  = exec.stepsFalhou  || 0;
        const total   = exec.totalSteps   || (passou + falhou);
        const pct     = total > 0 ? Math.round(passou * 100 / total) : 0;

        document.getElementById('subTitle').textContent = `Execução #${execucaoId} — ${exec.urlAlvo}`;
        document.getElementById('mTotal').textContent   = total;
        document.getElementById('mPassou').textContent  = passou;
        document.getElementById('mFalhou').textContent  = falhou;
        document.getElementById('mPct').textContent     = pct + '%';
        document.getElementById('resumoExecucao').style.display = 'grid';
        document.getElementById('btnDownload').style.display    = '';
        document.getElementById('btnCodigo').style.display      = '';

        todasEvidencias = await api('GET', '/api/evidencias/execucao/' + execucaoId);
        renderizarEvidencias(todasEvidencias);
    } catch (e) { toast('Erro ao carregar evidências: ' + e.message, 'danger'); }
}

/**
 * Monta URL da imagem a partir do nomeArquivo salvo no banco.
 * nomeArquivo = "execucao-5/step-1-ok-t1.png"
 * URL final   = /api/evidencias/imagem/5/step-1-ok-t1.png
 */
function urlImagem(nomeArquivo) {
    if (!nomeArquivo) return null;
    // extrai "execucao-5/nome.png" → id=5, file="nome.png"
    const match = nomeArquivo.match(/^execucao-(\d+)\/(.+)$/);
    if (match) {
        return `/api/evidencias/imagem/${match[1]}/${encodeURIComponent(match[2])}`;
    }
    // fallback: tenta usar direto
    return `/api/evidencias/imagem/${nomeArquivo}`;
}

function renderizarEvidencias(lista) {
    const grid = document.getElementById('thumbGrid');
    if (!lista || !lista.length) {
        grid.innerHTML = '<p class="text-muted text-center" style="padding:40px;grid-column:1/-1">Nenhuma evidência encontrada.</p>';
        return;
    }
    grid.innerHTML = lista
        .sort((a,b) => (a.numeroStep||0) - (b.numeroStep||0))
        .map(ev => {
            const imgUrl = urlImagem(ev.nomeArquivo);
            return `
            <div class="thumb ${ev.statusStep === 'FALHOU' ? 'falhou' : ''}"
                 onclick="abrirLightbox('${ev.nomeArquivo || ''}', '${(ev.nomeStep||'').replace(/'/g,"\\'")}')">
                <div class="thumb-img">
                    ${imgUrl
                        ? `<img src="${imgUrl}" alt="step-${ev.numeroStep}"
                               onerror="this.parentElement.innerHTML='<span style=font-size:11px;color:#9CA3AF>Sem imagem</span>'">`
                        : '<span style="font-size:11px;color:#9CA3AF">Sem screenshot</span>'}
                </div>
                <div class="thumb-info">
                    <div style="font-size:10px;font-weight:bold;color:${ev.statusStep === 'PASSOU' ? '#166534' : '#DC2626'}">
                        step-${ev.numeroStep} — ${ev.statusStep}
                    </div>
                    <div style="font-size:10px;color:#6B7280;margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                        ${escapeHtml(ev.nomeStep || '—')}
                    </div>
                    ${ev.motivoFalha ? `<div style="font-size:9px;color:#DC2626;margin-top:2px">${escapeHtml(ev.motivoFalha.substring(0,60))}...</div>` : ''}
                </div>
            </div>`;
        }).join('');
}

function filtrarStatus(status, btn) {
    document.querySelectorAll('.filter-tabs .btn').forEach(b => b.classList.remove('active-filter'));
    btn.classList.add('active-filter');
    const filtradas = status ? todasEvidencias.filter(e => e.statusStep === status) : todasEvidencias;
    renderizarEvidencias(filtradas);
}

function abrirLightbox(nomeArquivo, _titulo) {
    if (!nomeArquivo) return;
    const url = urlImagem(nomeArquivo);
    if (!url) return;
    document.getElementById('lightboxImg').src = url;
    document.getElementById('lightbox').classList.add('open');
}

function fecharLightbox() {
    document.getElementById('lightbox').classList.remove('open');
}

async function downloadEvidencias() {
    if (!execucaoAtualId) return;
    window.location.href = '/api/evidencias/download/' + execucaoAtualId;
}

async function gerarCodigo() {
    window.location.href = '/exportar-codigo';
}

carregarExecucoes();
checkSeleniumStatus();
