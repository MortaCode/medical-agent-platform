/**
 * 主控制器：串联「输入 -> 流式推理 -> 逐帧渲染 -> 侧栏指标」完整链路。
 *
 * <p>数据流：</p>
 * <pre>
 *   query ──> SseClient(POST /api/think, text/event-stream)
 *          ──> ThoughtParser.parseFrame  (metadata JSON -> 视图模型)
 *          ──> SessionStore.push         (按 seq 有序累积 + 派生指标)
 *          ──> ThoughtRender.buildCard   (生成阶段卡片)
 *          ──> renderSidebar             (置信度/引用/记忆/审计/事件流)
 * </pre>
 *
 * <p>降级策略：真实请求失败（后端未启动、网络不可达）时自动回落到演示模式，
 * 保证界面在任何环境下都可完整演示。</p>
 */
(function (global) {
  'use strict';

  var CFG = global.APP_CONFIG;
  var P = global.ThoughtParser;
  var R = global.ThoughtRender;
  var doc = global.document;

  var dom = {};
  var store = new global.SessionStore();
  var client = null;
  var currentQuery = '';
  var running = false;
  var demoHistoryTokens = 1860;   // 演示模式下模拟的历史会话 token 占用

  /* ==================== 初始化 ==================== */

  function cacheDom() {
    ['conn-dot', 'conn-text', 'session-id', 'run-meta', 'ask-form', 'query-input',
     'submit-btn', 'stop-btn', 'demo-toggle', 'question-box', 'stage-list',
     'final-box', 'final-conf', 'final-text', 'final-cite', 'notice-box',
     'stat-confidence', 'stat-threshold', 'stat-confidence-bar', 'stat-citations',
     'stat-evidence', 'stat-evidence-detail', 'stat-memory', 'stat-memory-detail',
     'stat-audit', 'event-log']
      .forEach(function (id) {
        dom[id] = doc.getElementById(id);
      });
  }

  function setConn(state, text) {
    dom['conn-dot'].className = 'conn-dot' + (state ? ' is-' + state : '');
    dom['conn-text'].textContent = text;
  }

  function showNotice(text, isError) {
    if (!text) {
      dom['notice-box'].hidden = true;
      return;
    }
    dom['notice-box'].textContent = text;
    dom['notice-box'].className = 'notice' + (isError ? ' is-error' : '');
    dom['notice-box'].hidden = false;
  }

  function logEvent(text, cls) {
    var line = R.el('div', 'term-line' + (cls ? ' ' + cls : ''), text);
    dom['event-log'].appendChild(line);
    dom['event-log'].scrollTop = dom['event-log'].scrollHeight;
  }

  function clearEventLog() {
    dom['event-log'].innerHTML = '';
    logEvent('stream: connecting…', 'term-muted');
  }

  /* ==================== 渲染 ==================== */

  /** 渲染一帧：普通阶段追加卡片，最终建议帧写入强调卡片。 */
  function renderFrame(view) {
    if (view.stage === 'FINAL_SUGGESTION') {
      renderFinal(view);
    } else {
      dom['stage-list'].appendChild(R.buildCard(view));
    }
  }

  /** 最终建议卡：文本 + 置信度 + 引用来源。 */
  function renderFinal(view) {
    var data = view.data || {};
    dom['final-conf'].textContent = 'confidence ' + (data.confidenceText || '—');
    dom['final-text'].textContent = view.content;

    var cites = (data.citations || []).map(function (c) {
      return c.source + (c.page ? '（页码 ' + c.page + '）' : '');
    });
    dom['final-cite'].textContent = cites.length ? '引用：' + cites.join('、') : '';
    dom['final-box'].hidden = false;
  }

  /** 刷新侧栏全部指标。 */
  function renderSidebar() {
    var conf = store.confidence;
    dom['stat-confidence'].textContent = conf === null ? '—' : conf.toFixed(2);
    dom['stat-threshold'].textContent = CFG.confidenceThreshold.toFixed(2);
    dom['stat-confidence-bar'].style.width =
      (conf === null ? 0 : Math.min(100, conf * 100)) + '%';

    dom['stat-citations'].textContent = store.citations.length;
    dom['stat-evidence'].textContent = store.evidenceCount;
    dom['stat-evidence-detail'].textContent = store.evidenceCount
      ? '三阶段 RAG 精排 Top-' + store.evidenceCount
      : '尚未检索';

    var tokens = estimateTokens();
    var mem = store.memoryState(tokens);
    dom['stat-memory'].textContent = mem.text;
    dom['stat-memory-detail'].textContent =
      '历史 ' + mem.used.toLocaleString('en-US') + ' / ' + mem.threshold.toLocaleString('en-US') + ' token';

    var a = store.audit;
    dom['stat-audit'].innerHTML = '';
    [['id', a ? a.id : '—'],
     ['finalConfidence', a ? String(a.finalConfidence) : '—'],
     ['stageTrace', a ? a.stageTrace + '已落库' : '—'],
     ['createdAt', a ? a.createdAt : '—']
    ].forEach(function (kv) {
      var line = R.el('div', null, kv[0] + '：' + kv[1]);
      dom['stat-audit'].appendChild(line);
    });
  }

  /** token 估算：演示模式使用固定历史占用，真实模式按字符数粗估。 */
  function estimateTokens() {
    if (isOffline()) { return demoHistoryTokens; }
    var chars = currentQuery.length;
    store.frames.forEach(function (f) { chars += (f.content || '').length; });
    return Math.round(chars * 0.75);
  }

  /** 是否处于离线渲染路径（演示模式或文档截图预览模式）。 */
  function isOffline() {
    return CFG.demoMode || CFG.previewMode;
  }

  /** 更新顶部运行状态行。 */
  function renderRunMeta(state) {
    var done = store.stageCount();
    var text = 'POST /api/think · SSE 流式推送 · ';
    if (state === 'idle') {
      text += '待发起';
    } else if (state === 'running') {
      text += '进行中 ' + done + '/6 阶段';
    } else {
      var cost = ((store.finishedAt - store.startedAt) / 1000).toFixed(1);
      text += '已完成 ' + done + '/6 阶段 · 用时 ' + cost + 's';
    }
    dom['run-meta'].textContent = text;
  }

  /* ==================== 一轮推理 ==================== */

  /** 重置界面，进入新一轮推理。 */
  function beginRun(query) {
    running = true;
    currentQuery = query;
    store.reset();
    store.startedAt = Date.now();

    dom['stage-list'].innerHTML = '';
    dom['final-box'].hidden = true;
    dom['question-box'].textContent = query;
    dom['question-box'].hidden = false;
    dom['query-input'].disabled = true;
    dom['submit-btn'].disabled = true;
    dom['stop-btn'].disabled = false;
    showNotice('');
    clearEventLog();
    renderSidebar();
    renderRunMeta('running');
  }

  /** 结束一轮推理。 */
  function endRun(state) {
    running = false;
    store.finishedAt = Date.now();
    store.buildAudit(currentQuery);

    dom['query-input'].disabled = false;
    dom['submit-btn'].disabled = false;
    dom['stop-btn'].disabled = true;

    renderSidebar();
    renderRunMeta(state);
  }

  /** 处理单帧（真实流与演示流共用）。 */
  function handleFrame(raw) {
    var view = P.parseFrame(raw);
    var accepted = store.push(view);
    if (!accepted) { return; }
    renderFrame(accepted);
    renderSidebar();
    renderRunMeta('running');
    logEvent('event: ' + accepted.stage + ' ✓', 'term-ok');
  }

  /**
   * 创建 SSE 客户端。全局唯一实例：客户端内部持有会话 ID，
   * 复用同一实例才能让后续请求持续携带 X-Conversation-Id，
   * 从而使后端记忆模块按会话累积历史（多轮对话的长期记忆依赖此点）。
   */
  function ensureClient() {
    if (client) { return client; }
    client = new global.SseClient({
      baseUrl: CFG.apiBase,
      onOpen: function (info) {
        store.setConversationId(info.conversationId);
        dom['session-id'].textContent = '会话：' + (info.conversationId || '—');
        setConn('open', '已连接');
        logEvent('stream: opened', 'term-muted');
      },
      onFrame: handleFrame,
      onDone: function () {
        setConn('open', '已完成');
        logEvent('stream: closed', 'term-warn');
        endRun('done');
      },
      onError: function (err) {
        setConn('error', '异常');
        logEvent('stream: error ' + err.message, 'term-err');
        showNotice('推理流异常：' + err.message, true);
        endRun('error');
      }
    });
    return client;
  }

  /** 发起真实流式推理。 */
  function startReal(query) {
    setConn('running', '推理中');
    ensureClient().stream(query).catch(function (err) {
      // fetch 层失败（如后端未启动）也走异常分支
      if (running) {
        setConn('error', '异常');
        logEvent('stream: error ' + err.message, 'term-err');
        showNotice('无法连接后端服务（' + err.message + '）。可勾选「演示模式」离线回放。', true);
        endRun('error');
      }
    });
  }

  /** 回放内置演示帧序列（演示模式与文档截图预览模式共用）。 */
  function startDemo(query) {
    var frames = global.DemoFrames.frames;
    var tag = CFG.previewMode ? '' : ' (demo)';   // 预览模式不暴露数据来源标识
    store.setConversationId(global.DemoFrames.conversationId);
    dom['session-id'].textContent = '会话：' + global.DemoFrames.conversationId;

    beginRun(query);
    // 回放耗时按脚本化总时长回填，避免瞬时渲染导致“用时 0.1s”的失真展示
    store.startedAt = Date.now() - (global.DemoFrames.durationMs || 0);
    setConn('running', CFG.previewMode ? '推理中' : '演示回放');
    logEvent('stream: opened' + tag, 'term-muted');

    var i = 0;
    function next() {
      if (i >= frames.length) {
        setConn('open', '已完成');
        logEvent('stream: closed' + tag, 'term-warn');
        endRun('done');
        return;
      }
      handleFrame(frames[i++]);
      if (CFG.demoInstant) {
        next();
      } else {
        global.setTimeout(next, CFG.demoFrameDelayMs);
      }
    }

    if (CFG.demoInstant) {
      next();
    } else {
      global.setTimeout(next, 300);
    }
  }

  /* ==================== 事件绑定 ==================== */

  function bindEvents() {
    dom['ask-form'].addEventListener('submit', function (e) {
      e.preventDefault();
      var query = dom['query-input'].value.trim();
      if (!query || running) { return; }
      if (dom['demo-toggle'].checked) {
        startDemo(query);
      } else {
        beginRun(query);
        startReal(query);
      }
    });

    dom['stop-btn'].addEventListener('click', function () {
      if (!running) { return; }
      if (client) { client.abort(); }
      logEvent('stream: aborted by user', 'term-warn');
      showNotice('已手动中止本轮推理。', false);
      endRun('error');
    });

    dom['demo-toggle'].addEventListener('change', function () {
      showNotice(dom['demo-toggle'].checked
        ? '演示模式已开启：将回放内置帧序列，不请求后端服务。' : '');
    });
  }

  /* ==================== 启动 ==================== */

  function boot() {
    cacheDom();
    dom['stat-threshold'].textContent = CFG.confidenceThreshold.toFixed(2);
    dom['session-id'].textContent = '会话：—';
    clearEventLog();
    renderSidebar();
    renderRunMeta('idle');
    bindEvents();

    // ?demo=1 自动回放并勾选演示模式；?preview=1 仅用于生成文档插图，不改变控件状态
    if (isOffline()) {
      dom['demo-toggle'].checked = CFG.demoMode;
      dom['query-input'].value = global.DemoFrames.query;
      startDemo(global.DemoFrames.query);
    }
  }

  if (doc.readyState === 'loading') {
    doc.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }

  global.MedicalAgentApp = {
    store: store,
    handleFrame: handleFrame
  };
})(window);
