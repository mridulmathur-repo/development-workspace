export async function api(path, options = {}, token) {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  })
  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = await response.json()
      message = body.detail || body.message || body.error || message
    } catch { /* keep the status message */ }
    throw new Error(message)
  }
  return response.status === 204 ? null : response.json()
}

