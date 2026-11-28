import http from 'node:http'

const port = Number(process.env.PORT || 8080)
const answer = '这是来自真实服务链路的测试回答。'
const injectedFailures = new Map()

function shouldFail(messages) {
  const content = (messages || []).map((message) => message.content || '').join('\n')
  const match = content.match(/\[RELEASE_FAIL:([^\]:]+):(\d+)\]/)
  if (!match) return false
  const [, token, configured] = match
  const remaining = injectedFailures.get(token) ?? Number(configured)
  if (remaining <= 0) {
    injectedFailures.delete(token)
    return false
  }
  injectedFailures.set(token, remaining - 1)
  return true
}

function json(response, status, body) {
  response.writeHead(status, { 'content-type': 'application/json' })
  response.end(JSON.stringify(body))
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let body = ''
    request.on('data', (chunk) => {
      body += chunk
    })
    request.on('end', () => resolve(body))
    request.on('error', reject)
  })
}

function completionFor(messages) {
  const system = (messages || [])
    .filter((message) => message.role === 'system')
    .map((message) => message.content || '')
    .join('\n')
  if (system.includes('简历解析助手')) {
    return JSON.stringify({
      skills: ['Java', 'Spring Boot', 'MySQL'],
      projects: [{ name: '交易系统', description: '负责订单与库存一致性' }],
    })
  }
  if (system.includes('score') && system.includes('hint')) {
    return JSON.stringify({ score: 8, hint: '测试提示：补充量化结果。' })
  }
  if (system.includes('提取 1 到 5 个')) {
    return '[]'
  }
  if (system.includes('reportMarkdown') && system.includes('trainingPlan')) {
    return JSON.stringify({
      summary: {
        fitAssessment: '具备继续训练的基础',
        actionRecommendation: '继续投递并加强项目量化',
        overallRisk: '项目细节仍需补充',
      },
      scores: { technical: 8, expression: 8, logic: 8 },
      stagePerformances: [],
      strengths: ['沟通清晰'],
      trainingPlan: { threeDay: [], sevenDay: [], nextInterviewFocus: [] },
      finalAdvice: '继续练习结构化表达。',
      reportMarkdown: '技术能力：8/10\n表达清晰度：8/10\n逻辑思维：8/10',
    })
  }
  return answer
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url || '/', `http://${request.headers.host || 'localhost'}`)
  if (request.method === 'GET' && url.pathname === '/v1/models') {
    return json(response, 200, {
      object: 'list',
      data: [{ id: 'release-test-model', object: 'model', created: 0, owned_by: 'prelude' }],
    })
  }
  if (request.method === 'POST' && url.pathname === '/v1/embeddings') {
    const body = JSON.parse(await readBody(request))
    const input = Array.isArray(body.input) ? body.input : [body.input]
    return json(response, 200, {
      object: 'list',
      model: body.model || 'release-test-embedding',
      data: input.map((_, index) => ({
        object: 'embedding',
        index,
        embedding: [0.1, 0.2, 0.3, 0.4],
      })),
      usage: { prompt_tokens: input.length, total_tokens: input.length },
    })
  }
  if (request.method === 'POST' && url.pathname === '/v1/chat/completions') {
    const body = JSON.parse(await readBody(request))
    if (shouldFail(body.messages)) {
      return json(response, 500, { error: { message: 'Injected release failure', type: 'server_error' } })
    }
    const content = completionFor(body.messages)
    if (body.stream) {
      response.writeHead(200, {
        'content-type': 'text/event-stream',
        'cache-control': 'no-cache',
        connection: 'keep-alive',
      })
      const chunk = {
        id: 'chatcmpl-release',
        object: 'chat.completion.chunk',
        created: Math.floor(Date.now() / 1000),
        model: body.model || 'release-test-model',
        choices: [{ index: 0, delta: { role: 'assistant', content }, finish_reason: null }],
      }
      response.write(`data: ${JSON.stringify(chunk)}\n\n`)
      response.write(`data: ${JSON.stringify({ ...chunk, choices: [{ index: 0, delta: {}, finish_reason: 'stop' }] })}\n\n`)
      response.end('data: [DONE]\n\n')
      return
    }
    return json(response, 200, {
      id: 'chatcmpl-release',
      object: 'chat.completion',
      created: Math.floor(Date.now() / 1000),
      model: body.model || 'release-test-model',
      choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 },
    })
  }
  json(response, 404, { error: { message: `Unhandled ${request.method} ${url.pathname}` } })
})

server.listen(port, '0.0.0.0', () => {
  console.log(`release LLM stub listening on ${port}`)
})
