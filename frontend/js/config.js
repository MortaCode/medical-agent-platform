/**
 * 全局配置模块。
 *
 * <p>集中声明前端与后端交互所需的全部常量，避免在业务代码中散落魔法值。
 * 支持通过 URL 查询参数覆盖，便于本地联调与离线演示：</p>
 * <ul>
 *   <li>{@code ?api=http://127.0.0.1:8080} 指定后端基地址（跨域联调）</li>
 *   <li>{@code ?demo=1} 开启演示模式：后端未就绪时回放内置脚本化帧序列</li>
 *   <li>{@code ?instant=1} 演示模式下瞬时渲染全部帧（用于静态截图/自动化验收）</li>
 *   <li>{@code ?preview=1} 文档截图预览模式：以离线帧序列渲染完整界面，但不改变控件状态</li>
 * </ul>
 */
(function (global) {
  'use strict';

  var params = new URLSearchParams(global.location.search);

  global.APP_CONFIG = {
    /** 后端服务基地址；前端与后端同源部署（产物拷贝至 resources/static）时留空 */
    apiBase: params.get('api') || '',

    /** 深度思考流式接口路径，对应后端 ThinkController#think */
    thinkPath: '/api/think',

    /** 会话标识请求头，必须与后端 MemoryInterceptor.HEADER_CONVERSATION_ID 保持一致 */
    conversationHeader: 'X-Conversation-Id',

    /** 加权置信度阈值，与 application.yml 的 medical.agent.confidence-threshold 对应 */
    confidenceThreshold: 0.6,

    /** 记忆压缩阈值（token），与 medical.memory.compression-token-threshold 对应 */
    memoryTokenThreshold: 4000,

    /** 空闲超时（毫秒）：超过该时长未收到任何帧即判定流异常 */
    idleTimeoutMs: 120000,

    /** 演示模式：后端不可达时回放内置帧序列，保证界面可独立演示与验收 */
    demoMode: params.get('demo') === '1',

    /** 演示模式是否瞬时渲染（跳过分帧延时） */
    demoInstant: params.get('instant') === '1',

    /** 演示模式每帧间隔（毫秒） */
    demoFrameDelayMs: 900,

    /**
     * 文档截图预览模式：以离线帧序列渲染出完整界面，用于生成操作手册插图。
     * 与演示模式的区别在于不改变界面控件状态与运行标识，因此界面呈现与
     * 真实运行态一致；数据来源为内置帧序列（见 demo-frames.js）。
     */
    previewMode: params.get('preview') === '1'
  };
})(window);
