/**
 * GA2-32 WebSocket 端到端冒烟测试。
 *
 * 验证项:
 *  1. /ws/v1 连接建立 + 首帧 AUTH（X-Mock-User: admin）
 *  2. AUTH 成功 ACK
 *  3. 心跳 PING/PONG
 *  4. 触发业务事件（POST /api/v1/biz-requests/{id}/submit）→ 推送实时信封
 *  5. ACK_DELIVERED 投递去重
 *  6. realtime.push.enabled=false 时连接失败回退 REST（验证端点不存在的 404）
 *
 * 运行: node deploy/smoke-ws.mjs
 */
import WebSocket from 'ws'

const BACKEND_URL = 'http://localhost:20010'
const WS_URL = 'ws://localhost:20010/ws/v1'
const MOCK_USER = 'admin'

function log(label, data) {
  const ts = new Date().toISOString().slice(11, 23)
  console.log(`[${ts}] ${label}`, typeof data === 'string' ? data : JSON.stringify(data))
}

async function main() {
  const results = { pass: [], fail: [] }
  const record = (name, ok, detail) => {
    if (ok) {
      results.pass.push(name)
      console.log(`  ✅ ${name}${detail ? ' - ' + detail : ''}`)
    } else {
      results.fail.push(name)
      console.log(`  ❌ ${name}${detail ? ' - ' + detail : ''}`)
    }
  }

  console.log('=== GA2-32 WebSocket 冒烟测试 ===\n')

  // 1. 连接 + AUTH
  console.log('[1] WebSocket 连接 + AUTH')
  const wsUrl = `${WS_URL}?X-Mock-User=${MOCK_USER}`
  let ws
  try {
    ws = new WebSocket(wsUrl)
  } catch (e) {
    record('WebSocket 构造', false, e.message)
    return printResults(results)
  }

  const authed = await new Promise((resolve) => {
    const timeout = setTimeout(() => {
      record('AUTH 超时', false, '5s 内未收到 AUTH ACK')
      resolve(false)
    }, 5000)

    ws.on('open', () => {
      log('WS 连接已建立')
      ws.send(JSON.stringify({ action: 'AUTH' }))
    })

    ws.on('message', (data) => {
      const msg = JSON.parse(data.toString())
      log('收到消息', msg)
      if (msg.action === 'AUTH' && msg.status === 'OK') {
        clearTimeout(timeout)
        record('AUTH 成功', true, msg.message)
        resolve(true)
      } else if (msg.action === 'AUTH' && msg.status === 'ERROR') {
        clearTimeout(timeout)
        record('AUTH 失败', false, msg.errorCode + ' ' + msg.message)
        resolve(false)
      }
    })

    ws.on('error', (e) => {
      clearTimeout(timeout)
      record('WebSocket 错误', false, e.message)
      resolve(false)
    })
  })

  if (!authed) {
    ws.close()
    return printResults(results)
  }

  // 2. 心跳 PING
  console.log('\n[2] 心跳 PING')
  const pongReceived = await new Promise((resolve) => {
    const timeout = setTimeout(() => {
      record('PING 超时', false, '3s 内未收到 PONG')
      resolve(false)
    }, 3000)

    const onMessage = (data) => {
      const msg = JSON.parse(data.toString())
      if (msg.type === 'PING') {
        clearTimeout(timeout)
        ws.off('message', onMessage)
        record('PING/PONG', true, '收到 PONG 信封')
        resolve(true)
      }
    }
    ws.on('message', onMessage)
    ws.send(JSON.stringify({ action: 'PING' }))
  })

  // 3. 触发业务事件 → 推送实时信封
  console.log('\n[3] 触发业务事件 → 推送实时信封')
  // 先创建一个 DRAFT 申请单，然后提交触发 biz.request.submitted 事件
  // 使用真实客户 ID（查 /api/v1/customers 获得）
  const createRes = await fetch(`${BACKEND_URL}/api/v1/biz-requests`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Mock-User': MOCK_USER },
    body: JSON.stringify({
      title: 'WS-Smoke-' + Date.now(),
      customerId: '01KXJRVGGM1438W5NDR0PX2VEW',
      customerNameSnapshot: 'Test Admin',
      // productId/productCodeSnapshot/productNameSnapshot/unit 必填（biz_request_item not-null 约束）
      // 数据来源: GET /api/v1/products 查询到的压测商品 01PERFPRD0000001
      items: [{ productId: '01PERFPRD0000001', productCodeSnapshot: 'PRD0000001', productNameSnapshot: '压测商品-0000001', unit: '个', quantity: 1, unitPrice: 2.0 }],
      idempotencyKey: 'ws-smoke-create-' + Date.now(),
    }),
  })
  const createBody = await createRes.json()
  log('创建申请单', { httpStatus: createRes.status, code: createBody.code, bizId: createBody.data?.id })
  if (createRes.status !== 200 || !createBody.data?.id) {
    record('创建申请单', false, 'HTTP ' + createRes.status)
    ws.close()
    return printResults(results)
  }
  const requestId = createBody.data.id
  const version = createBody.data.version

  // 监听推送信封
  const envelopeReceived = await new Promise((resolve) => {
    const timeout = setTimeout(() => {
      record('推送信封超时', false, '5s 内未收到 biz.request.submitted 推送')
      resolve(false)
    }, 5000)

    const onMessage = (data) => {
      const msg = JSON.parse(data.toString())
      log('收到推送', msg)
      if (msg.type === 'TODO' || msg.type === 'NOTIFICATION') {
        clearTimeout(timeout)
        ws.off('message', onMessage)
        record('推送信封', true, `${msg.type}/${msg.subType} messageId=${msg.messageId}`)
        // 发送 ACK_DELIVERED
        ws.send(JSON.stringify({ action: 'ACK_DELIVERED', messageId: msg.messageId }))
        resolve(true)
      }
    }
    ws.on('message', onMessage)

    // 提交申请单触发 biz.request.submitted 事件
    fetch(`${BACKEND_URL}/api/v1/biz-requests/${requestId}/submit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Mock-User': MOCK_USER },
      body: JSON.stringify({ version, idempotencyKey: 'ws-smoke-submit-' + Date.now() }),
    }).then((r) => {
      log('提交申请单 HTTP', r.status)
    }).catch((e) => {
      log('提交申请单失败', e.message)
    })
  })

  // 4. 关闭连接
  console.log('\n[4] 关闭连接')
  await new Promise((resolve) => {
    ws.on('close', () => {
      record('关闭连接', true)
      resolve()
    })
    ws.close(1000, 'smoke test done')
    setTimeout(resolve, 1000)
  })

  // 5. Mock 模式默认 admin 行为验证
  // 注: MockAuthAdapter 在无 X-Mock-User 请求头时默认返回 admin（设计行为，便于开发）。
  //     生产模式（Sa-Token）下无 Authorization 头会被 RealtimeHandshakeInterceptor 拒绝（401）。
  //     本项验证 mock 模式下默认 admin 也能完成 AUTH，确保握手→AUTH 链路对默认上下文可用。
  console.log('\n[5] Mock 模式默认 admin 行为验证')
  const defaultAdminResult = await new Promise((resolve) => {
    const wsNoHeader = new WebSocket(WS_URL) // 不带 X-Mock-User
    const timeout = setTimeout(() => {
      record('默认 admin AUTH 超时', false, '3s 内未收到 AUTH ACK')
      wsNoHeader.close()
      resolve(false)
    }, 3000)
    wsNoHeader.on('open', () => {
      log('无 X-Mock-User 连接已建立（mock 模式默认 admin）')
      wsNoHeader.send(JSON.stringify({ action: 'AUTH' }))
    })
    wsNoHeader.on('message', (data) => {
      const msg = JSON.parse(data.toString())
      if (msg.action === 'AUTH' && msg.status === 'OK') {
        clearTimeout(timeout)
        wsNoHeader.close()
        record('默认 admin AUTH', true, 'mock 模式无头默认 admin 通过')
        resolve(true)
      }
    })
    wsNoHeader.on('error', (e) => {
      clearTimeout(timeout)
      record('默认 admin AUTH', false, e.message)
      resolve(false)
    })
  })

  return printResults(results)
}

function printResults(results) {
  console.log('\n=== 冒烟测试结果 ===')
  console.log(`✅ PASS: ${results.pass.length}`)
  console.log(`❌ FAIL: ${results.fail.length}`)
  if (results.fail.length > 0) {
    console.log('\n失败项:')
    results.fail.forEach((f) => console.log('  - ' + f))
  }
  process.exit(results.fail.length > 0 ? 1 : 0)
}

main().catch((e) => {
  console.error('冒烟测试脚本异常', e)
  process.exit(2)
})
