# 医疗智能体协同推理平台 V1.0 —— 前端

零依赖（无构建步骤）的原生前端，负责把后端 `POST /api/think` 的 SSE 思考流
逐帧渲染为可视化的 CoT 推理过程。

## 目录结构

```
frontend/
├── index.html              页面骨架：顶部栏 + 主区逐帧渲染 + 侧栏指标
├── css/
│   └── app.css             样式表
└── js/
    ├── config.js           全局配置（API 基地址、阈值、演示模式开关）
    ├── sse-client.js       SSE 流式客户端（fetch + ReadableStream 手工解析）
    ├── frame-parser.js     ThoughtFrame -> 视图模型（按 stage 分派 metadata 解析）
    ├── thought-render.js   逐帧 DOM 渲染（六阶段卡片 + 审方投票 + 证据列表）
    ├── session-store.js    会话状态仓库（有序累积、置信度、引用、审计快照）
    ├── demo-frames.js      演示模式内置帧序列（离线回放）
    └── app.js              主控制器（输入 -> 流式推理 -> 渲染 -> 指标）
```

## 为什么不用原生 EventSource

`POST /api/think` 既要求 **POST** 请求体 `{"query":"..."}`，又要求携带
**`X-Conversation-Id`** 会话头；而浏览器原生 `EventSource` 只支持 GET、
且无法自定义请求头。因此 `sse-client.js` 采用
`fetch()` + `response.body.getReader()` + `TextDecoder` 手工解析 SSE 报文：
以空行切分事件块，块内识别 `event:` / `data:` 字段，以冒号开头的行（心跳）忽略。

## 运行方式

### 方式一：与后端同源部署（推荐，无需处理跨域）

把 `frontend/` 下的全部文件拷贝到后端工程的 `src/main/resources/static/`，
启动后端后访问 `http://127.0.0.1:8080/index.html` 即可。

### 方式二：独立部署 / 本地联调

用任意静态服务器托管 `frontend/`，并通过 `api` 查询参数指向后端地址：

```
http://127.0.0.1:5173/?api=http://127.0.0.1:8080
```

跨域场景需后端放开 CORS（允许 `X-Conversation-Id` 请求头与
`X-Conversation-Id` 响应头的暴露）。

## 离线渲染模式（后端未就绪时）

```
index.html?demo=1              # 演示模式：勾选界面上的「演示模式」并回放内置六阶段帧序列
index.html?demo=1&instant=1    # 瞬时渲染全部帧（跳过分帧延时）
index.html?preview=1&instant=1 # 文档截图预览：同样回放，但不改变任何控件状态
```

三种模式都不发起网络请求，渲染路径与真实 SSE 流完全一致。
`preview` 模式专用于生成操作手册等文档的界面插图——界面呈现与真实运行态一致，
数据来源为内置帧序列（见 `demo-frames.js`）。

## 测试

`build/test_sse.js`（需 Node 22）用 mock 后端验证 SSE 客户端的真实路径，
覆盖跨 chunk 分片、中文增量解码、会话头跨轮次延续：

```
node build/test_sse.js
```

## 接口约定

| 项目 | 值 |
| --- | --- |
| 请求 | `POST /api/think`，`Content-Type: application/json`，体 `{"query":"..."}` |
| 响应 | `text/event-stream` |
| 会话头 | `X-Conversation-Id`（请求携带、响应回写） |
| 事件名 | 阶段枚举名，如 `FINAL_SUGGESTION` |
| 数据体 | `ThoughtFrame` JSON：`seq` / `stage` / `stageLabel` / `content` / `metadata` / `timestamp` |

`metadata` 为已序列化的 JSON 字符串，按阶段对应不同结构：

| 阶段 | metadata 结构 |
| --- | --- |
| `SYMPTOM_DECOMPOSE` | `TriageResult` |
| `EVIDENCE_RETRIEVAL` | `Citation[]` |
| `HYPOTHESIS_GEN` | `DiagnosisResult` |
| `DIFFERENTIAL_DIAGNOSIS` | `DiagnosisResult` |
| `CONFIDENCE_SCORE` | `PharmacyResult[]` |
| `FINAL_SUGGESTION` | `{confidence, citations}` |

## 数值口径

加权置信度 = 通过率 × 60% + 平均置信度 × 40%，保留两位小数，
与后端 `AgentRouter#weightedConfidence` 完全一致。
