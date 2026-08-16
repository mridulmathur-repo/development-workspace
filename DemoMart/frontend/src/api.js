export async function api(path, options = {}, token) {
  // BASE_URL is ./ so API calls follow the page location:
  // /api locally and /demomart/api when Nginx hosts the UI below /demomart/.
  const response = await fetch(`${import.meta.env.BASE_URL}api${path}`, {
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

