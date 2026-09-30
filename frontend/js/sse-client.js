/**
 * SSE 流式客户端。
 *
 * <p>关键设计说明：本平台 {@code POST /api/think} 返回 {@code text/event-stream}。
 * 浏览器原生 {@code EventSource} 仅支持 GET 且无法携带自定义请求头，
 * 而本接口既要求 POST 请求体 {@code {"query":"..."}}，又要求携带
 * {@code X-Conversation-Id} 会话头，因此这里采用
 * {@code fetch} + {@code ReadableStream} 手工解析 SSE 报文的方式实现。</p>
 *
 * <p>报文解析遵循 SSE 规范：以「空行」切分事件块，块内逐行识别
 * {@code event:} / {@code data:} / {@code id:} / {@code retry:} 字段，
 * 以冒号开头的行为注释（服务端心跳），直接忽略。</p>
 */
(function (global) {
  'use strict';

  var CFG = global.APP_CONFIG;

  /**
   * @param {Object}   options
   * @param {string}   [options.baseUrl] 后端基地址
   * @param {Function} [options.onOpen]  连接建立回调，入参 {conversationId}
   * @param {Function} [options.onFrame] 收到一个思考帧回调，入参为解析后的 ThoughtFrame
   * @param {Function} [options.onDone]  流正常结束回调
   * @param {Function} [options.onError] 异常回调，入参为 Error
   */
  function SseClient(options) {
    var opts = options || {};
    this.baseUrl = opts.baseUrl || '';
    this.onOpen = opts.onOpen || function () {};
    this.onFrame = opts.onFrame || function () {};
    this.onDone = opts.onDone || function () {};
    this.onError = opts.onError || function () {};

    /** 当前进行中的请求控制器，用于「中止」 */
    this.controller = null;
    /** 会话 ID：由服务端响应头回写后持久持有，实现长期记忆按会话隔离 */
    this.conversationId = null;
    /** 未消费的报文缓冲（跨 chunk 的半个事件暂存在此） */
    this.buffer = '';
    this.idleTimer = null;
  }

  /** 中止当前流：主动 abort 不触发 onError。 */
  SseClient.prototype.abort = function () {
    this.clearIdleTimer();
    if (this.controller) {
      this.controller.abort();
      this.controller = null;
    }
  };

  SseClient.prototype.clearIdleTimer = function () {
    if (this.idleTimer) {
      global.clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
  };

  /** 重置空闲计时：每收到一帧就续期，长时间无帧则判定异常。 */
  SseClient.prototype.resetIdleTimer = function () {
    var self = this;
    self.clearIdleTimer();
    if (!CFG.idleTimeoutMs) { return; }
    self.idleTimer = global.setTimeout(function () {
      self.abort();
      self.onError(new Error('空闲超时：' + Math.round(CFG.idleTimeoutMs / 1000) + ' 秒内未收到任何思考帧'));
    }, CFG.idleTimeoutMs);
  };

  /**
   * 发起一次流式推理。
   * @param {string} query 患者主诉文本
   * @returns {Promise} 流处理结束（或异常）后 resolve
   */
  SseClient.prototype.stream = function (query) {
    var self = this;
    var headers = {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    };
    if (self.conversationId) {
      headers[CFG.conversationHeader] = self.conversationId;
    }

    self.controller = new AbortController();
    self.buffer = '';

    return fetch(self.baseUrl + CFG.thinkPath, {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({ query: query }),
      signal: self.controller.signal
    }).then(function (resp) {
      if (!resp.ok) {
        throw new Error('HTTP ' + resp.status + ' ' + resp.statusText);
      }
      // 服务端在响应头回写会话 ID，后续请求持续携带同一 ID
      var cid = resp.headers.get(CFG.conversationHeader);
      if (cid) { self.conversationId = cid; }
      self.onOpen({ conversationId: self.conversationId });
      self.resetIdleTimer();
      return self.pump(resp.body.getReader());
    }).catch(function (err) {
      if (err && err.name === 'AbortError') { return; }
      self.onError(err);
    });
  };

  /** 持续读取响应体，解码后交给缓冲区解析。 */
  SseClient.prototype.pump = function (reader) {
    var self = this;
    var decoder = new TextDecoder('utf-8');

    function step() {
      return reader.read().then(function (result) {
        if (result.done) {
          self.flush();
          self.clearIdleTimer();
          self.onDone();
          return;
        }
        // stream:true 保证多字节 UTF-8 字符跨 chunk 时不被截断
        self.buffer += decoder.decode(result.value, { stream: true });
        self.drain();
        return step();
      });
    }

    return step();
  };

  /** 按空行切分事件块并逐块派发。 */
  SseClient.prototype.drain = function () {
    var sep = /\r?\n\r?\n/;
    var m;
    while ((m = sep.exec(this.buffer)) !== null) {
      var block = this.buffer.slice(0, m.index);
      this.buffer = this.buffer.slice(m.index + m[0].length);
      this.dispatch(block);
    }
  };

  /** 流结束时处理缓冲区残留的最后一个事件（服务端未以空行收尾时）。 */
  SseClient.prototype.flush = function () {
    var rest = this.buffer.trim();
    this.buffer = '';
    if (rest) { this.dispatch(rest); }
  };

  /**
   * 解析单个事件块并回调 onFrame。
   * @param {string} block 形如 "event: FINAL_SUGGESTION\ndata: {...}"
   */
  SseClient.prototype.dispatch = function (block) {
    var self = this;
    var eventName = 'message';
    var dataLines = [];

    block.split(/\r?\n/).forEach(function (line) {
      if (!line || line.charAt(0) === ':') { return; }   // 空行/心跳注释
      var i = line.indexOf(':');
      var field = i < 0 ? line : line.slice(0, i);
      var value = i < 0 ? '' : line.slice(i + 1).replace(/^ /, '');
      if (field === 'event') {
        eventName = value;
      } else if (field === 'data') {
        dataLines.push(value);
      }
    });

    if (!dataLines.length) { return; }

    var frame;
    try {
      frame = JSON.parse(dataLines.join('\n'));
    } catch (e) {
      return;                                            // 非 JSON 载荷直接跳过
    }
    frame.event = eventName;
    self.resetIdleTimer();
    self.onFrame(frame);
  };

  global.SseClient = SseClient;
})(window);
