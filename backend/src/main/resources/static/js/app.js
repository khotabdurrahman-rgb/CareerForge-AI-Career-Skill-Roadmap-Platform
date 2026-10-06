/* CareerForge: same-origin session API, hash routing, and accessible native dialogs. */
(() => {
  'use strict';
  const $ = (selector, root = document) => root.querySelector(selector);
  const app = $('#app');
  const modal = $('#modal');
  const routes = {
    dashboard: ['Dashboard', 'grid-1x2'], skills: ['My skills', 'stack'],
    careers: ['Career paths', 'compass'], gap: ['Skill gap', 'pie-chart'],
    roadmap: ['Learning roadmap', 'signpost-split'], projects: ['Projects', 'folder2-open'],
    resources: ['Resources', 'book'], assessments: ['Assessments', 'clipboard-check'], planner: ['Weekly planner', 'calendar3'],
    resume: ['Resume builder', 'file-earmark-person'], compare: ['Compare careers', 'layout-split'],
    recommendations: ['Project ideas', 'lightbulb'], history: ['Progress history', 'clock-history'], mentor: ['Career mentor', 'chat-dots'],
    profile: ['My profile', 'person'], admin: ['Administration', 'shield-check']
  };
  const statuses = { NOT_STARTED: 'Not started', IN_PROGRESS: 'In progress', COMPLETED: 'Completed' };
  const levels = ['Beginner', 'Intermediate', 'Advanced'];
  const state = { user: null, data: null, catalog: [], careers: [], users: [], route: 'dashboard', query: '', filter: '', adminTab: 'users', busy: false, revision: 0 };
  let returnFocus = null;
  let sessionRevision = 0;
  let refreshTask = null;
  const mobileViewport = window.matchMedia('(max-width: 900px)');

  const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const icon = name => `<i class="bi bi-${name}" aria-hidden="true"></i>`;
  const eq = (a, b) => String(a) === String(b);
  const initial = name => String(name || 'Student').trim().split(/\s+/).slice(0, 2).map(word => word[0]).join('').toUpperCase();
  const isAdmin = () => /^(ROLE_)?ADMIN$/i.test(state.user?.role || '');
  const list = value => Array.isArray(value) ? value : [];
  const readiness = () => Math.min(100, Math.max(0, Number(state.data?.gap?.readiness) || 0));
  const matches = (...values) => values.join(' ').toLowerCase().includes(state.query.toLowerCase());
  const safeUrl = value => { try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : ''; } catch { return ''; } };
  const badge = (label, tone = '') => `<span class="tag ${tone}">${esc(label)}</span>`;
  const statusBadge = value => badge(statuses[value] || value || 'Not started', value === 'COMPLETED' ? 'green' : value === 'IN_PROGRESS' ? 'blue' : '');
  const linkButton = (route, label, glyph = 'arrow-right') => `<a class="btn btn-outline-secondary" href="#${route}">${esc(label)} ${icon(glyph)}</a>`;
  const actionButton = (action, label, glyph = 'plus-lg', id = '', style = 'primary') => `<button type="button" class="btn btn-${style}" data-action="${action}"${id !== '' ? ` data-id="${esc(id)}"` : ''}>${icon(glyph)} ${esc(label)}</button>`;
  const iconButton = (action, label, glyph, id, danger = false) => `<button type="button" class="icon-btn${danger ? ' danger' : ''}" data-action="${action}" data-id="${esc(id)}" aria-label="${esc(label)}" title="${esc(label)}">${icon(glyph)}</button>`;
  const empty = (title, description, action = '', glyph = 'inbox') => `<div class="empty">${icon(glyph)}<h2>${esc(title)}</h2><p>${esc(description)}</p>${action}</div>`;
  const ring = value => `<div class="ring" style="--progress:${value}" role="img" aria-label="Career readiness ${value}%"><div class="ring-inner">${value}%<small>career ready</small></div></div>`;
  const progress = (value, label = 'Progress') => `<div class="progress" role="progressbar" aria-label="${esc(label)}" aria-valuenow="${value}" aria-valuemin="0" aria-valuemax="100"><div class="progress-bar" style="width:${value}%"></div></div>`;
  const field = (label, name, value = '', type = 'text', required = false, max = 255) => `<div class="form-group"><label class="form-label" for="field-${name}">${esc(label)}</label><input class="form-control" id="field-${name}" name="${name}" type="${type}" value="${esc(value)}" maxlength="${max}" ${required ? 'required' : ''} ${type === 'password' ? 'minlength="8"' : ''}></div>`;
  const textarea = (label, name, value = '', required = false, max = 2000) => `<div class="form-group"><label class="form-label" for="field-${name}">${esc(label)}</label><textarea class="form-control" id="field-${name}" name="${name}" rows="3" maxlength="${max}" ${required ? 'required' : ''}>${esc(value)}</textarea></div>`;
  const selectField = (label, name, options, selected = '', required = true) => `<div class="form-group"><label class="form-label" for="field-${name}">${esc(label)}</label><select class="form-select" id="field-${name}" name="${name}" ${required ? 'required' : ''}>${options.map(option => `<option value="${esc(option.value)}" ${eq(option.value, selected) ? 'selected' : ''}>${esc(option.label)}</option>`).join('')}</select></div>`;
  const options = values => values.map(value => ({ value, label: value }));
  const skillOptions = () => [{ value: '', label: 'Choose a skill' }, ...state.catalog.map(skill => ({ value: skill.id, label: skill.name }))];
  const careerOptions = () => [{ value: '', label: 'Choose a career path' }, ...state.careers.map(career => ({ value: career.id, label: career.name }))];
  const errorBox = () => '<div class="error-box" role="alert" tabindex="-1" data-form-error></div>';
  const v2 = window.CareerForgeV2({ $, state, api: (...args) => api(...args), esc, icon, eq, list, badge, statuses, field, textarea, selectField, options, skillOptions, careerOptions, errorBox, head, footer, empty, progress, actionButton, iconButton, openModal: (...args) => openModal(...args), toast, submitForm: (...args) => submitForm(...args), render: () => renderView(), refresh: () => refresh(), endSession: () => endSession() });

  class ApiError extends Error { constructor(message, status) { super(message); this.status = status; } }
  async function api(path, method = 'GET', body) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(path, { method, credentials: 'same-origin', cache: 'no-store', signal: controller.signal,
        headers: { Accept: 'application/json', ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}) });
      const raw = await response.text();
      let data;
      try { data = raw ? JSON.parse(raw) : null; } catch {
        if (response.ok) throw new ApiError('The server returned an unexpected response. Please try again.', response.status);
      }
      if (!response.ok) {
        const details = data?.errors;
        const validation = Array.isArray(details) ? details.join(' ') : details && typeof details === 'object' ? Object.values(details).join(' ') : '';
        const error = new ApiError(validation || data?.message || data?.detail || (response.status === 401 ? 'Your session has expired. Please sign in again.' : `Request failed (${response.status}). Please try again.`), response.status);
        if (response.status === 401 && state.user && !path.startsWith('/api/auth/')) endSession(true);
        throw error;
      }
      return data;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(error.name === 'AbortError' ? 'The request timed out. Please try again.' : 'Cannot reach CareerForge. Check your connection and try again.', 0);
    } finally { clearTimeout(timeout); }
  }

  function toast(message, error = false) {
    const node = document.createElement('div');
    node.className = `notification${error ? ' error' : ''}`;
    if (error) node.setAttribute('role', 'alert');
    node.innerHTML = `${icon(error ? 'exclamation-circle' : 'check-circle')}<span>${esc(message)}</span><button aria-label="Dismiss notification">${icon('x-lg')}</button>`;
    const region = $('#notifications');
    region.append(node);
    const limit = mobileViewport.matches ? 2 : 3;
    while (region.children.length > limit) region.firstElementChild.remove();
    $('button', node).onclick = () => node.remove();
    setTimeout(() => node.remove(), error ? 12000 : 6500);
  }

  function endSession(expired = false) {
    sessionRevision++;
    v2.clear();
    state.user = null; state.data = null; state.catalog = []; state.careers = []; state.users = [];
    state.busy = false; refreshTask = null;
    if (modal.open) modal.close();
    location.hash = 'login';
    renderAuth();
    if (expired) toast('Session expired. Please sign in again.', true);
  }

  function renderAuth(message = '') {
    closeNav();
    if (v2.isRecovery()) { v2.activate('recovery'); v2.renderRecovery(); return; }
    const register = location.hash === '#register';
    document.title = `${register ? 'Create account' : 'Sign in'} | CareerForge`;
    app.innerHTML = `<main id="main" class="auth-page"><div class="auth-wrap">
      <a href="#login" class="brand auth-brand"><img src="assets/brand.png" alt="">CareerForge</a>
      <section class="auth-panel" aria-labelledby="auth-title"><div class="auth-tabs"><a href="#login" class="${register ? '' : 'active'}" ${!register ? 'aria-current="page"' : ''}>Sign in</a><a href="#register" class="${register ? 'active' : ''}" ${register ? 'aria-current="page"' : ''}>Create account</a></div>
      <h1 id="auth-title">${register ? 'Start building your future.' : 'Welcome back.'}</h1><p class="subtitle">${register ? 'Your next career milestone starts here.' : 'A little progress today. A stronger career tomorrow.'}</p>
      <form id="auth-form" data-mode="${register ? 'register' : 'login'}">${errorBox()}
      ${register ? field('Full name', 'name', '', 'text', true, 100) : ''}
      <div class="form-group"><label class="form-label" for="email">Email address</label><input class="form-control" type="email" id="email" name="email" placeholder="you@college.edu" autocomplete="username" maxlength="255" required></div>
      <div class="form-group"><label class="form-label" for="password">Password</label><div class="password-wrap"><input class="form-control" type="password" id="password" name="password" placeholder="${register ? 'At least 8 characters' : 'Enter your password'}" autocomplete="${register ? 'new-password' : 'current-password'}" ${register ? 'minlength="8" maxlength="72"' : ''} required><button type="button" class="icon-btn" data-action="password" aria-label="Show password" title="Show password">${icon('eye')}</button></div></div>
      <button class="btn btn-primary submit" type="submit">${register ? 'Create account' : 'Sign in'} ${icon('arrow-right')}</button></form><div class="recovery-links"><a href="#forgot-password">Forgot password?</a><a href="#verification">Verify email</a></div>
      <div class="demo-separator" data-demo-controls hidden>Try a demo account</div><div class="demo-buttons" data-demo-controls hidden>${actionButton('demo-student', 'Student demo', 'mortarboard', '', 'outline-secondary')}${actionButton('demo-admin', 'Admin demo', 'shield-check', '', 'outline-secondary')}</div>
      </section><p class="auth-footer">CareerForge &middot; Your career, a work in progress.</p></div></main>`;
    if (message) { const box = $('[data-form-error]'); box.textContent = message; }
    if (register) $('#field-name').autocomplete = 'name';
    api('/api/auth/config').then(config => {
      document.querySelectorAll('[data-demo-controls]').forEach(element => { element.hidden = !config.demoEnabled; });
    }).catch(() => {});
  }

  function shell() {
    app.innerHTML = `<button class="sidebar-backdrop" aria-label="Close navigation" data-action="close-nav" tabindex="-1"></button>
      <aside class="sidebar" aria-label="Main navigation"><a class="brand" href="#dashboard"><img src="assets/brand.png" alt="">CareerForge</a>
      <nav class="side-nav">${[['Workspace', ['dashboard', 'skills', 'assessments', 'planner', 'roadmap']], ['Explore', ['careers', 'compare', 'gap', 'resources', 'mentor']], ['Portfolio', ['projects', 'recommendations', 'resume', 'history']], ['Account', ['profile', 'admin']]].map(([group, keys]) => `<div class="nav-label">${group}</div>${keys.filter(key => key !== 'admin' || isAdmin()).map(key => { const [label, glyph] = routes[key]; return `<a href="#${key}" data-route="${key}">${icon(glyph)}<span>${label}</span></a>`; }).join('')}`).join('')}</nav>
      <div class="sidebar-foot"><div class="workspace-label">${icon('mortarboard')}<div>Student workspace<br><small>Build your next chapter</small></div></div><button class="logout" data-action="logout">${icon('box-arrow-left')} Sign out</button></div></aside>
      <div class="shell"><header class="topbar"><div class="d-flex align-items-center gap-3"><button class="icon-btn mobile-menu" data-action="open-nav" aria-controls="sidebar-nav" aria-expanded="false" aria-label="Open navigation">${icon('list')}</button><div class="breadcrumb"><span>Workspace</span>${icon('chevron-right')}<strong id="breadcrumb-page">Dashboard</strong></div></div><div class="top-actions"><span class="refresh-label" id="refresh-label">All changes saved</span><button class="icon-btn" data-action="refresh" title="Refresh workspace" aria-label="Refresh workspace">${icon('arrow-clockwise')}</button><a class="user-button" href="#profile" aria-label="Open your profile"><span class="avatar">${esc(initial(state.user.name))}</span><span class="user-label"><strong>${esc(state.user.name)}</strong><small>${isAdmin() ? 'Administrator' : 'Student account'}</small></span></a></div></header>
      <main id="main" class="content" tabindex="-1"></main></div>`;
    $('.sidebar').id = 'sidebar-nav';
  }

  function head(title, description, action = '') {
    return `<div class="page-head"><div><h1>${esc(title)}</h1><p>${esc(description)}</p></div>${action}</div>`;
  }
  function toolbar(placeholder, filterOptions = null) {
    return `<div class="toolbar"><div class="search-box">${icon('search')}<input class="form-control" type="search" id="view-search" placeholder="${esc(placeholder)}" aria-label="${esc(placeholder)}" value="${esc(state.query)}"></div>${filterOptions ? `<div class="filters"><select id="view-filter" class="form-select" aria-label="Filter results">${filterOptions.map(option => `<option value="${esc(option.value)}" ${eq(option.value, state.filter) ? 'selected' : ''}>${esc(option.label)}</option>`).join('')}</select></div>` : ''}</div>`;
  }
  function footer() { return '<footer class="footer"><span>CareerForge &middot; Student workspace</span><span>Small steps. Real progress.</span></footer>'; }
  function roadmapRows(items, interactive = false) {
    return items.map((step, index) => {
      const done = step.status === 'COMPLETED'; const current = step.status === 'IN_PROGRESS';
      const symbol = `<span class="step-symbol ${done ? 'done' : current ? 'current' : ''}">${done ? icon('check-lg') : step.position || index + 1}</span>`;
      return interactive ? `<li>${symbol}<div class="step-detail"><h2>${esc(step.title)}</h2><p>${esc(step.skill?.name || 'Portfolio milestone')}</p></div><select class="form-select" data-step="${esc(step.id)}" aria-label="Status for ${esc(step.title)}">${Object.entries(statuses).map(([value, label]) => `<option value="${value}" ${step.status === value ? 'selected' : ''}>${label}</option>`).join('')}</select></li>` : `<div class="list-row"><div class="row-main">${symbol}<div><strong>${esc(step.title)}</strong><small>${esc(step.skill?.name || 'Portfolio milestone')}</small></div></div>${statusBadge(step.status)}</div>`;
    }).join('');
  }

  function dashboardView() {
    const d = state.data; const completed = d.roadmap.filter(step => step.status === 'COMPLETED').length;
    const next = d.roadmap.filter(step => step.status !== 'COMPLETED').slice(0, 3);
    const roadmapPercent = d.roadmap.length ? Math.round(completed / d.roadmap.length * 100) : 0;
    const metrics = [
      ['Career readiness', `${readiness()}%`, 'pie-chart', `${d.gap.completed.length} of ${d.career?.skills?.length || 0} required skills`],
      ['Skills in your toolkit', d.skills.length, 'stack', 'Keep growing your skill set'],
      ['Roadmap milestones', `${completed}/${d.roadmap.length}`, 'signpost-split', `${roadmapPercent}% of your roadmap completed`],
      ['Portfolio projects', d.projects.length, 'folder2-open', `${d.projects.filter(project => project.status === 'COMPLETED').length} completed projects`]
    ];
    return `<div class="date-label">${icon('calendar3')} ${esc(new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }))}</div>${head(`Hello, ${state.user.name.split(' ')[0] || 'there'}.`, "Here's where you stand on your career journey.", actionButton('add-skill', 'Add a skill'))}
      <div class="metrics">${metrics.map(([label, number, glyph, note]) => `<section class="panel metric"><div class="metric-top"><span>${label}</span><span class="metric-icon">${icon(glyph)}</span></div><div class="metric-number">${number}</div><small>${esc(note)}</small></section>`).join('')}</div>
      <nav class="dashboard-v2" aria-label="Next steps"><a href="#planner">${icon('calendar3')} Plan your week ${icon('arrow-right')}</a><a href="#assessments">${icon('clipboard-check')} Assess your skills ${icon('arrow-right')}</a><a href="#history">${icon('clock-history')} Review your progress ${icon('arrow-right')}</a></nav>
      <div class="dashboard-grid"><section class="panel"><div class="goal-band"><div class="goal-symbol">${icon('compass')}</div><div><span class="eyebrow">Your career goal</span><h2>${esc(d.career?.name || 'Choose your direction')}</h2><p>${d.career ? `${d.career.skills?.length || 0} core skills to build your foundation` : 'Explore a path that feels right for you'}</p></div><a class="btn btn-outline-secondary" href="#careers" aria-label="Change career goal">${icon('pencil')} Change</a></div><div class="readiness-area">${ring(readiness())}<div><h3>${readiness() >= 100 ? 'Your foundation is ready.' : 'Every skill moves you forward.'}</h3><p>${d.gap.missing.length ? `${d.gap.missing.length} more skills to cover your career requirements.` : 'Put your skills into practice with a portfolio project.'}</p><div class="tags">${badge(`${d.gap.completed.length} acquired`, 'green')}${badge(`${d.gap.missing.length} to learn`, 'blue')}</div></div></div><div class="section-note">${icon('info-circle')} Readiness compares your skills with your selected career requirements.</div></section>
      <section class="panel"><div class="panel-head"><h2>Up next</h2><a href="#roadmap">View roadmap ${icon('arrow-right')}</a></div>${next.length ? roadmapRows(next) : empty('You are all caught up', 'Explore resources or start a new project.', linkButton('projects', 'View projects'), 'check2-circle')}<div class="panel-body pt-3"><div class="d-flex justify-content-between mb-2"><small class="text-secondary">Roadmap progress</small><small>${roadmapPercent}%</small></div>${progress(roadmapPercent, 'Roadmap progress')}</div></section>
      <section class="panel"><div class="panel-head"><h2>Your skill toolkit</h2><a href="#skills">View all ${icon('arrow-right')}</a></div>${d.skills.length ? d.skills.slice(0, 5).map(item => `<div class="list-row"><div class="row-main"><span class="metric-icon">${icon('code-slash')}</span><div><strong>${esc(item.skill.name)}</strong><small>Part of your skill toolkit</small></div></div>${badge(item.level, item.level === 'Advanced' ? 'green' : '')}</div>`).join('') : empty('Your toolkit starts here', 'Add the skills you already know.', actionButton('add-skill', 'Add your first skill'), 'stack')}</section>
      <section class="panel"><div class="panel-head"><h2>Recommended learning</h2><a href="#resources">Explore ${icon('arrow-right')}</a></div>${recommendedResources().slice(0, 3).map(resource => resourceRow(resource)).join('') || empty('More learning ahead', 'No resources available yet for your next skills.', linkButton('careers', 'Explore careers'), 'book')}<div class="section-note">Learning resources for your next milestone.</div></section></div>${footer()}`;
  }
  function skillsView() {
    const items = state.data.skills.filter(item => matches(item.skill.name, item.level) && (!state.filter || item.level === state.filter));
    return `${head('My skills', 'A living toolkit of what you know and what you are building.', actionButton('add-skill', 'Add a skill'))}${toolbar('Search your skills', [{ value: '', label: 'All levels' }, ...options(levels)])}<section class="panel" id="results">${skillTable(items)}</section>${footer()}`;
  }
  function skillTable(items) {
    if (!items.length) return empty(state.data.skills.length ? 'No matching skills' : 'Build your skill toolkit', state.data.skills.length ? 'Try a different search or level.' : 'Add a skill and set your current proficiency.', actionButton('add-skill', 'Add a skill'), 'stack');
    return `<div class="table-responsive"><table class="table"><thead><tr><th>Skill</th><th>Proficiency</th><th>Career requirement</th><th>Actions</th></tr></thead><tbody>${items.map(item => `<tr><td><strong>${esc(item.skill.name)}</strong></td><td>${badge(item.level, item.level === 'Advanced' ? 'green' : '')}</td><td>${state.data.career?.skills?.some(skill => eq(skill.id, item.skill.id)) ? badge('Required', 'blue') : '<small>Additional skill</small>'}</td><td><div class="row-actions justify-content-end">${iconButton('edit-skill', `Update ${item.skill.name}`, 'pencil', item.id)}${iconButton('delete-skill', `Remove ${item.skill.name}`, 'trash3', item.id, true)}</div></td></tr>`).join('')}</tbody></table></div>`;
  }
  function careersView() {
    return `${head('Career paths', 'Find your direction. See the skills that will take you there.')}${toolbar('Search career paths')}<div class="cards-grid" id="results">${careerCards()}</div>${footer()}`;
  }
  function careerCards() {
    const items = state.careers.filter(career => matches(career.name, career.description, list(career.skills).map(skill => skill.name).join(' ')));
    return items.map(career => {
      const selected = eq(career.id, state.user.careerId);
      const known = list(career.skills).filter(skill => state.data.skills.some(item => eq(item.skill.id, skill.id))).length;
      const percent = career.skills.length ? Math.round(known / career.skills.length * 100) : 0;
      return `<article class="panel item-card ${selected ? 'selected' : ''}"><div class="d-flex justify-content-between align-items-center"><div class="item-icon">${icon(/data/i.test(career.name) ? 'database' : /design/i.test(career.name) ? 'palette' : /security/i.test(career.name) ? 'shield-lock' : /cloud/i.test(career.name) ? 'cloud' : 'code-slash')}</div>${selected ? badge('Your career goal', 'green') : badge(`${career.skills.length} skills`)}</div><h2>${esc(career.name)}</h2><p>${esc(career.description)}</p><div class="tags">${career.skills.map(skill => badge(skill.name, state.data.skills.some(item => eq(item.skill.id, skill.id)) ? 'green' : '')).join('')}</div><div class="mt-auto"><div class="d-flex justify-content-between mb-2"><small class="text-secondary">${known}/${career.skills.length} skills acquired</small><small>${percent}%</small></div>${progress(percent, `${career.name} readiness`)}</div><div class="card-bottom">${selected ? linkButton('gap', 'View skill gap') : actionButton('select-career', 'Choose this path', 'arrow-right', career.id, 'outline-secondary')}</div></article>`;
    }).join('') || empty('No career paths found', state.query ? 'Try a different search.' : 'Career paths will appear when the catalog is available.', '', 'compass');
  }
  function gapView() {
    const d = state.data;
    return `${head('Skill gap analysis', `Your skills compared with ${d.career?.name || 'your career goal'}.`, linkButton('careers', 'Change career', 'compass'))}<div class="gap-overview"><div class="readiness-area">${ring(readiness())}<div><h2>${esc(d.career?.name || 'Select a career goal')}</h2><p>${d.gap.completed.length} acquired &middot; ${d.gap.missing.length} still to learn</p>${badge('Based on your current skill toolkit', 'blue')}</div></div>${linkButton('roadmap', 'Go to roadmap', 'signpost-split')}</div><div class="split"><section class="panel"><div class="panel-head"><h2>${icon('check2-circle')} Acquired skills</h2>${badge(d.gap.completed.length, 'green')}</div>${d.gap.completed.length ? `<ul class="gap-list">${d.gap.completed.map(skill => `<li><span>${icon('check-circle')} <strong class="ms-2">${esc(skill.name)}</strong></span>${badge('Acquired', 'green')}</li>`).join('')}</ul>` : empty('A fresh starting point', 'Add skills you already know to see them here.', actionButton('add-skill', 'Add a skill'), 'stack')}</section><section class="panel"><div class="panel-head"><h2>${icon('bullseye')} Skills to build</h2>${badge(d.gap.missing.length, 'blue')}</div>${d.gap.missing.length ? `<ul class="gap-list missing">${d.gap.missing.map(skill => `<li><span>${icon('circle')} <strong class="ms-2">${esc(skill.name)}</strong></span>${actionButton('learn-skill', 'Resources', 'book', skill.id, 'outline-secondary')}</li>`).join('')}</ul>` : empty('All requirements covered', 'Your next step is putting those skills to work.', linkButton('projects', 'Build your portfolio'), 'trophy')}</section></div>${footer()}`;
  }
  function roadmapView() {
    const items = state.data.roadmap.filter(step => matches(step.title, step.skill?.name || '') && (!state.filter || step.status === state.filter));
    const done = state.data.roadmap.filter(step => step.status === 'COMPLETED').length;
    const percent = state.data.roadmap.length ? Math.round(done / state.data.roadmap.length * 100) : 0;
    return `${head('Learning roadmap', `Your next steps toward ${state.data.career?.name || 'your career goal'}.`, linkButton('resources', 'Find resources', 'book'))}<div class="d-flex align-items-center gap-3 mb-4"><div class="flex-grow-1">${progress(percent, 'Roadmap completed')}</div><span class="text-secondary small">${done}/${state.data.roadmap.length} complete</span></div>${toolbar('Search milestones', [{ value: '', label: 'All statuses' }, ...Object.entries(statuses).map(([value, label]) => ({ value, label }))])}<section class="panel" id="results">${roadmapContent(items)}</section>${footer()}`;
  }
  function roadmapContent(items) { return items.length ? `<ol class="roadmap-list">${roadmapRows(items, true)}</ol>` : empty('No milestones to show', state.filter || state.query ? 'Try another search or status.' : 'Choose a career path to build your learning roadmap.', linkButton('careers', 'Explore careers'), 'signpost-split'); }
  function projectsView() {
    return `${head('Portfolio projects', 'Turn what you learn into something you can show.', actionButton('add-project', 'Add project'))}${toolbar('Search projects', [{ value: '', label: 'All projects' }, { value: 'IN_PROGRESS', label: 'In progress' }, { value: 'COMPLETED', label: 'Completed' }])}<div class="cards-grid" id="results">${projectCards()}</div>${footer()}`;
  }
  function projectCards() {
    const items = state.data.projects.filter(project => matches(project.name, project.technology, project.description) && (!state.filter || project.status === state.filter));
    return items.map(project => `<article class="panel item-card"><div class="d-flex justify-content-between align-items-center"><div class="item-icon">${icon('folder2-open')}</div>${statusBadge(project.status)}</div><h2>${esc(project.name)}</h2><p>${esc(project.description || 'No description added.')}</p><div class="tags">${String(project.technology || '').split(',').map(tech => tech.trim()).filter(Boolean).map(tech => badge(tech)).join('')}</div><div class="card-bottom">${safeUrl(project.githubUrl) ? `<a href="${esc(safeUrl(project.githubUrl))}" target="_blank" rel="noopener noreferrer" class="btn btn-outline-secondary">${icon('github')} Repository ${icon('box-arrow-up-right')}</a>` : '<small class="text-secondary">No repository linked</small>'}<div class="row-actions">${iconButton('edit-project', `Edit ${project.name}`, 'pencil', project.id)}${iconButton('delete-project', `Delete ${project.name}`, 'trash3', project.id, true)}</div></div></article>`).join('') || empty(state.data.projects.length ? 'No matching projects' : 'Your portfolio starts here', state.data.projects.length ? 'Try a different search or status.' : 'Add something you are building or have completed.', actionButton('add-project', 'Add your first project'), 'folder2-open');
  }
  function recommendedResources() {
    const missing = state.data.gap.missing;
    return [...state.data.resources].sort((a, b) => Number(missing.some(skill => eq(skill.id, b.skill?.id))) - Number(missing.some(skill => eq(skill.id, a.skill?.id))));
  }
  function resourceRow(resource) {
    const url = safeUrl(resource.url);
    return `<div class="list-row"><div class="row-main"><span class="metric-icon">${icon(resource.type?.toLowerCase() === 'video' ? 'play-btn' : 'book')}</span><div><strong>${esc(resource.title)}</strong><small>${esc(resource.skill?.name || 'General')} &middot; ${esc(resource.type)}</small></div></div>${url ? `<a class="icon-btn" href="${esc(url)}" target="_blank" rel="noopener noreferrer" title="Open ${esc(resource.title)}" aria-label="Open ${esc(resource.title)} in new tab">${icon('box-arrow-up-right')}</a>` : badge('Unavailable')}</div>`;
  }
  function resourcesView() {
    const types = [...new Set(state.data.resources.map(resource => resource.type))].sort();
    return `${head('Learning resources', 'The next good read, lesson, or course for your journey.')}${toolbar('Search titles, skills, or types', [{ value: '', label: 'All types' }, ...options(types)])}<div class="cards-grid" id="results">${resourceCards()}</div>${footer()}`;
  }
  function resourceCards() {
    const items = recommendedResources().filter(resource => matches(resource.title, resource.skill?.name, resource.type) && (!state.filter || resource.type === state.filter));
    return items.map(resource => {
      const url = safeUrl(resource.url); const recommended = state.data.gap.missing.some(skill => eq(skill.id, resource.skill?.id));
      return `<article class="panel item-card"><div class="d-flex justify-content-between align-items-center"><span class="item-icon">${icon(resource.type?.toLowerCase() === 'video' ? 'play-btn' : 'journal-code')}</span>${badge(resource.type, 'blue')}</div><h2>${esc(resource.title)}</h2><div class="tags">${badge(resource.skill?.name || 'General')}${recommended ? badge('For your skill gap', 'green') : ''}</div><p>${esc(url ? new URL(url).hostname : 'Resource link unavailable')}</p><div class="card-bottom">${url ? `<a class="btn btn-outline-secondary" href="${esc(url)}" target="_blank" rel="noopener noreferrer">Open resource ${icon('box-arrow-up-right')}</a>` : badge('Unavailable')}</div></article>`;
    }).join('') || empty('No resources found', state.query || state.filter ? 'Try a different search or type.' : 'Learning resources will appear as the catalog grows.', '', 'book');
  }
  function profileView() {
    const u = state.user;
    return `${head('My profile', 'A little context helps shape your career journey.')}<div class="profile-grid"><aside class="profile-summary"><div class="avatar">${esc(initial(u.name))}</div><h2>${esc(u.name)}</h2><p>${esc(u.email)}</p>${badge(isAdmin() ? 'Administrator' : 'Student', 'green')}<p class="mt-4 mb-1">Career goal</p><strong class="small">${esc(state.data.career?.name || 'Not selected')}</strong></aside><form class="profile-form" id="profile-form">${errorBox()}<h2 class="mb-4">Personal &amp; academic details</h2><div class="form-grid">${field('Full name', 'name', u.name, 'text', true, 100)}<div>${field('Email address', 'email', u.email, 'email')}<small class="text-secondary">Your sign-in email</small></div>${field('Course / degree', 'course', u.course)}${field('College', 'college', u.college)}${field('Current academic year', 'currentYear', u.currentYear, 'text', false, 50)}${selectField('Career goal', 'careerId', careerOptions(), u.careerId)}</div><div class="form-footer"><button class="btn btn-primary" type="submit">${icon('check-lg')} Save changes</button></div></form></div>${footer()}`;
  }
  function adminView() {
    return `${head('Administration', 'Manage the people and learning catalog in CareerForge.', state.adminTab === 'careers' ? actionButton('add-career', 'Add career') : state.adminTab === 'resources' ? actionButton('add-resource', 'Add resource') : '')}<div class="tabs" role="tablist" aria-label="Administration"><button role="tab" data-admin-tab="users" class="${state.adminTab === 'users' ? 'active' : ''}" aria-selected="${state.adminTab === 'users'}">Users <span class="ms-1 text-secondary">${state.users.length}</span></button><button role="tab" data-admin-tab="careers" class="${state.adminTab === 'careers' ? 'active' : ''}" aria-selected="${state.adminTab === 'careers'}">Careers</button><button role="tab" data-admin-tab="resources" class="${state.adminTab === 'resources' ? 'active' : ''}" aria-selected="${state.adminTab === 'resources'}">Resources</button></div>${toolbar(`Search ${state.adminTab}`)}<section class="panel" id="results" role="tabpanel" aria-label="${esc(state.adminTab)}">${adminContent()}</section>${footer()}`;
  }
  function adminContent() {
    if (state.adminTab === 'users') {
      const items = state.users.filter(user => matches(user.name, user.email, user.course, user.college, user.role));
      return items.length ? `<div class="table-responsive"><table class="table"><thead><tr><th>Student</th><th>Academics</th><th>Career goal</th><th>Role</th></tr></thead><tbody>${items.map(user => `<tr><td><strong>${esc(user.name)}</strong><br><small>${esc(user.email)}</small></td><td>${esc(user.course || 'Not provided')}<br><small>${esc(user.college || '')} ${esc(user.currentYear || '')}</small></td><td>${esc(state.careers.find(career => eq(career.id, user.careerId))?.name || 'Not selected')}</td><td>${badge(user.role, /admin/i.test(user.role) ? 'blue' : 'green')}</td></tr>`).join('')}</tbody></table></div>` : empty('No users found', 'Try a different search.', '', 'people');
    }
    if (state.adminTab === 'careers') {
      const items = state.careers.filter(career => matches(career.name, career.description));
      return items.length ? `<div class="table-responsive"><table class="table"><thead><tr><th>Career path</th><th>Required skills</th><th>Actions</th></tr></thead><tbody>${items.map(career => `<tr><td><strong>${esc(career.name)}</strong><br><small>${esc(career.description)}</small></td><td><div class="tags">${career.skills.map(skill => badge(skill.name)).join('')}</div></td><td><div class="row-actions justify-content-end">${iconButton('edit-career', `Edit ${career.name}`, 'pencil', career.id)}${iconButton('delete-career', `Delete ${career.name}`, 'trash3', career.id, true)}</div></td></tr>`).join('')}</tbody></table></div>` : empty('No careers found', 'Add a career path or try another search.', actionButton('add-career', 'Add career'), 'compass');
    }
    const items = state.data.resources.filter(resource => matches(resource.title, resource.skill?.name, resource.type));
    return items.length ? `<div class="table-responsive"><table class="table"><thead><tr><th>Resource</th><th>Skill</th><th>Type</th><th>Actions</th></tr></thead><tbody>${items.map(resource => `<tr><td><strong>${esc(resource.title)}</strong>${safeUrl(resource.url) ? `<br><a href="${esc(safeUrl(resource.url))}" target="_blank" rel="noopener noreferrer"><small>${esc(new URL(safeUrl(resource.url)).hostname)} ${icon('box-arrow-up-right')}</small></a>` : ''}</td><td>${esc(resource.skill?.name)}</td><td>${badge(resource.type, 'blue')}</td><td><div class="row-actions justify-content-end">${iconButton('edit-resource', `Edit ${resource.title}`, 'pencil', resource.id)}${iconButton('delete-resource', `Delete ${resource.title}`, 'trash3', resource.id, true)}</div></td></tr>`).join('')}</tbody></table></div>` : empty('No resources found', 'Add a resource or try another search.', actionButton('add-resource', 'Add resource'), 'book');
  }

  const views = { dashboard: dashboardView, skills: skillsView, careers: careersView, gap: gapView, roadmap: roadmapView, projects: projectsView, resources: resourcesView, profile: profileView, admin: adminView, ...v2.views };
  function renderView(focus = false) {
    if (!state.user) return;
    if (v2.isRecovery()) { v2.activate('recovery'); v2.renderRecovery(); return; }
    if (!$('#main.content')) shell();
    const requested = location.hash.slice(1).split('?')[0];
    const route = routes[requested] && (requested !== 'admin' || isAdmin()) ? requested : 'dashboard';
    if (state.route !== route) { state.query = ''; state.filter = ''; state.route = route; }
    v2.activate(route);
    document.title = `${routes[route][0]} | CareerForge`;
    $('#breadcrumb-page').textContent = routes[route][0];
    document.querySelectorAll('[data-route]').forEach(item => { const active = item.dataset.route === route; item.classList.toggle('active', active); if (active) item.setAttribute('aria-current', 'page'); else item.removeAttribute('aria-current'); });
    $('#main').innerHTML = state.data || v2.views[route] ? views[route]() : state.workspaceError ? empty('Your workspace could not load', state.workspaceError, actionButton('retry', 'Try again', 'arrow-clockwise'), 'cloud-slash') : `<div class="loading-area" aria-busy="true"><span class="spinner-border text-success" role="status"><span class="visually-hidden">Loading</span></span>Loading your workspace...</div>`;
    if (route === 'profile' && state.data) { $('#field-email').disabled = true; v2.profile(); }
    v2.afterRender();
    closeNav();
    if (focus) $('#main').focus({ preventScroll: true });
  }
  function updateResults() {
    const target = $('#results'); if (!target) return;
    const content = {
      skills: () => skillTable(state.data.skills.filter(item => matches(item.skill.name, item.level) && (!state.filter || item.level === state.filter))),
      careers: careerCards, projects: projectCards, resources: resourceCards, admin: adminContent,
      roadmap: () => roadmapContent(state.data.roadmap.filter(step => matches(step.title, step.skill?.name || '') && (!state.filter || step.status === state.filter)))
    };
    if (content[state.route]) target.innerHTML = content[state.route]();
  }

  async function refresh(render = true) {
    if (refreshTask) return refreshTask;
    const revision = sessionRevision;
    const task = (async () => {
      const requests = [api('/api/dashboard'), api('/api/skills'), api('/api/careers')];
      if (isAdmin()) requests.push(api('/api/admin/users'));
      const results = await Promise.allSettled(requests);
      if (revision !== sessionRevision) return false;
      const failed = results.find(result => result.status === 'rejected');
      if (failed) throw failed.reason;
      const [data, catalog, careers, users] = results.map(result => result.value);
      if (!data?.user) throw new ApiError('The dashboard response is missing your profile. Please retry.', 0);
      state.user = data.user;
      state.workspaceError = '';
      state.data = { ...data, skills: list(data.skills), roadmap: list(data.roadmap).sort((a, b) => a.position - b.position), projects: list(data.projects), resources: list(data.resources), gap: { ...data.gap, completed: list(data.gap?.completed), missing: list(data.gap?.missing) } };
      state.catalog = list(catalog); state.careers = list(careers); state.users = list(users);
      if (render) { shell(); renderView(); }
      return true;
    })();
    refreshTask = task;
    try { return await task; } finally { if (refreshTask === task) refreshTask = null; }
  }
  async function loadWorkspace() {
    state.workspaceError = '';
    shell(); renderView();
    try { await refresh(); }
    catch (error) {
      if (!state.user) return;
      state.workspaceError = error.message;
      if (v2.views[state.route] || v2.isRecovery()) return;
      $('#main').innerHTML = empty('Your workspace could not load', error.message, actionButton('retry', 'Try again', 'arrow-clockwise'), 'cloud-slash');
    }
  }
  function closeNav() {
    $('.sidebar')?.classList.remove('open'); $('.sidebar-backdrop')?.classList.remove('visible');
    $('[data-action="open-nav"]')?.setAttribute('aria-expanded', 'false');
    if ($('.sidebar')) $('.sidebar').inert = mobileViewport.matches;
    if ($('.shell')) $('.shell').inert = false;
    document.body.style.overflow = '';
    if (window.scrollX) window.scrollTo(0, window.scrollY);
  }
  mobileViewport.addEventListener('change', () => {
    closeNav();
    const region = $('#notifications');
    const limit = mobileViewport.matches ? 2 : 3;
    while (region.children.length > limit) region.firstElementChild.remove();
  });
  function openModal(title, body, submit, handler, danger = false) {
    if (modal.open) return;
    returnFocus = document.activeElement;
    modal.innerHTML = `<div class="modal-heading"><h2 id="modal-title">${esc(title)}</h2><button class="icon-btn" type="button" data-action="close-modal" aria-label="Close dialog">${icon('x-lg')}</button></div><form id="dialog-form"><div class="modal-content-body">${errorBox()}${body}<div class="modal-footer-actions"><button class="btn btn-outline-secondary" type="button" data-action="close-modal">Cancel</button><button class="btn btn-${danger ? 'danger' : 'primary'}" type="submit">${icon(danger ? 'trash3' : 'check-lg')} ${esc(submit)}</button></div></div></form>`;
    modal.showModal();
    const form = $('#dialog-form');
    form.onsubmit = event => { event.preventDefault(); submitForm(form, () => handler(new FormData(form)), true); };
    requestAnimationFrame(() => $('input:not([type="checkbox"]),select,textarea,button[type="submit"]', form)?.focus());
  }
  function closeModal() {
    if (!modal.open) return;
    if ($('#dialog-form')?.dataset.busy === 'true') return;
    modal.close();
    if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true });
    else $('#main')?.focus({ preventScroll: true });
  }
  modal.addEventListener('cancel', event => { event.preventDefault(); closeModal(); });
  modal.addEventListener('click', event => { if (event.target === modal) { const bounds = modal.getBoundingClientRect(); if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) closeModal(); } });

  async function submitForm(form, callback, dialog = false) {
    if (form.dataset.busy === 'true' || state.busy) return;
    if (!form.reportValidity()) return;
    form.dataset.busy = 'true'; state.busy = true;
    const submit = $('button[type="submit"]', form); const original = submit.innerHTML;
    const controls = [...form.querySelectorAll('button')];
    const oldDisabled = controls.map(control => control.disabled);
    controls.forEach(control => control.disabled = true);
    submit.innerHTML = '<span class="spinner-border spinner-border-sm" aria-hidden="true"></span> Saving...';
    const errorNode = $('[data-form-error]', form); errorNode.textContent = '';
    try {
      await callback();
      form.dataset.busy = 'false';
      if (dialog && modal.open) closeModal();
    } catch (error) {
      if (form.isConnected) { errorNode.textContent = error.message; errorNode.focus(); }
      else if (error.status !== 401) toast(error.message, true);
    } finally {
      state.busy = false; form.dataset.busy = 'false';
      controls.forEach((control, index) => control.disabled = oldDisabled[index]);
      submit.innerHTML = original;
    }
  }
  async function changed(path, method, body, message) {
    await api(path, method, body);
    toast(message);
    // A successful write must not be retried just because its follow-up read failed.
    try { await refresh(); } catch (error) { if (state.user) toast(`Saved, but the workspace could not refresh. ${error.message}`, true); }
  }
  function profilePayload(careerId = state.user.careerId) {
    const { name, course, college, currentYear } = state.user;
    return { name, course: course || '', college: college || '', currentYear: currentYear || '', careerId: Number(careerId), ...(state.user.timezone ? { timezone: state.user.timezone } : {}) };
  }
  function skillDialog(id) {
    const item = id ? state.data.skills.find(skill => eq(skill.id, id)) : null;
    if (!state.catalog.length) return toast('No skills are available in the catalog yet.', true);
    openModal(item ? 'Update skill proficiency' : 'Add to your skill toolkit', `${selectField('Skill', 'skillId', skillOptions(), item?.skill.id || '')}${selectField('Current proficiency', 'level', options(levels), item?.level || 'Beginner')}<p class="form-text mb-0">Choose the level that best reflects your current experience.</p>`, item ? 'Update skill' : 'Add skill', data => changed('/api/skills', 'POST', { skillId: Number(item?.skill.id || data.get('skillId')), level: data.get('level') }, 'Your skill toolkit has been updated.'));
    if (item) $('#field-skillId').disabled = true;
  }
  function projectDialog(id) {
    const project = state.data.projects.find(item => eq(item.id, id));
    openModal(project ? 'Edit portfolio project' : 'Add portfolio project', `${field('Project name', 'name', project?.name, 'text', true, 150)}${textarea('Description', 'description', project?.description)}${field('Technologies', 'technology', project?.technology)}${field('GitHub / repository URL', 'githubUrl', project?.githubUrl, 'url', false, 1000)}${selectField('Status', 'status', [{ value: 'IN_PROGRESS', label: 'In progress' }, { value: 'COMPLETED', label: 'Completed' }], project?.status || 'IN_PROGRESS')}`, project ? 'Save project' : 'Add project', data => {
      const body = Object.fromEntries(data); validateUrlField(body.githubUrl, true);
      body.name = body.name.trim();
      if (!body.name) throw new Error('Enter a project name.');
      return changed(`/api/projects${project ? `/${encodeURIComponent(project.id)}` : ''}`, project ? 'PUT' : 'POST', body, project ? 'Portfolio project updated.' : 'Project added to your portfolio.');
    });
  }
  function validateUrlField(value, optional = false) { if (!(optional && !value) && !safeUrl(value)) throw new Error('Enter a valid http or https URL.'); }
  function careerDialog(id) {
    const career = state.careers.find(item => eq(item.id, id));
    openModal(career ? 'Edit career path' : 'Add career path', `${field('Career name', 'name', career?.name, 'text', true, 150)}${textarea('Description', 'description', career?.description, true)}<fieldset><legend class="form-label fs-6">Required skills</legend><div class="checkbox-list">${state.catalog.map(skill => `<label><input class="form-check-input mt-0" type="checkbox" name="skillIds" value="${esc(skill.id)}" ${career?.skills.some(item => eq(item.id, skill.id)) ? 'checked' : ''}> ${esc(skill.name)}</label>`).join('')}</div></fieldset>`, 'Save career', data => {
      const skillIds = data.getAll('skillIds').map(Number);
      if (!skillIds.length) throw new Error('Select at least one required skill.');
      const name = data.get('name').trim(), description = data.get('description').trim();
      if (!name || !description) throw new Error('Enter a career name and description.');
      return changed(`/api/admin/careers${career ? `/${encodeURIComponent(career.id)}` : ''}`, career ? 'PUT' : 'POST', { name, description, skillIds }, 'Career path saved.');
    });
  }
  function resourceDialog(id) {
    const resource = state.data.resources.find(item => eq(item.id, id));
    const types = [...new Set(['Documentation', 'Course', 'Video', 'Article', 'Practice', ...state.data.resources.map(item => item.type)])];
    openModal(resource ? 'Edit learning resource' : 'Add learning resource', `${selectField('Related skill', 'skillId', skillOptions(), resource?.skill?.id)}${field('Resource title', 'title', resource?.title, 'text', true)}${field('Resource URL', 'url', resource?.url, 'url', true, 1000)}${selectField('Resource type', 'type', options(types), resource?.type || 'Documentation')}`, 'Save resource', data => {
      const body = { skillId: Number(data.get('skillId')), title: data.get('title').trim(), url: data.get('url').trim(), type: data.get('type') };
      if (!body.title) throw new Error('Enter a resource title.');
      validateUrlField(body.url); return changed(`/api/admin/resources${resource ? `/${encodeURIComponent(resource.id)}` : ''}`, resource ? 'PUT' : 'POST', body, 'Learning resource saved.');
    });
  }
  function confirmDelete(type, id) {
    const collections = { skill: state.data.skills, project: state.data.projects, career: state.careers, resource: state.data.resources };
    const item = collections[type].find(value => eq(value.id, id)); if (!item) return;
    const name = item.name || item.title || item.skill?.name;
    const path = { skill: '/api/skills', project: '/api/projects', career: '/api/admin/careers', resource: '/api/admin/resources' }[type];
    openModal(`Remove ${type}?`, `<p>Remove <strong>${esc(name)}</strong> ${type === 'skill' ? 'from your toolkit? Your skill gap and roadmap will be recalculated.' : 'from CareerForge? This cannot be undone.'}</p>`, 'Remove', () => changed(`${path}/${encodeURIComponent(id)}`, 'DELETE', undefined, `${type[0].toUpperCase() + type.slice(1)} removed.`), true);
  }

  async function authenticate(form, demo = '') {
    const data = new FormData(form); const register = !demo && form.dataset.mode === 'register';
    const body = demo ? { email: `${demo}@careerforge.dev`, password: 'Career123!' } : { email: data.get('email').trim(), password: data.get('password'), ...(register ? { name: data.get('name').trim() } : {}) };
    if (register && !body.name) throw new Error('Enter your full name.');
    await api(`/api/auth/${register ? 'register' : 'login'}`, 'POST', body);
    if (register) {
      try { const result = await api('/api/me'); state.user = result?.user || result; }
      catch (error) { if (error.status !== 401) throw error; await api('/api/auth/login', 'POST', { email: body.email, password: body.password }); }
    }
    if (!state.user) { const result = await api('/api/me'); state.user = result?.user || result; }
    if (!state.user?.id) { state.user = null; throw new Error('The server did not return a valid session. Please sign in again.'); }
    sessionRevision++; location.hash = 'dashboard';
    await loadWorkspace();
    if (register) toast('Welcome to CareerForge. Complete your profile to get started.');
  }

  document.addEventListener('submit', event => {
    const form = event.target;
    if (form.id === 'auth-form') { event.preventDefault(); submitForm(form, () => authenticate(form)); }
    if (form.id === 'profile-form') {
      event.preventDefault(); submitForm(form, () => {
        const body = Object.fromEntries(new FormData(form)); body.name = body.name.trim(); body.careerId = Number(body.careerId);
        if (!body.name) throw new Error('Enter your full name.');
        return changed('/api/profile', 'PUT', body, 'Profile saved. Your career plan is up to date.');
      });
    }
  });
  document.addEventListener('input', event => { if (event.target.id === 'view-search') { state.query = event.target.value; updateResults(); } });
  document.addEventListener('change', async event => {
    const target = event.target;
    if (target.id === 'view-filter') { state.filter = target.value; updateResults(); }
    if (target.dataset.step) {
      const step = state.data.roadmap.find(item => eq(item.id, target.dataset.step));
      if (!step || state.busy) { if (step) target.value = step.status; return; }
      target.disabled = true; state.busy = true;
      try { await changed(`/api/roadmap/${encodeURIComponent(step.id)}`, 'PUT', { status: target.value }, 'Roadmap progress updated.'); }
      catch (error) { target.value = step.status; if (error.status !== 401) toast(error.message, true); }
      finally { target.disabled = false; state.busy = false; }
    }
  });
  document.addEventListener('click', async event => {
    const tab = event.target.closest('[data-admin-tab]');
    if (tab && isAdmin()) { state.adminTab = tab.dataset.adminTab; state.query = ''; renderView(); $(`[data-admin-tab="${state.adminTab}"]`)?.focus(); return; }
    const button = event.target.closest('[data-action]'); if (!button) return;
    const action = button.dataset.action; const id = button.dataset.id;
    if (action === 'password') { const input = $('#password'); const show = input.type === 'password'; input.type = show ? 'text' : 'password'; button.innerHTML = icon(show ? 'eye-slash' : 'eye'); button.setAttribute('aria-label', show ? 'Hide password' : 'Show password'); button.title = show ? 'Hide password' : 'Show password'; return; }
    if (action === 'close-modal') return closeModal();
    if (action === 'open-nav') { $('.sidebar').inert = false; $('.sidebar').classList.add('open'); $('.sidebar-backdrop').classList.add('visible'); button.setAttribute('aria-expanded', 'true'); $('.shell').inert = true; document.body.style.overflow = 'hidden'; $('.side-nav a.active')?.focus(); return; }
    if (action === 'close-nav') return closeNav();
    if (action.startsWith('demo-')) {
      if (state.busy) return;
      const form = $('#auth-form');
      const demo = action === 'demo-admin' ? 'admin' : 'student';
      $('#email').value = `${demo}@careerforge.dev`; $('#password').value = 'Career123!';
      if ($('#field-name')) $('#field-name').value = 'Demo Student';
      return submitForm(form, () => authenticate(form, demo));
    }
    if (!state.user || state.busy) return;
    if (action.includes('career') && action !== 'select-career' && !isAdmin()) return;
    if (action.includes('resource') && action !== 'learn-skill' && !isAdmin()) return;
    if (action === 'add-skill' || action === 'edit-skill') return skillDialog(id);
    if (action === 'add-project' || action === 'edit-project') return projectDialog(id);
    if (action === 'add-career' || action === 'edit-career') return careerDialog(id);
    if (action === 'add-resource' || action === 'edit-resource') return resourceDialog(id);
    if (action.startsWith('delete-')) return confirmDelete(action.slice(7), id);
    if (action === 'learn-skill') {
      const skill = state.catalog.find(item => eq(item.id, id));
      state.query = skill?.name || ''; state.filter = ''; state.route = 'resources';
      if (location.hash === '#resources') renderView(); else location.hash = 'resources';
      return;
    }
    if (action === 'select-career') {
      const career = state.careers.find(item => eq(item.id, id)); if (!career) return;
      return openModal('Change your career goal?', `<p>Set your goal to <strong>${esc(career.name)}</strong>? Your skill gap and learning roadmap will update to match this path.</p>`, 'Choose career', () => changed('/api/profile', 'PUT', profilePayload(id), 'Career goal updated.'));
    }
    if (action === 'logout') {
      state.busy = true; button.disabled = true;
      try { await api('/api/auth/logout', 'POST'); endSession(); toast('You have signed out.'); }
      catch (error) { toast(error.message, true); }
      finally { state.busy = false; button.disabled = false; }
    }
    if (action === 'refresh' || action === 'retry') {
      if (v2.views[state.route]) { v2.reload(state.route); return; }
      state.busy = true; button.disabled = true;
      try { if (action === 'retry' && !state.data) await loadWorkspace(); else { await refresh(); toast('Workspace refreshed.'); } }
      catch (error) { if (state.user) toast(error.message, true); }
      finally { state.busy = false; button.disabled = false; }
    }
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !modal.open && $('.sidebar.open')) { closeNav(); $('[data-action="open-nav"]')?.focus(); }
    if (event.target.matches('[data-admin-tab]') && ['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
      event.preventDefault(); const tabs = [...document.querySelectorAll('[data-admin-tab]')];
      const index = tabs.indexOf(event.target); const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
      tabs[next].click();
    }
    if (event.key === 'Tab' && $('.sidebar.open') && !modal.open) {
      const links = [...$('.sidebar').querySelectorAll('a,button')]; const first = links[0], last = links[links.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });
  window.addEventListener('hashchange', () => { if (modal.open && $('#dialog-form')?.dataset.busy !== 'true') closeModal(); state.user ? renderView(true) : renderAuth(); });
  async function boot() {
    try {
      const result = await api('/api/me'); state.user = result?.user || result;
      if (!state.user?.id) throw new ApiError('Please sign in to continue.', 401);
      sessionRevision++; await loadWorkspace();
    } catch (error) {
      state.user = null;
      if (location.hash !== '#register' && !v2.isRecovery()) location.hash = 'login';
      renderAuth(error.status === 401 ? '' : error.message);
    }
  }
  boot();
})();
