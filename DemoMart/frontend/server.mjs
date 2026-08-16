import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const appRoot = path.dirname(fileURLToPath(import.meta.url))
const publicRoot = path.resolve(appRoot, 'dist')
const port = Number(process.env.PORT || 5174)
const gateway = new URL(process.env.GATEWAY_URL || 'http://127.0.0.1:8090')

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp'
}

function proxyToGateway(request, response) {
  const upstream = http.request({
    hostname: gateway.hostname,
    port: gateway.port,
    path: request.url,
    method: request.method,
    headers: { ...request.headers, host: gateway.host }
  }, upstreamResponse => {
    response.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers)
    upstreamResponse.pipe(response)
  })

  upstream.on('error', error => {
    if (!response.headersSent) {
      response.writeHead(502, { 'Content-Type': 'application/json' })
    }
    response.end(JSON.stringify({ error: 'API Gateway unavailable', detail: error.message }))
  })
  request.pipe(upstream)
}

function sendFile(request, response, filePath) {
  const extension = path.extname(filePath).toLowerCase()
  response.writeHead(200, {
    'Content-Type': contentTypes[extension] || 'application/octet-stream',
    'Cache-Control': extension === '.html' ? 'no-cache' : 'public, max-age=86400'
  })
  if (request.method === 'HEAD') {
    response.end()
    return
  }
  fs.createReadStream(filePath).pipe(response)
}

const server = http.createServer((request, response) => {
  const requestUrl = new URL(request.url || '/', `http://${request.headers.host || 'localhost'}`)

  if (requestUrl.pathname === '/health') {
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify({ status: 'UP' }))
    return
  }

  if (requestUrl.pathname.startsWith('/api/')) {
    proxyToGateway(request, response)
    return
  }

  if (!['GET', 'HEAD'].includes(request.method || 'GET')) {
    response.writeHead(405).end()
    return
  }

  let relativePath
  try {
    relativePath = decodeURIComponent(requestUrl.pathname === '/' ? 'index.html' : requestUrl.pathname.slice(1))
  } catch {
    response.writeHead(400).end('Invalid URL')
    return
  }

  const requestedFile = path.resolve(publicRoot, relativePath)
  const insidePublicRoot = requestedFile === publicRoot || requestedFile.startsWith(`${publicRoot}${path.sep}`)
  if (!insidePublicRoot) {
    response.writeHead(403).end('Forbidden')
    return
  }

  fs.stat(requestedFile, (error, stats) => {
    if (!error && stats.isFile()) {
      sendFile(request, response, requestedFile)
      return
    }
    sendFile(request, response, path.join(publicRoot, 'index.html'))
  })
})

server.listen(port, '0.0.0.0', () => {
  console.log(`DemoMart frontend listening on http://0.0.0.0:${port}`)
})

function shutdown() {
  server.close(() => process.exit(0))
  setTimeout(() => process.exit(1), 5000).unref()
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
