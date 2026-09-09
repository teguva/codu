<script>
  let health = $state(null);
  let healthError = $state('');
  let items = $state([]);
  let selected = $state(null);
  let scanning = $state(false);
  let scanResult = $state(null);
  let token = $state(localStorage.getItem('coog-token') || '');
  let loadError = $state('');

  const headers = () => {
    const h = { Accept: 'application/json' };
    if (token) h.Authorization = `Bearer ${token}`;
    return h;
  };

  async function refreshHealth() {
    try {
      const res = await fetch('/health');
      health = await res.json();
      healthError = '';
    } catch (err) {
      health = null;
      healthError = String(err);
    }
  }

  async function refreshLibrary() {
    loadError = '';
    try {
      const res = await fetch('/api/v1/library', { headers: headers() });
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
      const data = await res.json();
      items = data.items || [];
    } catch (err) {
      loadError = String(err);
    }
  }

  async function rescan() {
    scanning = true;
    loadError = '';
    try {
      const res = await fetch('/api/v1/library/rescan', { method: 'POST', headers: headers() });
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
      scanResult = await res.json();
      await refreshLibrary();
    } catch (err) {
      loadError = String(err);
    } finally {
      scanning = false;
    }
  }

  async function openItem(id) {
    const res = await fetch(`/api/v1/library/${id}`, { headers: headers() });
    selected = await res.json();
  }

  function saveToken() {
    localStorage.setItem('coog-token', token);
    refreshLibrary();
  }

  $effect(() => {
    refreshHealth();
    refreshLibrary();
  });
</script>

<div class="app">
  <header>
    <div>
      <h1>Coog</h1>
      <p class="sub">Admin — library, jobs, server. Not the living-room client.</p>
    </div>
    <div class="health">
      <span class="dot" class:ok={health?.status === 'ok'} class:bad={!!healthError}></span>
      {#if health}
        <span>{health.status} · {health.version}</span>
      {:else}
        <span>{healthError || 'checking…'}</span>
      {/if}
    </div>
  </header>

  <div class="toolbar">
    <label>
      Token
      <input bind:value={token} placeholder="COOG_AUTH_TOKEN" onchange={saveToken} />
    </label>
    <button onclick={rescan} disabled={scanning}>{scanning ? 'Scanning…' : 'Rescan library'}</button>
    <button onclick={refreshLibrary}>Refresh</button>
    {#if scanResult}
      <span class="muted">indexed {scanResult.indexed}, probed {scanResult.probed}, removed {scanResult.removed}</span>
    {/if}
  </div>

  {#if loadError}
    <p class="error">{loadError}</p>
  {/if}

  <table>
    <thead>
      <tr>
        <th>Title</th>
        <th>Kind</th>
        <th>Codec</th>
        <th>Resolution</th>
        <th>Duration</th>
      </tr>
    </thead>
    <tbody>
      {#each items as item}
        <tr class="clickable" onclick={() => openItem(item.id)}>
          <td>{item.showTitle ? `${item.showTitle} — ${item.title}` : item.title}</td>
          <td>{item.kind}</td>
          <td>{item.codecVideo || '—'} {item.hdr ? `· ${item.hdr}` : ''}</td>
          <td>{item.width && item.height ? `${item.width}×${item.height}` : '—'}</td>
          <td>{item.durationMs ? Math.round(item.durationMs / 1000) + 's' : '—'}</td>
        </tr>
      {:else}
        <tr><td colspan="5" class="muted">No items. Point COOG_LIBRARY_PATH at Videos/Movies + Videos/Series and rescan.</td></tr>
      {/each}
    </tbody>
  </table>

  {#if selected}
    <section class="detail">
      <h2>{selected.title}</h2>
      <p class="muted"><code>{selected.path}</code></p>
      <p>video {selected.codecVideo || '—'} · audio {selected.codecAudio || '—'} · {selected.contentType}</p>
      <p><a href={`/api/v1/media/${selected.id}/stream`}>stream</a></p>
      <pre>{JSON.stringify(selected.probe, null, 2)}</pre>
    </section>
  {/if}
</div>
