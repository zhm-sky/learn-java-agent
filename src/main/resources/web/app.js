(function () {
  const messagesEl = document.getElementById('messages');
  const inputEl = document.getElementById('input');
  const sendBtn = document.getElementById('send');
  const welcomeEl = document.getElementById('welcome');

  function addMsg(role, content, isError) {
    if (welcomeEl) welcomeEl.remove();
    const div = document.createElement('div');
    div.className = 'msg ' + role + (isError ? ' error' : '');
    const bubble = document.createElement('div');
    bubble.className = 'msg-bubble';
    bubble.textContent = content || '(无回复)';
    div.appendChild(bubble);
    messagesEl.appendChild(div);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return div;
  }

  function addLoading() {
    if (welcomeEl) welcomeEl.remove();
    const div = document.createElement('div');
    div.className = 'msg assistant loading';
    const bubble = document.createElement('div');
    bubble.className = 'msg-bubble';
    bubble.innerHTML = '<span class="typing"><span></span><span></span><span></span></span>';
    div.appendChild(bubble);
    messagesEl.appendChild(div);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return div;
  }

  function removeLoading(el) {
    if (el && el.parentNode) el.remove();
  }

  async function send() {
    const text = inputEl.value.trim();
    if (!text) return;

    addMsg('user', text);
    inputEl.value = '';
    inputEl.style.height = 'auto';
    sendBtn.disabled = true;

    const loadingEl = addLoading();

    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text })
      });
      const data = await res.json();
      removeLoading(loadingEl);

      if (res.ok) {
        addMsg('assistant', data.reply || '(无回复)');
      } else {
        addMsg('assistant', '错误: ' + (data.error || res.status), true);
      }
    } catch (err) {
      removeLoading(loadingEl);
      addMsg('assistant', '请求失败: ' + err.message, true);
    }

    sendBtn.disabled = false;
  }

  sendBtn.addEventListener('click', send);

  inputEl.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  });

  inputEl.addEventListener('input', function () {
    this.style.height = 'auto';
    this.style.height = Math.min(this.scrollHeight, 160) + 'px';
  });
})();
