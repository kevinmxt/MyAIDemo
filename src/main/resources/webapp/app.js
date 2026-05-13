// DOM elements
const chatMessages = document.getElementById('chatMessages');
const chatInput = document.getElementById('chatInput');
const sendBtn = document.getElementById('sendBtn');
const ingestBtn = document.getElementById('ingestBtn');
const ingestDir = document.getElementById('ingestDir');
const ingestStatus = document.getElementById('ingestStatus');
const documentList = document.getElementById('documentList');
const storeStatus = document.getElementById('storeStatus');

let isWaiting = false;

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    refreshDocuments();
});

// Handle Enter key (send on Enter, newline on Shift+Enter)
function handleKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
}

// Auto-resize textarea
chatInput.addEventListener('input', () => {
    chatInput.style.height = 'auto';
    chatInput.style.height = Math.min(chatInput.scrollHeight, 120) + 'px';
});

async function sendMessage() {
    const query = chatInput.value.trim();
    if (!query || isWaiting) return;

    // Clear input
    chatInput.value = '';
    chatInput.style.height = 'auto';

    // Hide welcome message
    const welcome = document.querySelector('.welcome-message');
    if (welcome) welcome.remove();

    // Add user message
    addMessage('user', query);

    // Show loading
    setWaiting(true);
    const loadingEl = addLoadingDots();

    try {
        const response = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ query })
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '请求失败');
        }

        const data = await response.json();

        // Remove loading dots
        loadingEl.remove();

        // Add assistant message with sources
        addAssistantMessage(data.answer, data.sources);
    } catch (error) {
        loadingEl.remove();
        addMessage('assistant', '抱歉，处理你的问题时出错了：' + error.message);
    } finally {
        setWaiting(false);
    }
}

function addMessage(role, content) {
    const div = document.createElement('div');
    div.className = 'message ' + role;
    div.textContent = content;
    chatMessages.appendChild(div);
    scrollToBottom();
}

function addAssistantMessage(answer, sources) {
    const div = document.createElement('div');
    div.className = 'message assistant';

    // Simple markdown rendering
    let html = renderMarkdown(answer);

    // Add sources if available
    if (sources && sources.length > 0) {
        html += '<div class="sources">';
        html += '<div class="sources-label">📎 参考来源：</div>';
        sources.forEach(s => {
            const shortName = s.fileName.replace(/\\/g, '/').split('/').pop();
            html += '<div class="source-item">';
            html += '<span class="source-file">' + escapeHtml(shortName) + '</span>';
            html += '<span class="source-score">(相关度: ' + (s.score * 100).toFixed(0) + '%)</span>';
            html += '</div>';
        });
        html += '</div>';
    }

    div.innerHTML = html;
    chatMessages.appendChild(div);
    scrollToBottom();
}

function renderMarkdown(text) {
    let html = escapeHtml(text);

    // Code blocks (``` ... ```)
    html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>');

    // Inline code (`...`)
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Bold (**...**)
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

    // Italic (*...*)
    html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');

    // Newlines to <br>
    html = html.replace(/\n/g, '<br>');

    return html;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function addLoadingDots() {
    const div = document.createElement('div');
    div.className = 'loading-dots';
    div.innerHTML = '<span></span><span></span><span></span>';
    chatMessages.appendChild(div);
    scrollToBottom();
    return div;
}

function setWaiting(waiting) {
    isWaiting = waiting;
    sendBtn.disabled = waiting;
    chatInput.disabled = waiting;
}

async function ingestDocuments() {
    const directory = ingestDir.value.trim();
    if (!directory) {
        showIngestStatus('请输入文档目录路径', 'error');
        return;
    }

    ingestBtn.disabled = true;
    showIngestStatus('正在摄入文档...', 'loading');

    try {
        const response = await fetch('/api/ingest', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ directory })
        });

        const data = await response.json();

        if (response.ok && data.success) {
            showIngestStatus('成功！处理了 ' + data.filesProcessed + ' 个文件，创建了 ' + data.segmentsCreated + ' 个片段。', 'success');
            refreshDocuments();
        } else {
            showIngestStatus('失败：' + (data.error || data.message || '未知错误'), 'error');
        }
    } catch (error) {
        showIngestStatus('请求失败：' + error.message, 'error');
    } finally {
        ingestBtn.disabled = false;
    }
}

function showIngestStatus(message, type) {
    ingestStatus.textContent = message;
    ingestStatus.className = 'status-message ' + type;
}

async function refreshDocuments() {
    documentList.innerHTML = '<div class="empty-state">加载中...</div>';

    try {
        const response = await fetch('/api/documents');
        if (!response.ok) throw new Error('请求失败');

        const documents = await response.json();

        if (documents.length === 0) {
            documentList.innerHTML = '<div class="empty-state">暂无已索引文档</div>';
            storeStatus.textContent = '存储状态: 空';
        } else {
            let totalSegments = 0;
            let html = '';
            documents.forEach(doc => {
                totalSegments += doc.segmentCount;
                html += '<div class="doc-item">';
                html += '<div class="doc-name">' + escapeHtml(doc.fileName) + '</div>';
                html += '<div class="doc-meta">' + doc.segmentCount + ' 个片段</div>';
                if (doc.directory) {
                    html += '<div class="doc-meta">' + escapeHtml(doc.directory) + '</div>';
                }
                html += '</div>';
            });
            documentList.innerHTML = html;
            storeStatus.textContent = '存储状态: ' + documents.length + ' 个文档, ' + totalSegments + ' 个片段';
        }
    } catch (error) {
        documentList.innerHTML = '<div class="empty-state">加载失败：' + error.message + '</div>';
        storeStatus.textContent = '存储状态: 错误';
    }
}

function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}
