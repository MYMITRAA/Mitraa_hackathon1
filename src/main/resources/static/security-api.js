let mitraaCsrfToken = '';
let mitraaCsrfHeader = 'X-XSRF-TOKEN';

const hiddenStyle = document.createElement('style');
hiddenStyle.textContent = '[hidden]{display:none!important}';
document.head.appendChild(hiddenStyle);

function mitraaChangesState(method) {
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes((method || 'GET').toUpperCase());
}

async function mitraaRefreshCsrf() {
  const response = await fetch('/api/auth/csrf', {credentials: 'same-origin', cache: 'no-store'});
  if (!response.ok) {
    throw new Error(response.status === 401
      ? 'Your session expired. Please sign in again.'
      : `Security token request failed (${response.status})`);
  }
  const data = await response.json();
  mitraaCsrfToken = data.token || '';
  mitraaCsrfHeader = data.headerName || 'X-XSRF-TOKEN';
}

async function api(url, options = {}, retried = false) {
  const method = (options.method || 'GET').toUpperCase();
  if (mitraaChangesState(method) && !mitraaCsrfToken) await mitraaRefreshCsrf();
  const headers = {
    ...(options.body ? {'Content-Type': 'application/json'} : {}),
    ...(mitraaChangesState(method) && mitraaCsrfToken ? {[mitraaCsrfHeader]: mitraaCsrfToken} : {}),
    ...(options.headers || {})
  };
  const response = await fetch(url, {...options, method, headers, credentials: 'same-origin', cache: 'no-store'});
  if (response.status === 403 && mitraaChangesState(method) && !retried) {
    await mitraaRefreshCsrf();
    return api(url, options, true);
  }
  let data = {};
  try { data = await response.json(); } catch (_) { }
  if (!response.ok) {
    if (response.status === 401) throw new Error('Your session expired. Please sign in again.');
    if (response.status === 403) throw new Error('Security validation failed. Refresh the page and sign in again.');
    throw new Error(data.message || data.status || `Request failed (${response.status})`);
  }
  return data;
}
