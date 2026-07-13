# 前端流式稳定性重构证据 2026-06-05

> 本文件记录系统前端核心面试链路（InterviewView）的稳定性重构（Phase 1 至 Phase 3），用于支撑论文中的“性能优化与稳定性设计”章节。

---

## 证据 13：高频流式渲染防抖与前端状态机死锁解除 (rAF Throttling & Mutex)

来源文件：
- `frontend/src/views/InterviewView.vue`
- `frontend/src/composables/useVoiceMedia.ts`

关键实现点：
- **rAF 缓冲渲染**：针对 LLM 打字机高频 chunk 推送（SSE）导致的 Vue 3 响应式更新压力，引入基于 `requestAnimationFrame` (rAF) 的非响应式缓冲池（Chunk Buffer），将高频 DOM 更新降频并对齐浏览器重绘节奏，降低长文本渲染的掉帧卡顿风险。
- **并发截断互斥锁 (Mutex)**：在状态机底层实施严格的并发管理。当用户主动切断或推进流程（如点击“生成报告”）时，主动执行 `abortActiveStream()`，从网络请求的 `AbortController` 级别斩断底层的连接和定时器，实现了网络通道层面的物理互斥，防止不同 Promise 并发污染局部状态树。
- **职责剥离与无死角重构**：将冗杂的媒体流 API（MediaRecorder、AudioContext、兼容性嗅探）从视图主控板中剥离至专用的 `useVoiceMedia.ts` Hook，消除了多余的纯 UI 死代码，极大降低了心智负担。

可引用代码位置：
```text
InterviewView.vue L222-261 (rAF buffer 节流与刷新机制)
InterviewView.vue L507 (handleFinish 底层网络流并发互斥锁)
useVoiceMedia.ts L1-71 (音视频底层纯逻辑的 Composable 剥离)
```

论文可用表述：
为解决大模型流式通信（SSE）极高频切片带来的浏览器微任务队列拥塞问题，本文系统在前端应用层重构了打字机渲染引擎，引入了基于 `requestAnimationFrame` 的缓冲控制机制，将高密度的网络 IO 响应节流至设备屏幕安全帧率内，保障了富文本输出的丝滑度。此外，针对异构异步任务中的状态锁死问题，系统重新设计了网络生命周期级别的互斥机制，通过全局 `AbortController` 拦截异常状态流转，强行熔断并回收僵尸连接资源，极大提升了核心面试引擎在极端并发压力下的系统级稳健性。

---

## 证据 14：大模型上下文截断保护与断点续传容灾机制 (Sliding Window & Snapshot)

来源文件：
- `frontend/src/views/InterviewView.vue`
- `frontend/src/api/contracts.ts`

关键实现点：
- **Token 滑动窗口保护 (Sliding Window)**：在组装大模型对话请求负载时，引入前端上下文动态截断机制。该机制始终将系统的 `system prompt` 作为前置“思想钢印”硬性保留，并从对话尾部回溯截取固定阈值（如最近 20 轮）的对话切片。通过前端主动“斩尾”，防止超长深度面试拖垮后端 API 乃至触发模型窗口上限（Token Limit Exceeded）。
- **流式中途离线快照 (Offline Snapshot)**：通过挂载 3 秒阈值的防抖写操作（Throttle），将正在进行的 AI 流式输出中途态悄然同步至浏览器 `sessionStorage` 缓存。通过极低频率的 I/O 避免阻塞主线程。
- **劫持与复苏机制**：针对用户端的极端异常（如意外断网、进程被杀、浏览器强制刷新），系统在路由核心入口 `loadDashboard` 构建了快照嗅探探针。在拉取服务端持久化数据的同时合并本地残留的 `snapshot`，辅以 `window.confirm` 进行显式挂载合并，并在生命周期终点执行严密的物理清除（物理删除 `removeItem`）。

可引用代码位置：
```text
InterviewView.vue L192-220 (Token 滑动截断与防抖离线快照引擎)
InterviewView.vue L95-119 (loadDashboard 快照嗅探探针与断点恢复)
```

论文可用表述：
为规避极多轮次深度追问场景下大模型上下文（Context Window）过载导致的服务端异常风险，本系统在客户端代理层部署了安全截断算法。该算法通过双指针维持系统指令的优先保留，并对历史栈实施滑动窗口裁切，从流量源头控制 Token 成本。在前端数据容灾维度，系统架构内嵌了低开销的流式离线快照（Snapshot）降频器，可利用浏览器本地缓存存储会话中间态。结合组件初始化周期的探针自检与异常重载逻辑，该机制减少了异步持久化入库前断网引起的数据空白风险。
