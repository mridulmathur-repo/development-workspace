import { useEffect, useMemo, useRef, useState } from 'react'

const STAGES = [
  ['BACKLOG', 'Backlog'],
  ['READY_DEVELOPMENT', 'Ready for development'],
  ['DEVELOPMENT', 'Development'],
  ['TESTING', 'Testing'],
  ['STAGING', 'Staging'],
  ['HUMAN_REVIEW', 'Human review'],
  ['COMPLETED', 'Completed'],
]

const emptyDashboard = {
  counts: {}, stageCounts: {}, issues: [], executions: [], integrations: [],
}

async function request(path, options = {}) {
  const response = await fetch(path, options)
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || `Request failed with HTTP ${response.status}`)
  return body
}

function formatMinutes(value = 0) {
  if (value < 60) return `${value}m`
  if (value < 1440) return `${Math.floor(value / 60)}h ${value % 60}m`
  return `${Math.floor(value / 1440)}d ${Math.floor((value % 1440) / 60)}h`
}

function shortCommit(value) {
  return value ? value.slice(0, 8) : '—'
}

function App() {
  const [dashboard, setDashboard] = useState(emptyDashboard)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [typeFilter, setTypeFilter] = useState('ALL')
  const [view, setView] = useState('board')
  const [selected, setSelected] = useState(null)
  const [token, setToken] = useState(() => sessionStorage.getItem('automation-token') || '')
  const [tokenDraft, setTokenDraft] = useState('')
  const [unlocked, setUnlocked] = useState(Boolean(sessionStorage.getItem('automation-token')))
  const [epicKey, setEpicKey] = useState('DMT-1')
  const [actionMessage, setActionMessage] = useState('')
  const [pipelineStatuses, setPipelineStatuses] = useState([])
  const lastPipelineNotice = useRef('')

  const refresh = async (silent = false) => {
    if (!silent) setLoading(true)
    try {
      const result = await request('/api/dashboard')
      setDashboard(result)
      setError('')
    } catch (exception) {
      setError(exception.message)
    } finally {
      if (!silent) setLoading(false)
    }
  }

  useEffect(() => {
    refresh()
    const timer = window.setInterval(() => refresh(true), 30000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (!unlocked || !token) return undefined
    const loadStatuses = async () => {
      try {
        const statuses = await request('/api/automation/pipeline-status', {
          headers: { 'X-Automation-Control-Token': token },
        })
        setPipelineStatuses(statuses)
        const latest = statuses[0]
        if (latest && latest.updatedAt !== lastPipelineNotice.current && latest.state !== 'RUNNING') {
          lastPipelineNotice.current = latest.updatedAt
          setActionMessage(`${latest.epicKey}: ${latest.message}`)
          await refresh(true)
        }
      } catch (exception) {
        setActionMessage(exception.message)
      }
    }
    loadStatuses()
    const timer = window.setInterval(loadStatuses, 10000)
    return () => window.clearInterval(timer)
  }, [unlocked, token])

  const filteredIssues = useMemo(() => dashboard.issues.filter((issue) => {
    const matchesType = typeFilter === 'ALL' || issue.issueType.toUpperCase() === typeFilter
    const haystack = `${issue.key} ${issue.summary} ${issue.status} ${issue.assignee}`.toLowerCase()
    return matchesType && haystack.includes(query.toLowerCase())
  }), [dashboard.issues, query, typeFilter])

  const attention = useMemo(() => [
    ...dashboard.issues.filter((issue) => issue.stuck).map((issue) => ({
      id: issue.key, title: `${issue.key} · ${issue.summary}`, detail: issue.stuckReason,
      badge: issue.status, kind: 'issue', data: issue,
    })),
    ...dashboard.executions.filter((item) => item.status === 'HUMAN_REVIEW_REQUIRED').map((item) => ({
      id: item.id, title: `${item.epicKey} · ${item.epicSummary}`,
      detail: item.lastFailureSummary || 'Human gatekeeper decision required',
      badge: item.lastFailureCategory, kind: 'execution', data: item,
    })),
  ], [dashboard])

  const unlock = async (event) => {
    event.preventDefault()
    if (!tokenDraft.trim()) return
    const candidate = tokenDraft.trim()
    const response = await fetch('/api/automation/executions/token-check', {
      headers: { 'X-Automation-Control-Token': candidate },
    })
    if (response.status === 403) {
      setActionMessage('Control token was rejected.')
      return
    }
    sessionStorage.setItem('automation-token', candidate)
    setToken(candidate)
    setTokenDraft('')
    setUnlocked(true)
    setActionMessage('Operator controls unlocked for this browser tab.')
  }

  const lock = () => {
    sessionStorage.removeItem('automation-token')
    setToken('')
    setUnlocked(false)
    setActionMessage('Operator controls locked.')
  }

  const automationAction = async (path, body) => {
    try {
      const result = await request(path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Automation-Control-Token': token,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
      setActionMessage(`Action completed for ${result.epicKey || result.id}.`)
      await refresh(true)
      setSelected(result)
    } catch (exception) {
      setActionMessage(exception.message)
    }
  }

  const startEpic = async (event) => {
    event.preventDefault()
    const key = epicKey.trim().toUpperCase()
    if (!key || !window.confirm(`Create or resume the automation execution for ${key}?`)) return
    await automationAction(`/api/automation/epics/${encodeURIComponent(key)}/start`)
  }

  const resume = async (execution, phase) => {
    const instructions = window.prompt(`Instructions for resuming ${execution.epicKey} from ${phase}:`, '')
    if (instructions === null) return
    await automationAction(`/api/automation/executions/${execution.id}/resume`, { phase, instructions })
  }

  const cancel = async (execution) => {
    const instructions = window.prompt(`Reason for cancelling ${execution.epicKey}:`, '')
    if (instructions === null) return
    await automationAction(`/api/automation/executions/${execution.id}/cancel`, { instructions })
  }

  const triggerDevelopment = async (execution) => {
    const result = await request(`/api/automation/executions/${execution.id}/trigger-development`, {
      method: 'POST', headers: { 'X-Automation-Control-Token': token },
    }).catch((exception) => ({ message: exception.message }))
    setActionMessage(`${execution.epicKey}: ${result.message}`)
    await refresh(true)
  }

  const jiraLink = (key) => dashboard.jiraBaseUrl && key
    ? `${dashboard.jiraBaseUrl.replace(/\/$/, '')}/browse/${key}` : '#'

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand-block">
          <div className="brand-mark">AI</div>
          <div>
            <p className="eyebrow">Delivery intelligence</p>
            <h1>Jira AI Control</h1>
          </div>
        </div>
        <div className="top-actions">
          <span className={`live-pill ${error ? 'danger' : ''}`}><i />{error ? 'Data issue' : 'Live'}</span>
          <span className="timestamp">Updated {dashboard.generatedAt ? new Date(dashboard.generatedAt).toLocaleTimeString() : '—'}</span>
          <button className="icon-button" onClick={() => refresh()} aria-label="Refresh dashboard">↻</button>
        </div>
      </header>

      <main>
        <section className="hero-row">
          <div>
            <p className="eyebrow">{dashboard.jiraProjectKey || 'Project'} delivery command centre</p>
            <h2>See the work. Clear the blockers.</h2>
            <p className="hero-copy">Stories, tasks, agent executions and human gates in one operational view.</p>
          </div>
          <div className="health-strip">
            {dashboard.integrations.map((item) => (
              <div className="health-item" key={item.name} title={item.detail}>
                <span className={`health-dot ${item.state.toLowerCase()}`} />
                <span>{item.name}</span>
                <strong>{item.state}</strong>
              </div>
            ))}
          </div>
        </section>

        {error && <div className="alert error-alert">{error}</div>}
        {actionMessage && <div className="alert action-alert">{actionMessage}<button onClick={() => setActionMessage('')}>×</button></div>}

        <section className="metric-grid">
          <Metric label="User stories" value={dashboard.counts.stories || 0} tone="blue" note="Tracked in Jira" />
          <Metric label="Delivery tasks" value={dashboard.counts.tasks || 0} tone="violet" note="Development + test" />
          <Metric label="In development" value={dashboard.stageCounts.DEVELOPMENT || 0} tone="cyan" note="Work in progress" />
          <Metric label="In testing" value={dashboard.stageCounts.TESTING || 0} tone="amber" note="Independent checks" />
          <Metric label="Needs attention" value={(dashboard.counts.stuck || 0) + (dashboard.counts.humanReview || 0)} tone="red" note={`Idle ≥ ${dashboard.stuckAfterMinutes || 60}m`} />
          <Metric label="Completed" value={dashboard.stageCounts.COMPLETED || 0} tone="green" note="Jira status category" />
        </section>

        <section className="workspace-grid">
          <div className="primary-panel panel">
            <div className="panel-head controls-head">
              <div>
                <p className="eyebrow">Flow tracker</p>
                <h3>Delivery workspace</h3>
              </div>
              <div className="view-tabs">
                {['board', 'tracker', 'executions'].map((name) => (
                  <button key={name} className={view === name ? 'active' : ''} onClick={() => setView(name)}>
                    {name[0].toUpperCase() + name.slice(1)}
                  </button>
                ))}
              </div>
            </div>

            {view !== 'executions' && (
              <div className="filterbar">
                <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search key, summary, status or owner…" />
                <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
                  <option value="ALL">Stories + Tasks</option>
                  <option value="STORY">Stories only</option>
                  <option value="TASK">Tasks only</option>
                </select>
              </div>
            )}

            {loading ? <Loading /> : view === 'board'
              ? <Board issues={filteredIssues} onSelect={setSelected} />
              : view === 'tracker'
                ? <Tracker issues={filteredIssues} onSelect={setSelected} jiraLink={jiraLink} />
                : <Executions executions={dashboard.executions} pipelineStatuses={pipelineStatuses} unlocked={unlocked} onSelect={setSelected} onResume={resume} onCancel={cancel} onTrigger={triggerDevelopment} />}
          </div>

          <aside className="side-stack">
            <section className="panel attention-panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">Gatekeeper inbox</p>
                  <h3>Human attention</h3>
                </div>
                <span className="count-badge">{attention.length}</span>
              </div>
              <div className="attention-list">
                {attention.length === 0 && <Empty text="No work currently needs intervention." />}
                {attention.slice(0, 8).map((item) => (
                  <button key={`${item.kind}-${item.id}`} className="attention-card" onClick={() => setSelected(item.data)}>
                    <span className="attention-icon">!</span>
                    <span><strong>{item.title}</strong><small>{item.detail}</small></span>
                    <em>{item.badge}</em>
                  </button>
                ))}
              </div>
            </section>

            <section className="panel operator-panel">
              <div className="panel-head">
                <div><p className="eyebrow">Protected actions</p><h3>Operator controls</h3></div>
                <span className={`lock-state ${unlocked ? 'unlocked' : ''}`}>{unlocked ? 'Unlocked' : 'Locked'}</span>
              </div>
              {!unlocked ? (
                <form onSubmit={unlock} className="operator-form">
                  <label>Automation control token</label>
                  <input type="password" value={tokenDraft} onChange={(event) => setTokenDraft(event.target.value)} placeholder="Enter token for this tab" autoComplete="off" />
                  <button className="primary-button" type="submit">Unlock controls</button>
                  <small>The token stays in this browser tab and is never bundled into the UI.</small>
                </form>
              ) : (
                <form onSubmit={startEpic} className="operator-form">
                  <label>Start or open Epic execution</label>
                  <div className="inline-form"><input value={epicKey} onChange={(event) => setEpicKey(event.target.value)} /><button className="primary-button">Start</button></div>
                  <button type="button" className="text-button" onClick={lock}>Lock operator controls</button>
                </form>
              )}
            </section>
          </aside>
        </section>
      </main>

      {selected && <DetailDrawer item={selected} onClose={() => setSelected(null)} jiraLink={jiraLink} />}
    </div>
  )
}

function Metric({ label, value, tone, note }) {
  return <article className={`metric-card ${tone}`}><span>{label}</span><strong>{value}</strong><small>{note}</small></article>
}

function Board({ issues, onSelect }) {
  return <div className="board-scroll"><div className="board">
    {STAGES.map(([stage, label]) => {
      const items = issues.filter((issue) => issue.stage === stage)
      return <div className="board-column" key={stage}>
        <div className="column-head"><span className={`stage-dot ${stage.toLowerCase()}`} /> <strong>{label}</strong><em>{items.length}</em></div>
        <div className="column-body">
          {items.length === 0 && <div className="column-empty">No items</div>}
          {items.map((issue) => <button key={issue.key} className={`work-card ${issue.stuck ? 'stuck' : ''}`} onClick={() => onSelect(issue)}>
            <div><span className="type-tag">{issue.issueType}</span>{issue.stuck && <span className="risk-tag">Stuck</span>}</div>
            <strong>{issue.key}</strong><p>{issue.summary}</p>
            <footer><span>{issue.assignee || 'Unassigned'}</span><span>{formatMinutes(issue.idleMinutes)} idle</span></footer>
          </button>)}
        </div>
      </div>
    })}
  </div></div>
}

function Tracker({ issues, onSelect, jiraLink }) {
  return <div className="table-wrap"><table><thead><tr><th>Key</th><th>Type</th><th>Summary</th><th>Status</th><th>Owner</th><th>Idle</th></tr></thead>
    <tbody>{issues.map((issue) => <tr key={issue.key} className={issue.stuck ? 'stuck-row' : ''} onClick={() => onSelect(issue)}>
      <td><a href={jiraLink(issue.key)} target="_blank" rel="noreferrer" onClick={(event) => event.stopPropagation()}>{issue.key}</a></td>
      <td><span className="type-tag">{issue.issueType}</span></td><td>{issue.summary}</td><td><span className={`status-chip ${issue.stage.toLowerCase()}`}>{issue.status}</span></td>
      <td>{issue.assignee || 'Unassigned'}</td><td>{formatMinutes(issue.idleMinutes)}{issue.stuck && <b className="warning-dot">!</b>}</td>
    </tr>)}</tbody></table>{issues.length === 0 && <Empty text="No Stories or Tasks match this filter." />}</div>
}

function Executions({ executions, pipelineStatuses, unlocked, onSelect, onResume, onCancel, onTrigger }) {
  if (executions.length === 0) return <Empty text="No MVP2 automation execution has started yet." />
  return <div className="execution-list">{executions.map((item) => {
    const pipeline = pipelineStatuses.find((status) => status.executionId === item.id)
    return <article className="execution-card" key={item.id}>
    <button className="execution-main" onClick={() => onSelect(item)}>
      <span className={`status-orb ${item.status.toLowerCase()}`} />
      <span><strong>{item.epicKey} · {item.epicSummary}</strong><small>{item.branchName}</small></span>
      <span className="phase-block"><b>{item.phase.replaceAll('_', ' ')}</b><small>commit {shortCommit(item.branchHeadCommit)}</small></span>
      <span className="attempt-block">{item.developmentAttempts} dev / {item.testingAttempts} test</span>
    </button>
    {pipeline && <div className="execution-actions"><span>{pipeline.message}</span></div>}
    {unlocked && item.status === 'ACTIVE' && item.phase === 'DEVELOPMENT' && <div className="execution-actions">
      <button onClick={() => onTrigger(item)} disabled={pipeline?.state === 'RUNNING'}>
        {pipeline?.state === 'RUNNING' ? 'Development running…' : 'Start next ready Story'}
      </button>
    </div>}
    {unlocked && item.status === 'HUMAN_REVIEW_REQUIRED' && <div className="execution-actions">
      <button onClick={() => onResume(item, 'DEVELOPMENT')}>Resume development</button>
      <button onClick={() => onResume(item, 'TESTING')}>Resume testing</button>
      <button className="danger-button" onClick={() => onCancel(item)}>Cancel</button>
    </div>}
  </article>})}</div>
}

function DetailDrawer({ item, onClose, jiraLink }) {
  const isExecution = Boolean(item.epicKey)
  return <div className="drawer-backdrop" onMouseDown={onClose}><aside className="drawer" onMouseDown={(event) => event.stopPropagation()}>
    <button className="drawer-close" onClick={onClose}>×</button>
    <p className="eyebrow">{isExecution ? 'Automation execution' : item.issueType}</p>
    <h3>{isExecution ? item.epicKey : item.key}</h3><h4>{isExecution ? item.epicSummary : item.summary}</h4>
    <div className="detail-grid">
      <Detail label="Status" value={isExecution ? item.status : item.status} />
      <Detail label={isExecution ? 'Phase' : 'Stage'} value={isExecution ? item.phase : item.stage} />
      <Detail label={isExecution ? 'Branch' : 'Parent'} value={isExecution ? item.branchName : item.parentKey || '—'} />
      <Detail label={isExecution ? 'Branch commit' : 'Assignee'} value={isExecution ? shortCommit(item.branchHeadCommit) : item.assignee || 'Unassigned'} />
      {isExecution ? <><Detail label="Development attempts" value={item.developmentAttempts} /><Detail label="Testing attempts" value={item.testingAttempts} /></>
        : <><Detail label="Idle time" value={formatMinutes(item.idleMinutes)} /><Detail label="Risk" value={item.stuck ? item.stuckReason : 'On track'} /></>}
    </div>
    {isExecution && item.lastFailureSummary && <div className="failure-box"><strong>{item.lastFailureCategory}</strong><p>{item.lastFailureSummary}</p><small>{item.lastSuggestedAction}</small></div>}
    {!isExecution && <a className="primary-button link-button" href={jiraLink(item.key)} target="_blank" rel="noreferrer">Open in Jira ↗</a>}
  </aside></div>
}

function Detail({ label, value }) { return <div><span>{label}</span><strong>{String(value ?? '—').replaceAll('_', ' ')}</strong></div> }
function Empty({ text }) { return <div className="empty-state"><span>◇</span><p>{text}</p></div> }
function Loading() { return <div className="loading-state"><i /><span>Loading delivery data…</span></div> }

export default App
