/**
 * 思考帧解析器：把后端 ThoughtFrame 转成可直接渲染的视图模型。
 *
 * <p>后端 {@code ThoughtFrame.metadata} 是「已序列化的 JSON 字符串」，且不同阶段
 * 承载的结构完全不同（分诊结果 / 引用数组 / 诊断结论 / 审方投票 / 最终建议），
 * 因此这里按 {@code stage} 分发到对应解析函数，统一产出 {@code {kind, data}} 视图模型，
 * 供 {@link ThoughtRender} 消费。</p>
 */
(function (global) {
  'use strict';

  /**
   * 六个 CoT 阶段的展示元数据。
   * 注意 no 的顺序与后端 AgentRouter 实际发射顺序一致：
   * 症状拆解 -> 证据检索 -> 假设生成 -> 鉴别诊断 -> 置信度评分 -> 最终建议。
   */
  var STAGES = [
    { key: 'SYMPTOM_DECOMPOSE',    no: 1, label: '症状拆解',   hint: 'TriageAgent 结构化症状与紧急度' },
    { key: 'EVIDENCE_RETRIEVAL',   no: 2, label: '证据检索',   hint: '三阶段 RAG 精排返回循证证据' },
    { key: 'HYPOTHESIS_GEN',       no: 3, label: '假设生成',   hint: 'DiagnosisAgent 生成诊断假设' },
    { key: 'DIFFERENTIAL_DIAGNOSIS', no: 4, label: '鉴别诊断', hint: '倾向性诊断与置信度' },
    { key: 'CONFIDENCE_SCORE',     no: 5, label: '置信度评分', hint: '多药师并行审方投票加权' },
    { key: 'FINAL_SUGGESTION',     no: 6, label: '最终建议',   hint: '综合分诊/诊断/审方的可行动建议' }
  ];

  var STAGE_MAP = {};
  STAGES.forEach(function (s) { STAGE_MAP[s.key] = s; });

  /** 安全解析 JSON 字符串，失败返回 null（不抛异常，避免单帧异常中断整条流）。 */
  function safeParse(text) {
    if (!text) { return null; }
    if (typeof text === 'object') { return text; }
    try {
      return JSON.parse(text);
    } catch (e) {
      return null;
    }
  }

  function arr(v) { return Array.isArray(v) ? v : []; }

  function num(v) {
    var n = typeof v === 'number' ? v : parseFloat(v);
    return isFinite(n) ? n : 0;
  }

  /** 置信度/概率统一格式化为两位小数，与后端 Math.round(x*100)/100 口径一致。 */
  function fixed2(v) { return num(v).toFixed(2); }

  /** 百分比宽度（用于概率条/置信度条），做 0~100 截断。 */
  function pct(v) {
    var p = num(v) * 100;
    if (p < 0) { p = 0; }
    if (p > 100) { p = 100; }
    return p;
  }

  /** 分诊结果 TriageResult -> 视图模型。 */
  function parseTriage(m) {
    if (!m) { return null; }
    return {
      symptoms: arr(m.symptoms),
      vitalSignsConcern: arr(m.vitalSignsConcern),
      duration: m.duration || '',
      redFlags: arr(m.redFlags),
      suspectedDepartments: arr(m.suspectedDepartments),
      urgency: (m.urgency || 'LOW').toUpperCase()
    };
  }

  /** 引用数组 List<Citation> -> 视图模型。 */
  function parseCitations(m) {
    var list = arr(m);
    return list.map(function (c) {
      return {
        source: c.source || '',
        page: c.page || '',
        url: c.url || '',
        excerpt: c.excerpt || ''
      };
    });
  }

  /** 诊断结论 DiagnosisResult -> 视图模型（含假设列表）。 */
  function parseDiagnosis(m) {
    if (!m) { return null; }
    return {
      hypotheses: arr(m.hypotheses).map(function (h) {
        return {
          name: h.name || '',
          probability: num(h.probability),
          probabilityText: fixed2(h.probability),
          supporting: arr(h.supporting),
          contradicting: arr(h.contradicting),
          neededTests: arr(h.neededTests)
        };
      }),
      finalDiagnosis: m.finalDiagnosis || '',
      confidence: num(m.confidence),
      confidenceText: fixed2(m.confidence)
    };
  }

  /** 审方投票 List<PharmacyResult> -> 视图模型。 */
  function parsePharmacy(m) {
    var list = arr(m);
    var approved = list.filter(function (v) { return !!v.approve; }).length;
    var avg = list.length
      ? list.reduce(function (s, v) { return s + num(v.confidence); }, 0) / list.length
      : 0;
    return {
      votes: list.map(function (v) {
        return {
          medication: v.medication || '',
          approve: !!v.approve,
          issues: arr(v.issues),
          contraindications: arr(v.contraindications),
          dosageAdvice: v.dosageAdvice || '',
          monitoring: arr(v.monitoring),
          confidence: num(v.confidence),
          confidenceText: fixed2(v.confidence)
        };
      }),
      approved: approved,
      total: list.length,
      approvalRate: list.length ? approved / list.length : 0,
      avgConfidence: avg,
      avgConfidenceText: fixed2(avg)
    };
  }

  /** 最终建议 {confidence, citations} -> 视图模型。 */
  function parseFinal(m) {
    var obj = m && typeof m === 'object' ? m : {};
    return {
      confidence: num(obj.confidence),
      confidenceText: fixed2(obj.confidence),
      citations: parseCitations(obj.citations)
    };
  }

  /**
   * 解析单个 ThoughtFrame。
   * @param {Object} frame 后端推送的原始帧
   * @returns {Object} 视图模型 {seq, stage, stageLabel, no, content, kind, data, ts}
   */
  function parseFrame(frame) {
    var meta = safeParse(frame.metadata);
    var stageKey = frame.stage || '';
    var stageDef = STAGE_MAP[stageKey] || { no: 0, label: frame.stageLabel || stageKey, hint: '' };
    var kind = 'text';
    var data = null;

    switch (stageKey) {
      case 'SYMPTOM_DECOMPOSE':
        kind = 'triage';   data = parseTriage(meta);     break;
      case 'EVIDENCE_RETRIEVAL':
        kind = 'citations'; data = parseCitations(meta); break;
      case 'HYPOTHESIS_GEN':
        kind = 'diagnosis'; data = parseDiagnosis(meta); break;
      case 'DIFFERENTIAL_DIAGNOSIS':
        kind = 'diagnosis'; data = parseDiagnosis(meta); break;
      case 'CONFIDENCE_SCORE':
        kind = 'pharmacy';  data = parsePharmacy(meta);  break;
      case 'FINAL_SUGGESTION':
        kind = 'final';     data = parseFinal(meta);     break;
      default:
        kind = 'text';      data = meta;
    }

    return {
      seq: typeof frame.seq === 'number' ? frame.seq : 0,
      stage: stageKey,
      stageLabel: frame.stageLabel || stageDef.label,
      no: stageDef.no,
      hint: stageDef.hint,
      content: frame.content || '',
      kind: kind,
      data: data,
      ts: frame.timestamp || Date.now()
    };
  }

  global.ThoughtParser = {
    STAGES: STAGES,
    STAGE_MAP: STAGE_MAP,
    parseFrame: parseFrame,
    safeParse: safeParse,
    fixed2: fixed2,
    pct: pct,
    num: num
  };
})(window);
