<script>
  const NAV = [
    { id: 'overview', label: 'Overview' },
    { id: 'activity', label: 'Activity' },
    { id: 'downloads', label: 'Downloads' },
    { id: 'library', label: 'Library' },
    { id: 'streaming', label: 'Streaming' },
  ];
  const PROVIDERS = [
    'yts', 'eztv', 'rarbg', '1337x', 'thepiratebay', 'kickasstorrents',
    'torrentgalaxy', 'magnetdl', 'rutor', 'rutracker', 'nyaasi', 'limetorrents',
  ];
  const QUALITIES = ['threed', 'cam', 'scr', '480p', '720p', '1080p', '4k', 'hdr', 'dolbyvision'];

  let page = $state(localStorage.getItem('coog-admin-page') || 'overview');
  let token = $state(localStorage.getItem('coog-token') || '');
  let health = $state(null);
  let healthError = $state('');
  let stats = $state(null);
  let statsError = $state('');
  let activity = $state([]);
  let jobs = $state([]);
  let jobUrl = $state('');
  let jobBusy = $state(false);
  let jobError = $state('');
  let expanded = $state({});
  let items = $state([]);
  let selected = $state(null);
  let scanning = $state(false);
  let scanResult = $state(null);
  let loadError = $state('');
  let libQuery = $state('');
  let libKind = $state('all');
  let streaming = $state(null);
  let rdToken = $state('');
  let streamBusy = $state(false);
  let streamError = $state('');

  const headers = () => {
    const h = { Accept: 'application/json' };
    if (token) h.Authorization = `Bearer ${token}`;
    return h;
  };

  function go(id) {
    page = id;
    localStorage.setItem('coog-admin-page', id);
  }

  function saveToken() {
    localStorage.setItem('coog-token', token);
    refreshAll();
  }

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

  async function refreshStats() {
    try {
      const res = await fetch('/api/v1/server/stats', { headers: headers() });
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
      stats = await res.json();
      statsError = '';
    } catch (err) {
      statsError = String(err);
    }
  }

  async function refreshActivity() {
    try {
      const res = await fetch('/api/v1/server/activity', { headers: headers() });
      if (!res.ok) throw new Error(`${res.status}`);
      const data = await res.json();
      activity = data.items || [];
    } catch {
      /* keep last */
    }
  }

  async function refreshJobs() {
    try {
      const res = await fetch('/api/v1/jobs', { headers: headers() });
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
      const data = await res.json();
      jobs = data.items || [];
      jobError = '';
    } catch (err) {
      jobError = String(err);
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

  async function refreshStreaming() {
    try {
      const res = await fetch('/api/v1/settings/streaming', { headers: headers() });
      if (!res.ok) throw new Error(`${res.status}`);
      streaming = await res.json();
      streamError = '';
    } catch (err) {
      streamError = String(err);
    }
  }

  async function refreshAll() {
    await Promise.all([
      refreshHealth(),
      refreshStats(),
      refreshActivity(),
      refreshJobs(),
      refreshLibrary(),
      refreshStreaming(),
    ]);
  }

  async function rescan() {
    scanning = true;
    loadError = '';
    try {
      const res = await fetch('/api/v1/library/rescan', { method: 'POST', headers: headers() });
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
      scanResult = await res.json();
      await refreshLibrary();
      await refreshStats();
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

  async function enqueue() {
    const url = jobUrl.trim();
    if (!url || jobBusy) return;
    jobBusy = true;
    jobError = '';
    try {
      const res = await fetch('/api/v1/jobs', {
        method: 'POST',
        headers: { ...headers(), 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'ytdlp', url }),
      });
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
      jobUrl = '';
      await refreshJobs();
    } catch (err) {
      jobError = String(err);
    } finally {
      jobBusy = false;
    }
  }

  async function cancelJob(id) {
    await fetch(`/api/v1/jobs/${id}/cancel`, { method: 'POST', headers: headers() });
    await refreshJobs();
  }

  async function pauseJob(id) {
    await fetch(`/api/v1/jobs/${id}/pause`, { method: 'POST', headers: headers() });
    await refreshJobs();
  }

  async function retryJob(id) {
    await fetch(`/api/v1/jobs/${id}/retry`, { method: 'POST', headers: headers() });
    await refreshJobs();
  }

  async function saveStreaming() {
    streamBusy = true;
    streamError = '';
    try {
      const body = { ...streaming };
      if (rdToken.trim()) body.realDebridToken = rdToken.trim();
      const res = await fetch('/api/v1/settings/streaming', {
        method: 'PUT',
        headers: { ...headers(), 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
      streaming = await res.json();
      rdToken = '';
    } catch (err) {
      streamError = String(err);
    } finally {
      streamBusy = false;
    }
  }

  function upsertJob(job) {
    if (!job?.id) return;
    const next = jobs.filter((j) => j.id !== job.id);
    next.unshift(job);
    next.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    jobs = next;
  }

  function jobLabel(job) {
    if (job.ready && job.status !== 'finished') return 'ready';
    return job.status || 'queued';
  }

  function fmtAgo(unix) {
    if (!unix || unix < 0) return 'never';
    const s = Math.max(0, Math.floor(Date.now() / 1000) - unix);
    if (s < 5) return 'just now';
    if (s < 60) return `${s}s ago`;
    if (s < 3600) return `${Math.floor(s / 60)}m ago`;
    return `${Math.floor(s / 3600)}h ago`;
  }

  function fmtTs(ms) {
    if (!ms) return '';
    const d = new Date(ms > 1e12 ? ms : ms * 1000);
    return d.toLocaleTimeString();
  }

  function fmtClock(ms) {
    if (!ms || ms < 0) return '';
    const total = Math.floor(ms / 1000);
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return `${m}:${String(s).padStart(2, '0')}`;
  }

  function bytes(n) {
    if (!n) return '—';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let v = n;
    let i = 0;
    while (v >= 1024 && i < units.length - 1) {
      v /= 1024;
      i += 1;
    }
    return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
  }

  function toggleChip(listName, value) {
    const cur = streaming?.[listName] || [];
    streaming = {
      ...streaming,
      [listName]: cur.includes(value) ? cur.filter((x) => x !== value) : [...cur, value],
    };
  }

  const filteredItems = $derived(
    items.filter((item) => {
      if (libKind !== 'all' && item.kind !== libKind) return false;
      const q = libQuery.trim().toLowerCase();
      if (!q) return true;
      const hay = `${item.title} ${item.showTitle || ''} ${item.path || ''}`.toLowerCase();
      return hay.includes(q);
    }),
  );

  $effect(() => {
    refreshAll();
    const tick = setInterval(() => {
      refreshHealth();
      refreshStats();
      if (page === 'activity') refreshActivity();
      if (page === 'downloads' || page === 'overview') refreshJobs();
    }, 8000);
    return () => clearInterval(tick);
  });

  $effect(() => {
    const tok = token;
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${proto}//${location.host}/ws${tok ? `?token=${encodeURIComponent(tok)}` : ''}`;
    const ws = new WebSocket(url);
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data);
        if (msg.type === 'activity' && msg.activity) {
          activity = [msg.activity, ...activity.filter((a) => a.ts !== msg.activity.ts)].slice(0, 500);
        }
        if (msg.job?.id && String(msg.type || '').startsWith('job.')) {
          upsertJob(msg.job);
        }
        if (msg.type === 'library.changed') refreshLibrary();
      } catch {
        /* ignore */
      }
    };
    return () => ws.close();
  });
</script>

<div class="shell">
  <aside>
    <div class="brand">
      <h1>Coog</h1>
      <p>Ops console</p>
    </div>
    <nav>
      {#each NAV as item}
        <button class:active={page === item.id} onclick={() => go(item.id)}>{item.label}</button>
      {/each}
    </nav>
    <label class="token">
      Token
      <input bind:value={token} placeholder="COOG_AUTH_TOKEN" onchange={saveToken} />
    </label>
    <div class="health">
      <span class="dot" class:ok={health?.status === 'ok'} class:bad={!!healthError}></span>
      {#if health}
        <span>{health.status} · {health.version}</span>
      {:else}
        <span>{healthError || 'checking…'}</span>
      {/if}
    </div>
  </aside>

  <main>
    {#if page === 'overview'}
      <header>
        <div>
          <h2>Overview</h2>
          <p class="muted">Worker, Real-Debrid, jobs, and catalog health in one place.</p>
        </div>
        <button class="ghost" onclick={refreshStats}>Refresh</button>
      </header>
      {#if statsError}<p class="error">{statsError}</p>{/if}
      <div class="cards">
        <article class="card">
          <h3>API</h3>
          <p class="stat">{stats?.version || health?.version || '—'}</p>
          <p class="muted">{stats?.ffmpeg || 'ffmpeg unknown'}</p>
        </article>
        <article class="card">
          <h3>Worker</h3>
          {#if stats?.worker?.updatedAt}
            <p class="stat" class:bad={stats.worker.stale}>{stats.worker.stale ? 'stale' : 'alive'}</p>
            <p class="muted">pid {stats.worker.pid} · {fmtAgo(stats.worker.updatedAt)}</p>
          {:else}
            <p class="stat bad">offline</p>
            <p class="muted">no heartbeat yet</p>
          {/if}
        </article>
        <article class="card">
          <h3>Library</h3>
          <p class="stat">{stats?.mediaCount ?? items.length}</p>
          <p class="muted">
            {#if stats?.disk}
              {bytes(stats.disk.freeBytes)} free of {bytes(stats.disk.totalBytes)}
            {:else}
              {stats?.libraryPath || '—'}
            {/if}
          </p>
        </article>
        <article class="card">
          <h3>Jobs</h3>
          <p class="stat">{stats?.jobs?.active ?? 0} active</p>
          <p class="muted">{stats?.jobs?.error ?? 0} failed · {stats?.jobs?.finished ?? 0} finished</p>
        </article>
        <article class="card">
          <h3>Real-Debrid</h3>
          {#if stats?.realDebrid?.configured}
            <p class="stat">{stats.realDebrid.username || 'connected'}</p>
            <p class="muted">
              {#if stats.realDebrid.error}
                {stats.realDebrid.error}
              {:else}
                {stats.realDebrid.premium ? 'premium' : stats.realDebrid.type || 'account'}
                {#if stats.realDebrid.expiration} · expires {new Date(stats.realDebrid.expiration).toLocaleDateString()}{/if}
              {/if}
            </p>
          {:else}
            <p class="stat">not set</p>
            <p class="muted">Add a token under Streaming.</p>
          {/if}
        </article>
        <article class="card">
          <h3>Catalog</h3>
          {#if stats?.catalogError}
            <p class="stat bad">error</p>
            <p class="muted">{stats.catalogError}</p>
          {:else if stats?.catalogErrorAt}
            <p class="stat">ok</p>
            <p class="muted">last trending fetch succeeded</p>
          {:else}
            <p class="stat">idle</p>
            <p class="muted">no catalog fetch yet</p>
          {/if}
        </article>
      </div>
    {/if}

    {#if page === 'activity'}
      <header>
        <div>
          <h2>Activity</h2>
          <p class="muted">Client, API, and worker failures in one feed. Tokens are redacted.</p>
        </div>
        <button class="ghost" onclick={refreshActivity}>Refresh</button>
      </header>
      <div class="log">
        {#each activity as ev}
          <article class="log-row" class:error={ev.level === 'error'} class:warn={ev.level === 'warn'}>
            <span class="log-meta">{fmtTs(ev.ts)} · {ev.source} · {ev.type}</span>
            <p>{ev.message}</p>
            {#if ev.jobId || ev.mediaId || ev.sessionId}
              <p class="muted">
                {#if ev.jobId}job {ev.jobId}{/if}
                {#if ev.mediaId} · media {ev.mediaId}{/if}
                {#if ev.sessionId} · session {ev.sessionId}{/if}
              </p>
            {/if}
          </article>
        {:else}
          <p class="muted">No events yet. Play something on the TV or queue a download.</p>
        {/each}
      </div>
    {/if}

    {#if page === 'downloads'}
      <header>
        <div>
          <h2>Downloads</h2>
          <p class="muted">Live over WebSocket. Cancel kills the in-flight ffmpeg/yt-dlp process. Retry re-queues a failed job.</p>
        </div>
        <button class="ghost" onclick={refreshJobs}>Refresh</button>
      </header>
      <div class="enqueue">
        <input bind:value={jobUrl} placeholder="https://…  (yt-dlp web stream)" onkeydown={(e) => e.key === 'Enter' && enqueue()} />
        <button onclick={enqueue} disabled={jobBusy || !jobUrl.trim()}>{jobBusy ? 'Queuing…' : 'Download'}</button>
      </div>
      {#if jobError}<p class="error">{jobError}</p>{/if}
      <div class="transfers">
        {#each jobs as job}
          <article class="transfer">
            <div class="transfer-main">
              <button class="title-btn" onclick={() => expanded = { ...expanded, [job.id]: !expanded[job.id] }}>
                <strong>{job.title || 'Untitled'}</strong>
                <span class="muted">{expanded[job.id] ? 'Hide' : 'Details'}</span>
              </button>
              <div class="transfer-meta">
                <span class="pill" class:ready={job.ready && job.status !== 'finished'} class:done={job.status === 'finished'} class:bad={job.status === 'error' || job.status === 'cancelled'}>
                  {jobLabel(job)}
                </span>
                <span class="pill">{job.type}</span>
                {#if job.type === 'debrid' || job.type === 'http'}
                  <span class="pill rd">Real-Debrid</span>
                {/if}
                <span class="muted">{Math.round((job.progress || 0) * 100)}%</span>
                {#if job.bufferedMs || job.expectedDurationMs}
                  <span class="muted">{fmtClock(job.bufferedMs)} / {fmtClock(job.expectedDurationMs) || '—'}</span>
                {/if}
                {#if job.expectedDurationMs && job.bufferedMs && job.bufferedMs < job.expectedDurationMs}
                  <span class="muted">{fmtClock(job.expectedDurationMs - job.bufferedMs)} left</span>
                {/if}
              </div>
              <div class="bar"><div class="bar-fill" style={`width: ${Math.min(100, Math.round((job.progress || 0) * 100))}%`}></div></div>
              {#if job.error}<p class="error">{job.error}</p>{/if}
              {#if expanded[job.id]}
                <dl class="facts">
                  <div><dt>IMDB</dt><dd>{job.imdbId || '—'}</dd></div>
                  <div><dt>Job</dt><dd><code>{job.id}</code></dd></div>
                  {#if job.mediaId}<div><dt>Media</dt><dd><code>{job.mediaId}</code></dd></div>{/if}
                </dl>
                {#if job.logTail}
                  <pre class="log-tail">{job.logTail}</pre>
                {:else}
                  <p class="muted">No stderr captured yet.</p>
                {/if}
              {/if}
            </div>
            <div class="transfer-actions">
              {#if job.status === 'error' || job.status === 'cancelled' || job.status === 'paused'}
                <button class="ghost" onclick={() => retryJob(job.id)}>{job.status === 'paused' ? 'Resume' : 'Retry'}</button>
              {/if}
              {#if job.status === 'queued' || job.status === 'downloading' || job.status === 'ready'}
                <button class="ghost" onclick={() => pauseJob(job.id)}>Pause</button>
              {/if}
              {#if job.status !== 'finished' && job.status !== 'cancelled' && job.status !== 'error'}
                <button class="ghost danger" onclick={() => cancelJob(job.id)}>Cancel</button>
              {/if}
            </div>
          </article>
        {:else}
          <p class="muted">No transfers. Play a trending title on the TV, or paste a URL above.</p>
        {/each}
      </div>
    {/if}

    {#if page === 'library'}
      <header>
        <div>
          <h2>Library</h2>
          <p class="muted">Search and filter the on-disk catalog. Stream links are HTTP Range URLs the TV already uses.</p>
        </div>
        <div class="toolbar">
          <button onclick={rescan} disabled={scanning}>{scanning ? 'Scanning…' : 'Rescan'}</button>
          <button class="ghost" onclick={refreshLibrary}>Refresh</button>
        </div>
      </header>
      {#if scanResult}
        <p class="muted">indexed {scanResult.indexed}, probed {scanResult.probed}, removed {scanResult.removed}</p>
      {/if}
      {#if loadError}<p class="error">{loadError}</p>{/if}
      <div class="toolbar">
        <input bind:value={libQuery} placeholder="Search title or path" />
        <select bind:value={libKind}>
          <option value="all">All kinds</option>
          <option value="movie">Movies</option>
          <option value="episode">Episodes</option>
        </select>
      </div>
      <table>
        <thead>
          <tr>
            <th>Title</th>
            <th>Kind</th>
            <th>Match</th>
            <th>Codec</th>
            <th>Resolution</th>
            <th>Probe</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {#each filteredItems as item}
            <tr class="clickable" onclick={() => openItem(item.id)}>
              <td>{item.showTitle ? `${item.showTitle} — ${item.title}` : item.title}</td>
              <td>{item.kind}</td>
              <td>{item.matchStatus || '—'}</td>
              <td>{item.codecVideo || '—'} {item.hdr ? `· ${item.hdr}` : ''}</td>
              <td>{item.width && item.height ? `${item.width}×${item.height}` : '—'}</td>
              <td>{item.probeError || 'ok'}</td>
              <td><a href={item.streamUrl || `/api/v1/media/${item.id}/stream`} onclick={(e) => e.stopPropagation()}>stream</a></td>
            </tr>
          {:else}
            <tr><td colspan="7" class="muted">No items. Point COOG_LIBRARY_PATH at Videos and rescan.</td></tr>
          {/each}
        </tbody>
      </table>
      {#if selected}
        <section class="detail">
          <h3>{selected.title}</h3>
          <p class="muted"><code>{selected.path}</code></p>
          <p>match {selected.matchStatus || 'unmatched'}{selected.imdbId ? ` · ${selected.imdbId}` : ''}</p>
          {#if selected.tagline}<p>{selected.tagline}</p>{/if}
          <p>video {selected.codecVideo || '—'} · audio {selected.codecAudio || '—'} · {selected.contentType || '—'}</p>
          {#if selected.probeError}<p class="error">{selected.probeError}</p>{/if}
          <p><a href={selected.streamUrl || `/api/v1/media/${selected.id}/stream`}>playable stream</a></p>
        </section>
      {/if}
    {/if}

    {#if page === 'streaming'}
      <header>
        <div>
          <h2>Streaming</h2>
          <p class="muted">Real-Debrid, keep-on-disk, binge knobs, and Torrentio filters. The worker keeps downloading after the TV leaves.</p>
        </div>
      </header>
      {#if streaming}
        <div class="settings-grid">
          <label class="check"><input type="checkbox" bind:checked={streaming.saveToLibrary} /> Save finished streams to the local library</label>
          <label class="check"><input type="checkbox" bind:checked={streaming.autoplayNextEpisode} /> Auto-play next episode</label>
          <label class="check"><input type="checkbox" bind:checked={streaming.autoDownloadNextEpisode} /> Auto-download next episode</label>
          <label class="check"><input type="checkbox" bind:checked={streaming.includeWebStreams} /> Include web streams when searching</label>
          <label>Prefetch before end (minutes)
            <input type="number" bind:value={streaming.prefetchBeforeEndMinutes} min="0" />
          </label>
          <label>Prefetch count
            <input type="number" bind:value={streaming.prefetchCount} min="1" />
          </label>
          <label>Continue overlay (seconds)
            <input type="number" bind:value={streaming.continueOverlaySeconds} min="3" />
          </label>
        </div>
        <h3>Torrentio providers</h3>
        <div class="chips">
          {#each PROVIDERS as name}
            <button class="chip" class:on={(streaming.torrentioProviders || []).includes(name)} onclick={() => toggleChip('torrentioProviders', name)}>{name}</button>
          {/each}
        </div>
        <h3>Exclude qualities</h3>
        <div class="chips">
          {#each QUALITIES as name}
            <button class="chip" class:on={(streaming.excludeQualities || []).includes(name)} onclick={() => toggleChip('excludeQualities', name)}>{name}</button>
          {/each}
        </div>
        <p class="muted">
          Real-Debrid
          {#if streaming.realDebridConfigured}
            connected · {streaming.realDebridTokenMasked}
          {:else}
            not configured — set <code>REALDEBRID_API_TOKEN</code> or paste a token.
          {/if}
        </p>
        <div class="enqueue">
          <input bind:value={rdToken} placeholder="Real-Debrid API token" type="password" />
          <button onclick={saveStreaming} disabled={streamBusy}>{streamBusy ? 'Saving…' : 'Save'}</button>
        </div>
        {#if streamError}<p class="error">{streamError}</p>{/if}
      {:else if streamError}
        <p class="error">{streamError}</p>
      {/if}
    {/if}
  </main>
</div>
