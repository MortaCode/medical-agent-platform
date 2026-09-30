/**
 * 演示模式帧序列（离线回放）。
 *
 * <p>后端（Milvus / Elasticsearch / 大模型）未就绪时，开启演示模式即可回放本文件
 * 内置的六阶段帧序列，用于界面演示、前端自测与软著验收。帧结构与后端
 * {@code ThoughtFrame} 完全一致（{@code metadata} 同为 JSON 字符串），
 * 因此渲染路径与真实 SSE 流完全相同。</p>
 *
 * <p>数值口径说明：加权置信度 = 通过率×60% + 平均置信度×40%，
 * 本例 3 个候选方案中 2 个通过（0.92 / 0.86）、1 个未通过（0.62），
 * 故 通过率 = 2/3、平均置信度 = 0.80，加权置信度 = 0.67×0.6 + 0.80×0.4 = 0.72。</p>
 */
(function (global) {
  'use strict';

  var BASE_TS = 1789666975000;   // 演示用固定基准时间戳，保证截图可复现

  /** 构造与后端一致的帧对象（metadata 序列化为 JSON 字符串）。 */
  function frame(seq, stage, stageLabel, content, metadata, offsetMs) {
    return {
      seq: seq,
      stage: stage,
      stageLabel: stageLabel,
      content: content,
      metadata: JSON.stringify(metadata),
      timestamp: BASE_TS + offsetMs
    };
  }

  var DEMO_CONVERSATION_ID = 'conv-20260920-0042';

  var DEMO_QUERY = '患者男，45岁，发热三天伴咽痛，体温最高38.5度';

  var DEMO_FRAMES = [
    frame(1, 'SYMPTOM_DECOMPOSE', '症状拆解',
      '分诊完成：症状=[发热, 咽痛]；紧急度=MEDIUM',
      {
        symptoms: ['发热', '咽痛'],
        vitalSignsConcern: ['体温 38.5℃'],
        duration: '3 天',
        redFlags: [],
        suspectedDepartments: ['呼吸内科', '耳鼻喉科'],
        urgency: 'MEDIUM'
      }, 1200),

    frame(2, 'EVIDENCE_RETRIEVAL', '证据检索',
      '三阶段 RAG 精排完成，返回 5 条循证证据',
      [
        {
          source: '急性上呼吸道感染诊疗指南',
          page: '12',
          url: 'https://med-lib.example.org/uri-2019-12',
          excerpt: '急性上呼吸道感染多由病毒引起，以对症治疗为主，不推荐常规使用抗菌药物。'
        },
        {
          source: '对乙酰氨基酚片说明书',
          page: '3',
          url: 'https://med-lib.example.org/otc-paracetamol',
          excerpt: '成人一次 0.5g，若持续发热或疼痛可间隔 4~6 小时重复用药，24 小时内不超过 4 次。'
        },
        {
          source: '急性扁桃体炎诊治共识',
          page: '7',
          url: 'https://med-lib.example.org/tonsillitis-2021',
          excerpt: '咽痛伴发热时应评估扁桃体肿大与脓性分泌物，必要时行咽拭子培养。'
        },
        {
          source: '流行性感冒诊疗方案',
          page: '4',
          url: 'https://med-lib.example.org/flu-2020-04',
          excerpt: '流感多伴全身肌肉酸痛、乏力等全身症状，可与普通上呼吸道感染相鉴别。'
        },
        {
          source: '复方氨酚烷胺胶囊说明书',
          page: '2',
          url: 'https://med-lib.example.org/otc-compound-amantadine',
          excerpt: '本品每粒含对乙酰氨基酚 0.25g，避免与其他含对乙酰氨基酚制剂同时服用。'
        }
      ], 6400),

    frame(3, 'HYPOTHESIS_GEN', '假设生成',
      '生成诊断假设：[急性上呼吸道感染, 急性扁桃体炎, 流行性感冒]',
      {
        hypotheses: [
          {
            name: '急性上呼吸道感染',
            probability: 0.62,
            supporting: ['发热 3 天', '咽痛', '无全身肌肉酸痛'],
            contradicting: ['未见明显鼻塞流涕'],
            neededTests: ['血常规', '咽拭子']
          },
          {
            name: '急性扁桃体炎',
            probability: 0.24,
            supporting: ['咽痛明显', '发热'],
            contradicting: ['未查见扁桃体化脓'],
            neededTests: ['咽部查体', '咽拭子培养']
          },
          {
            name: '流行性感冒',
            probability: 0.14,
            supporting: ['发热'],
            contradicting: ['无全身肌肉酸痛', '无流行病学接触史'],
            neededTests: ['流感病毒抗原检测']
          }
        ],
        finalDiagnosis: '急性上呼吸道感染',
        confidence: 0.86
      }, 10800),

    frame(4, 'DIFFERENTIAL_DIAGNOSIS', '鉴别诊断',
      '倾向性诊断=急性上呼吸道感染；置信度=0.86',
      {
        hypotheses: [
          {
            name: '急性上呼吸道感染',
            probability: 0.62,
            supporting: ['发热 3 天', '咽痛', '无全身肌肉酸痛'],
            contradicting: ['未见明显鼻塞流涕'],
            neededTests: ['血常规', '咽拭子']
          }
        ],
        finalDiagnosis: '急性上呼吸道感染',
        confidence: 0.86
      }, 13200),

    frame(5, 'CONFIDENCE_SCORE', '置信度评分',
      '并行审方投票完成，加权置信度=0.72',
      [
        {
          medication: '对乙酰氨基酚片 0.5g 口服，必要时',
          approve: true,
          issues: [],
          contraindications: ['严重肝肾功能不全者禁用'],
          dosageAdvice: '一次 0.5g，间隔 4~6 小时，24 小时内不超过 4 次',
          monitoring: ['肝功能', '体温变化'],
          confidence: 0.92
        },
        {
          medication: '连花清瘟胶囊 口服',
          approve: true,
          issues: ['中成药，需辨证使用'],
          contraindications: ['风寒感冒者不适用'],
          dosageAdvice: '一次 4 粒，一日 3 次',
          monitoring: ['胃肠道反应'],
          confidence: 0.86
        },
        {
          medication: '复方氨酚烷胺胶囊 口服',
          approve: false,
          issues: ['与对乙酰氨基酚重复用药，存在过量风险'],
          contraindications: ['活动性消化道溃疡者禁用'],
          dosageAdvice: '不建议与对乙酰氨基酚联用',
          monitoring: ['肝功能'],
          confidence: 0.62
        }
      ], 18600),

    frame(6, 'FINAL_SUGGESTION', '最终建议',
      '考虑急性上呼吸道感染（病毒性可能性大）。建议：1）多饮水、充分休息；'
      + '2）体温超过 38.5℃ 可按说明书服用对乙酰氨基酚片；'
      + '3）避免与其他含对乙酰氨基酚的复方制剂同时服用；'
      + '4）若出现持续高热、呼吸困难等危险信号请及时就医。'
      + '以上建议仅供参考，不构成医疗诊断。',
      {
        confidence: 0.72,
        citations: [
          { source: '急性上呼吸道感染诊疗指南', page: '12', url: 'https://med-lib.example.org/uri-2019-12', excerpt: '' },
          { source: '对乙酰氨基酚片说明书', page: '3', url: 'https://med-lib.example.org/otc-paracetamol', excerpt: '' }
        ]
      }, 24800)
  ];

  global.DemoFrames = {
    conversationId: DEMO_CONVERSATION_ID,
    query: DEMO_QUERY,
    /** 演示用总耗时（毫秒），对应最后一帧的时间偏移，用于顶部运行状态展示 */
    durationMs: 24800,
    frames: DEMO_FRAMES
  };
})(window);
