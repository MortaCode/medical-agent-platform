/**
 * 会话状态仓库：集中维护一次推理过程中的所有派生指标。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>按 {@code seq} 有序累积已渲染的思考帧，避免乱序到达导致卡片错位；</li>
 *   <li>汇总本轮引用条数、加权置信度、审方投票统计；</li>
 *   <li>维护会话 ID 与最近一次审计记录快照，供侧栏展示。</li>
 * </ul>
 */
(function (global) {
  'use strict';

  var CFG = global.APP_CONFIG;

  function SessionStore() {
    this.reset();
  }

  /** 开始新一轮推理：清空上一轮派生状态，但保留会话 ID（长期记忆需按会话连续）。 */
  SessionStore.prototype.reset = function () {
    this.frames = [];          // 已解析的思考帧视图模型，按 seq 升序
    this.seqSet = {};          // 去重：同一 seq 只保留首个
    this.evidenceCount = 0;    // 证据检索阶段精排返回的证据条数
    this.citations = [];       // 最终建议帧实际引用的文献条数（引用清单）
    this.confidence = null;    // 加权置信度
    this.pharmacy = null;      // 审方投票统计
    this.diagnosis = null;     // 诊断结论
    this.triage = null;        // 分诊结果
    this.audit = null;         // 审计记录快照
    this.startedAt = 0;
    this.finishedAt = 0;
  };

  /** 会话 ID 由服务端响应头回写，需与后端 MemoryInterceptor 保持一致。 */
  SessionStore.prototype.setConversationId = function (id) {
    this.conversationId = id || this.conversationId;
    return this.conversationId;
  };

  /**
   * 摄入一帧：按 seq 有序插入，并按阶段更新派生指标。
   * @returns {Object|null} 若该帧为首次到达则返回视图模型，重复帧返回 null
   */
  SessionStore.prototype.push = function (view) {
    if (!view || this.seqSet[view.seq]) { return null; }
    this.seqSet[view.seq] = true;

    // 有序插入：seq 单调递增，绝大多数情况下直接 push
    var idx = this.frames.length;
    while (idx > 0 && this.frames[idx - 1].seq > view.seq) { idx--; }
    this.frames.splice(idx, 0, view);

    switch (view.stage) {
      case 'SYMPTOM_DECOMPOSE':
        this.triage = view.data;
        break;
      case 'EVIDENCE_RETRIEVAL':
        this.evidenceCount = (view.data || []).length;
        this.citations = (view.data || []).slice();
        break;
      case 'HYPOTHESIS_GEN':
      case 'DIFFERENTIAL_DIAGNOSIS':
        this.diagnosis = view.data;
        break;
      case 'CONFIDENCE_SCORE':
        this.pharmacy = view.data;
        if (view.data && typeof view.data.approvalRate === 'number') {
          // 与后端 AgentRouter#weightedConfidence 同口径：通过率 60% + 平均置信度 40%
          this.confidence = Math.round(
            (view.data.approvalRate * 0.6 + view.data.avgConfidence * 0.4) * 100) / 100;
        }
        break;
      case 'FINAL_SUGGESTION':
        if (view.data) {
          if (typeof view.data.confidence === 'number' && view.data.confidence > 0) {
            this.confidence = view.data.confidence;
          }
          if (view.data.citations && view.data.citations.length) {
            this.citations = view.data.citations.slice();
          }
        }
        break;
      default:
        break;
    }
    return view;
  };

  /** 阶段完成数（用于顶部 meta 展示 已完成 n/6 阶段）。 */
  SessionStore.prototype.stageCount = function () {
    return this.frames.length;
  };

  /** 是否已完成全部六个阶段。 */
  SessionStore.prototype.isComplete = function () {
    return this.frames.length >= 6;
  };

  /**
   * 构造审计记录快照。
   * 与后端 ReasoningAuditRecord 的字段口径对齐（id 形如 {conversationId}-{timestamp}）。
   */
  SessionStore.prototype.buildAudit = function (query) {
    var ts = this.finishedAt || Date.now();
    this.audit = {
      id: (this.conversationId || 'local') + '-' + ts,
      conversationId: this.conversationId || '—',
      query: query || '',
      finalConfidence: this.confidence === null ? '—' : this.confidence,
      stageTrace: this.frames.length + ' 阶段轨迹',
      createdAt: formatTime(ts)
    };
    return this.audit;
  };

  /** 记忆压缩状态：token 用量为估算值，阈值为后端配置项。 */
  SessionStore.prototype.memoryState = function (tokenEstimate) {
    var threshold = CFG.memoryTokenThreshold;
    return {
      triggered: tokenEstimate >= threshold,
      used: tokenEstimate,
      threshold: threshold,
      text: tokenEstimate >= threshold ? '已触发' : '未触发'
    };
  };

  /** 时间戳格式化为 yyyy-MM-dd HH:mm:ss。 */
  function formatTime(ts) {
    var d = new Date(ts);
    function p(n) { return (n < 10 ? '0' : '') + n; }
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate())
      + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
  }

  global.SessionStore = SessionStore;
  global.formatTime = formatTime;
})(window);
