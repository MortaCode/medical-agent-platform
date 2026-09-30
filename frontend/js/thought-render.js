/**
 * 逐帧渲染器：把解析后的思考帧视图模型渲染为 DOM 节点。
 *
 * <p>按 stage 分派到对应的渲染函数，各渲染函数只负责「生成节点」，
 * 不直接操作全局状态，保证渲染逻辑与状态管理解耦。</p>
 */
(function (global) {
  'use strict';

  var P = global.ThoughtParser;

  /** 创建元素并一次性写入属性/文本，避免逐条 setAttribute 的冗长写法。 */
  function el(tag, className, text) {
    var node = global.document.createElement(tag);
    if (className) { node.className = className; }
    if (text !== undefined && text !== null) { node.textContent = String(text); }
    return node;
  }

  /** 生成标签组（症状/科室/检查等），空数组返回 null。 */
  function chipGroup(values) {
    if (!values || !values.length) { return null; }
    var frag = global.document.createDocumentFragment();
    values.forEach(function (v) {
      frag.appendChild(el('span', 'chip', v));
    });
    return frag;
  }

  /* ---------------- 各阶段内容渲染 ---------------- */

  /** ① 症状拆解：症状 / 疑似科室 / 危险信号 / 紧急度。 */
  function renderTriage(data) {
    var box = el('div', 'stage-text');
    if (!data) { return box; }

    box.appendChild(el('span', 'stage-label', '症状：'));
    var s = chipGroup(data.symptoms);
    if (s) { box.appendChild(s); }

    box.appendChild(el('span', 'stage-label', '疑似科室：'));
    var d = chipGroup(data.suspectedDepartments);
    if (d) { box.appendChild(d); }

    if (data.vitalSignsConcern.length) {
      box.appendChild(el('span', 'stage-label', '体征关注：'));
      box.appendChild(chipGroup(data.vitalSignsConcern));
    }

    box.appendChild(el('span', 'stage-label', '危险信号：'));
    box.appendChild(el('span', null, data.redFlags.length ? data.redFlags.join('、') : '无'));

    box.appendChild(el('span', 'stage-label', '　紧急度：'));
    box.appendChild(el('span', 'urgency urgency-' + data.urgency, data.urgency));

    if (data.duration) {
      box.appendChild(el('span', 'stage-label', '　病程：'));
      box.appendChild(el('span', null, data.duration));
    }
    return box;
  }

  /** ② 证据检索：逐条列出精排后的 Top-K 循证引用。 */
  function renderCitations(list) {
    var box = el('div', 'stage-text');
    if (!list || !list.length) {
      box.appendChild(el('span', 'stage-label', '本轮未召回可用证据'));
      return box;
    }
    list.forEach(function (c, i) {
      var row = el('div', 'evidence');
      row.appendChild(el('span', 'ev-no', '[证据' + (i + 1) + '] '));
      row.appendChild(el('span', null, c.source));
      if (c.page) {
        row.appendChild(el('span', 'ev-page', '　页码 ' + c.page));
      }
      if (c.url) {
        var a = el('a', 'ev-url', '　' + c.url);
        a.href = c.url;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        row.appendChild(a);
      }
      if (c.excerpt) {
        row.appendChild(el('div', 'ev-excerpt', c.excerpt));
      }
      box.appendChild(row);
    });
    return box;
  }

  /** ③/④ 诊断：假设概率条列表（③）或倾向性结论（④）。 */
  function renderDiagnosis(data, mode) {
    var box = el('div', 'stage-text');
    if (!data) { return box; }

    if (mode === 'hypotheses') {
      if (!data.hypotheses.length) {
        box.appendChild(el('span', 'stage-label', '未生成诊断假设'));
        return box;
      }
      data.hypotheses.forEach(function (h) {
        var row = el('div', 'hyp');
        row.appendChild(el('span', 'hyp-name', h.name));

        var wrap = el('span', 'hyp-bar-wrap');
        var bar = el('span', 'hyp-bar');
        var fill = el('i');
        fill.style.width = P.pct(h.probability) + '%';
        bar.appendChild(fill);
        wrap.appendChild(bar);
        row.appendChild(wrap);

        row.appendChild(el('span', 'hyp-val', h.probabilityText));
        box.appendChild(row);
      });
      return box;
    }

    // 鉴别诊断：倾向性诊断 + 置信度 + 支持/相悖证据 + 建议检查
    box.appendChild(el('span', 'stage-label', '倾向性诊断 '));
    box.appendChild(el('strong', null, data.finalDiagnosis || '—'));
    box.appendChild(el('span', 'stage-label', '（置信度 ' + data.confidenceText + '）'));

    var top = data.hypotheses.length ? data.hypotheses[0] : null;
    if (top) {
      if (top.supporting.length) {
        box.appendChild(el('div', null, '支持证据：' + top.supporting.join('；')));
      }
      if (top.contradicting.length) {
        box.appendChild(el('div', null, '相悖证据：' + top.contradicting.join('；')));
      }
      if (top.neededTests.length) {
        box.appendChild(el('div', null, '建议检查：' + top.neededTests.join('、')));
      }
    }
    return box;
  }

  /** ⑤ 置信度评分：逐条审方投票结果 + 加权汇总说明。 */
  function renderPharmacy(data) {
    var box = el('div', 'stage-text');
    if (!data || !data.votes.length) {
      box.appendChild(el('span', 'stage-label', '本轮无候选用药方案'));
      return box;
    }
    data.votes.forEach(function (v) {
      var row = el('div', 'vote');
      row.appendChild(el('span', null, v.medication));
      if (v.approve) {
        row.appendChild(el('span', 'vote-pass', '　✓ 通过（' + v.confidenceText + '）'));
      } else {
        var reason = v.issues.length ? ' ' + v.issues.join('；') : '';
        row.appendChild(el('span', 'vote-fail', '　✗ 未通过（' + v.confidenceText + '）' + reason));
      }
      box.appendChild(row);
    });

    var weighted = (data.approvalRate * 0.6 + data.avgConfidence * 0.4);
    box.appendChild(el('div', null,
      '并行审方投票完成：通过率 ' + data.approved + '/' + data.total
      + '，平均置信度 ' + data.avgConfidenceText
      + '，加权置信度 = ' + P.fixed2(weighted)
      + '（通过率×60% + 平均置信度×40%）'));
    return box;
  }

  /** 生成阶段卡片外壳（序号圆点 + 标题 + 事件名 + 状态徽标 + 内容）。 */
  function buildCard(view) {
    var li = el('li', 'stage');
    li.appendChild(el('div', 'stage-no', view.no || '·'));

    var body = el('div', 'stage-body');
    var head = el('div', 'stage-head');

    var titleBox = el('div');
    titleBox.appendChild(el('span', 'stage-title', view.stageLabel));
    titleBox.appendChild(el('span', 'stage-tag', view.stage));
    head.appendChild(titleBox);

    var badge = el('div', 'stage-badge');
    head.appendChild(badge);
    body.appendChild(head);

    var content;
    switch (view.kind) {
      case 'triage':    content = renderTriage(view.data);               badge.textContent = '✓ 完成'; break;
      case 'citations': content = renderCitations(view.data);            badge.textContent = '✓ 证据 ' + ((view.data && view.data.length) || 0) + ' 条'; break;
      case 'diagnosis':
        if (view.stage === 'HYPOTHESIS_GEN') {
          content = renderDiagnosis(view.data, 'hypotheses');
          badge.textContent = '✓ 假设 ' + ((view.data && view.data.hypotheses.length) || 0) + ' 条';
        } else {
          content = renderDiagnosis(view.data, 'final');
          badge.textContent = '✓ 完成';
        }
        break;
      case 'pharmacy':  content = renderPharmacy(view.data);             badge.textContent = '✓ ' + ((view.data && view.data.total) || 0) + ' 路审方'; break;
      default:          content = el('div', 'stage-text', view.content);  badge.textContent = '✓ 完成';
    }
    body.appendChild(content);
    li.appendChild(body);
    return li;
  }

  global.ThoughtRender = {
    buildCard: buildCard,
    el: el
  };
})(window);
