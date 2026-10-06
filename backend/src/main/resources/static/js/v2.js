/* Route-local V2 data and drafts. The V1 closure owns authentication and shared UI. */
window.CareerForgeV2 = function (ui) {
  'use strict';
  const { $, state, api, esc, icon, eq, list, badge, statuses, field, textarea, selectField, options, skillOptions, careerOptions, errorBox, head, footer, empty, progress, actionButton, iconButton, openModal, toast, submitForm, render, refresh } = ui;
  let cache = {}, pending = {}, errors = {}, epoch = 0, activeRoute = '';
  let quiz = null, result = null, quizIndex = 0, answers = {};
  let resumeDraft = null, resumeDirty = false, mentorDraft = '';
  let week = '', compareIds = null, config = null, configError = '', configTask = null;
  const recoveryRoutes = ['forgot-password', 'reset-password', 'verification', 'verify-email'];
  const routeName = () => location.hash.slice(1).split('?')[0];
  const isRecovery = () => recoveryRoutes.includes(routeName());
  const pathId = value => encodeURIComponent(value);
  const dateLabel = value => { if (!value) return 'Not available'; const date = new Date(value); return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString(undefined, { dateStyle: 'medium', ...(String(value).includes('T') ? { timeStyle: 'short' } : {}) }); };
  const pct = value => Math.max(0, Math.min(100, Number(value) || 0));
  const checkbox = (name, label, checked) => `<label class="check-line"><input type="checkbox" class="form-check-input" name="${esc(name)}" ${checked ? 'checked' : ''}>${esc(label)}</label>`;
  const buttons = content => `<div class="v2-actions">${content}</div>`;
  const panel = (title, content) => `<section class="v2-section"><h2>${esc(title)}</h2>${content}</section>`;
  const loading = () => '<div class="loading-area" role="status" aria-busy="true"><span class="spinner-border text-success" aria-hidden="true"></span>Loading...</div>';
  function localMonday() {
    let parts;
    try { parts = new Intl.DateTimeFormat('en-CA', { timeZone: state.user?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date()); } catch { parts = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date()); }
    const part = name => parts.find(item => item.type === name).value;
    const date = new Date(`${part('year')}-${part('month')}-${part('day')}T12:00:00Z`);
    date.setUTCDate(date.getUTCDate() - (date.getUTCDay() + 6) % 7);
    return date.toISOString().slice(0, 10);
  }
  function endpoint(route) {
    if (route === 'planner') { week ||= localMonday(); return `/api/planner?week=${pathId(week)}`; }
    if (route === 'compare') {
      compareIds ||= [state.user?.careerId || state.careers[0]?.id, state.careers.find(item => !eq(item.id, state.user?.careerId || state.careers[0]?.id))?.id];
      if (!compareIds.every(Boolean)) return '';
      return `/api/careers/compare?left=${pathId(compareIds[0])}&right=${pathId(compareIds[1])}`;
    }
    return `/api/${route === 'history' ? 'progress' : route}`;
  }
  function load(route, force = false) {
    const path = endpoint(route); if (!path) return;
    if (pending[route]?.path === path && !force) return pending[route].task;
    if (errors[route] && !force) return;
    if (!force && cache[route]?.path === path) return;
    const generation = epoch;
    delete errors[route];
    const entry = { path };
    pending[route] = entry;
    entry.task = (async () => {
      try {
        const data = await api(path);
        if (epoch !== generation || pending[route] !== entry) return;
        cache[route] = { path, data };
        if (route === 'resume' && !resumeDraft) resumeDraft = { ...data.profile };
      } catch (error) { if (epoch === generation && pending[route] === entry) errors[route] = error.message; }
      finally {
        if (epoch === generation && pending[route] === entry) { delete pending[route]; if (state.user && routeName() === route) render(); }
      }
    })();
    return entry.task;
  }
  function reload(route) { load(route, true); render(); }
  function view(route, title, description, content, actions = '') {
    load(route);
    const data = cache[route]?.path === endpoint(route) ? cache[route].data : null;
    return `${head(title, description, actions)}${errors[route] ? `<div class="error-box" role="alert">${esc(errors[route])} ${actionButton('v2-retry', 'Retry', 'arrow-clockwise', route, 'outline-secondary')}</div>` : ''}${pending[route] ? '<div class="v2-loading" role="status">Updating...</div>' : ''}${data ? content(data) : errors[route] ? '' : route === 'compare' && !endpoint(route) ? empty('Choose two career paths', 'Two careers are needed for comparison.', '<a href="#careers">View career paths</a>', 'compass') : loading()}${footer()}`;
  }
  async function mutate(route, path, method, body, message) {
    const generation = epoch;
    const data = await api(path, method, body);
    if (generation !== epoch) return;
    toast(message);
    await load(route, true);
    if (route === 'planner' && body?.roadmapStepId && body.status === 'COMPLETED') {
      try { await refresh(); } catch (error) { if (state.user) toast(`Task saved. ${error.message}`, true); }
    }
    if (routeName() === route) render();
    return data;
  }
  function assessmentView() {
    if (quiz) return `${head(quiz.skillName, 'Skill assessment', badge(`Expires ${dateLabel(quiz.expiresAt)}`, 'blue'))}${quizView()}${footer()}`;
    return view('assessments', 'Skill assessments', 'Measure your knowledge and review your next learning steps.', data => `${result ? assessmentResult() : ''}<div class="cards-grid">${list(data.skills).map(skill => `<article class="panel item-card"><div class="item-icon">${icon('clipboard-check')}</div><h2>${esc(skill.name)}</h2><div class="tags">${badge(`${skill.questionCount} questions`)}${badge(`${skill.attemptCount || 0} attempts`)}${skill.bestScore != null ? badge(`Best ${skill.bestScore}%`, 'green') : ''}</div>${actionButton('v2-start', 'Start assessment', 'play', skill.skillId)}</article>`).join('') || empty('No assessments yet', 'Skill assessments will appear here when available.', '', 'clipboard-check')}</div>${panel('Recent attempts', list(data.attempts).map(attemptRow).join('') || empty('No attempts yet', 'Complete an assessment to record your first score.'))}`);
  }
  const attemptRow = attempt => `<div class="list-row"><div class="row-main"><span class="metric-icon">${icon('check2-circle')}</span><div><strong>${esc(attempt.skillName)}</strong><small>${esc(dateLabel(attempt.completedAt))} &middot; ${esc(attempt.correct)}/${esc(attempt.total)} correct</small></div></div>${badge(`${attempt.score}%`, 'green')}</div>`;
  function quizView() {
    const questions = list(quiz.questions), question = questions[quizIndex];
    if (!question) return empty('No questions returned', 'Please try again later.', actionButton('v2-abandon', 'Close assessment', 'x-lg'));
    const expired = quiz.expiresAt && Date.now() >= new Date(quiz.expiresAt).getTime();
    return `<div class="quiz-shell">${expired ? '<div class="error-box" role="alert">This assessment has expired. Start a new attempt.</div>' : ''}<div class="v2-meta"><span>Question ${quizIndex + 1} of ${questions.length}</span><span>${Object.keys(answers).length} answered</span></div>${progress((quizIndex + 1) / questions.length * 100, 'Assessment progress')}<form id="quiz-form" class="quiz-form">${errorBox()}<fieldset><legend>${esc(question.prompt)}</legend><div class="quiz-options">${list(question.options).map((option, index) => `<label class="quiz-option"><input type="radio" name="quiz-answer" value="${index}" data-question="${esc(question.id)}" ${answers[question.id] === index ? 'checked' : ''} ${expired ? 'disabled' : ''}><span>${esc(option)}</span></label>`).join('')}</div></fieldset><div class="v2-actions quiz-controls">${iconButton('v2-quiz-prev', 'Previous question', 'arrow-left', '')}<span>${quizIndex + 1} / ${questions.length}</span>${quizIndex < questions.length - 1 ? iconButton('v2-quiz-next', 'Next question', 'arrow-right', '') : `<button type="submit" class="btn btn-primary" ${expired ? 'disabled' : ''}>${icon('check-lg')} Submit assessment</button>`}</div></form>${actionButton('v2-abandon', 'End attempt', 'x-lg', '', 'outline-secondary')}</div>`;
  }
  function assessmentResult() {
    return panel('Assessment results', `${attemptRow(result)}${list(result.feedback).map(item => { const question = list(result.questions).find(q => eq(q.id, item.questionId)); return `<div class="feedback-row"><strong>${icon(item.correct ? 'check-circle' : 'x-circle')} ${esc(question?.prompt || `Question ${item.questionId}`)}</strong><p>${esc(item.explanation)}</p>${!item.correct && question ? `<small>Correct answer: ${esc(question.options[item.correctOptionIndex])}</small>` : ''}</div>`; }).join('')}${actionButton('v2-dismiss-result', 'Close results', 'x-lg', '', 'outline-secondary')}`);
  }
  function plannerView() {
    week ||= localMonday();
    return view('planner', 'Weekly planner', 'Make room for the skills and milestones that matter.', data => `<div class="v2-meta"><span>${esc(dateLabel(data.weekStart))} &ndash; ${esc(dateLabel(data.weekEnd))}</span>${badge(data.timezone || '')}</div><div class="planner-summary"><strong>${esc(data.completedMinutes || 0)} <small>minutes completed</small></strong><strong>${esc(data.plannedMinutes || 0)} <small>minutes planned</small></strong><div>${progress(data.plannedMinutes ? pct(data.completedMinutes / data.plannedMinutes * 100) : 0, 'Planned minutes completed')}</div></div>${panel('Weekly goals', list(data.goals).map(goal => `<div class="list-row"><div class="row-main"><div><strong>${esc(goal.title)}</strong><small>${esc(goal.targetMinutes)} minutes this week</small></div></div>${buttons(iconButton('v2-edit-goal', `Edit ${goal.title}`, 'pencil', goal.id) + iconButton('v2-delete-goal', `Delete ${goal.title}`, 'trash3', goal.id, true))}</div>`).join('') || empty('Set your weekly intention', 'Add a goal for this week.', actionButton('v2-add-goal', 'Add goal')))}${panel('This week', taskRows(data.tasks))}${list(data.overdue).length ? panel('Overdue tasks', taskRows(data.overdue)) : ''}`, buttons(`<div class="week-picker">${iconButton('v2-week-prev', 'Previous week', 'chevron-left', '')}<label class="visually-hidden" for="planner-week">Week beginning</label><input type="date" id="planner-week" class="form-control" value="${esc(week)}">${iconButton('v2-week-next', 'Next week', 'chevron-right', '')}</div>${actionButton('v2-add-goal', 'Goal', 'plus-lg', '', 'outline-secondary')}${actionButton('v2-add-task', 'Task')}`));
  }
  function taskRows(tasks) {
    return list(tasks).map(task => `<div class="list-row planner-task"><div class="row-main"><div><strong>${esc(task.title)}</strong><small>${esc(dateLabel(task.dueDate))} &middot; ${esc(task.estimatedMinutes)} min${task.skillId ? ` &middot; ${esc(state.catalog.find(s => eq(s.id, task.skillId))?.name || 'Skill')}` : ''}</small></div></div><div class="v2-actions"><select class="form-select task-status" data-task-status="${esc(task.id)}" aria-label="Status for ${esc(task.title)}">${Object.entries(statuses).map(([value, label]) => `<option value="${value}" ${task.status === value ? 'selected' : ''}>${label}</option>`).join('')}</select>${iconButton('v2-edit-task', `Edit ${task.title}`, 'pencil', task.id)}${iconButton('v2-delete-task', `Delete ${task.title}`, 'trash3', task.id, true)}</div></div>`).join('') || empty('A clear week ahead', 'Add a task from your learning plan.', actionButton('v2-add-task', 'Add task'), 'calendar3');
  }
  function plannerItem(kind, id) { const data = cache.planner?.data; return list(kind === 'goal' ? data?.goals : [...list(data?.tasks), ...list(data?.overdue)]).find(item => eq(item.id, id)); }
  function plannerDialog(kind, id) {
    const item = id ? plannerItem(kind, id) : null;
    const stepOptions = [{ value: '', label: 'No roadmap milestone' }, ...list(state.data?.roadmap).map(step => ({ value: step.id, label: step.title }))];
    openModal(`${item ? 'Edit' : 'Add'} ${kind}`, `${field('Title', 'title', item?.title, 'text', true, 200)}${field('Week beginning', 'weekStart', item?.weekStart || week, 'date', true)}${kind === 'goal' ? field('Target minutes', 'targetMinutes', item?.targetMinutes || 120, 'number', true) : `${selectField('Skill (optional)', 'skillId', [{ value: '', label: 'No linked skill' }, ...skillOptions().slice(1)], item?.skillId || '', false)}${selectField('Roadmap milestone (optional)', 'roadmapStepId', stepOptions, item?.roadmapStepId || '', false)}${field('Due date', 'dueDate', item?.dueDate || week, 'date', true)}${field('Estimated minutes', 'estimatedMinutes', item?.estimatedMinutes || 30, 'number', true)}${selectField('Status', 'status', Object.entries(statuses).map(([value, label]) => ({ value, label })), item?.status || 'NOT_STARTED')}`}`, 'Save', data => {
      const body = Object.fromEntries(data); body.title = body.title.trim();
      if (!body.title) throw new Error('Enter a title.');
      const name = kind === 'goal' ? 'targetMinutes' : 'estimatedMinutes'; body[name] = Number(body[name]);
      if (!Number.isInteger(body[name]) || body[name] < 1) throw new Error('Enter a positive whole number of minutes.');
      if (kind === 'task') { body.skillId = body.skillId ? Number(body.skillId) : null; body.roadmapStepId = body.roadmapStepId ? Number(body.roadmapStepId) : null; }
      return mutate('planner', `/api/planner/${kind}s${item ? `/${pathId(item.id)}` : ''}`, item ? 'PUT' : 'POST', body, `${kind === 'goal' ? 'Goal' : 'Task'} saved.`);
    });
    const minutes = $(`#field-${kind === 'goal' ? 'targetMinutes' : 'estimatedMinutes'}`); minutes.min = '1'; minutes.max = kind === 'goal' ? '10080' : '1440'; minutes.step = '1';
  }
  const resumeFields = ['headline', 'summary', 'phone', 'location', 'website', 'education', 'experience', 'achievements'];
  const resumeSections = ['Skills', 'Projects', 'Education', 'Experience', 'Achievements'];
  function resumeView() {
    return view('resume', 'Resume builder', 'Bring your skills, projects, and experience together.', data => {
      const p = resumeDraft || data.profile || {};
      return `<div class="resume-layout"><form id="resume-form">${errorBox()}<div class="form-grid">${field('Professional headline', 'headline', p.headline, 'text', false, 200)}${field('Phone', 'phone', p.phone, 'tel', false, 40)}${field('Location', 'location', p.location)}${field('Website', 'website', p.website, 'url', false, 1000)}<div class="full">${textarea('Summary', 'summary', p.summary)}</div><div class="full">${textarea('Education', 'education', p.education)}</div><div class="full">${textarea('Experience', 'experience', p.experience)}</div><div class="full">${textarea('Achievements', 'achievements', p.achievements)}</div></div><fieldset class="resume-includes"><legend>Include sections</legend>${resumeSections.map(section => checkbox(`include${section}`, section, p[`include${section}`] !== false)).join('')}</fieldset><div class="form-footer"><span id="resume-save-state" role="status">${resumeDirty ? 'Unsaved changes' : 'All changes saved'}</span><button class="btn btn-primary" type="submit">${icon('check-lg')} Save resume</button></div></form><aside class="resume-preview" aria-label="Resume preview" id="resume-preview">${resumePreview(data, p)}</aside></div>`;
    }, actionButton('v2-pdf', 'Download PDF', 'download'));
  }
  function resumePreview(data, p) {
    const user = data.user || state.user;
    const block = (title, body) => body ? `<section><h3>${esc(title)}</h3>${body}</section>` : '';
    return `<h2>${esc(user.name)}</h2><p class="resume-headline">${esc(p.headline)}</p><p class="resume-contact">${[user.email, p.phone, p.location, p.website].filter(Boolean).map(esc).join(' &middot; ')}</p>${block('Summary', p.summary ? `<p>${esc(p.summary)}</p>` : '')}${p.includeSkills !== false ? block('Skills', list(data.skills).map(item => `<span class="resume-skill">${esc(item.skill?.name || item.name)}${item.level ? ` (${esc(item.level)})` : ''}</span>`).join('')) : ''}${p.includeProjects !== false ? block('Projects', list(data.projects).map(project => `<div class="resume-project"><strong>${esc(project.name)}</strong><p>${esc(project.description)}</p><small>${esc(project.technology)}</small></div>`).join('')) : ''}${['Education', 'Experience', 'Achievements'].map(section => p[`include${section}`] !== false ? block(section, p[section.toLowerCase()] ? `<p>${esc(p[section.toLowerCase()])}</p>` : '') : '').join('')}`;
  }
  function readResume(form) {
    const data = new FormData(form), body = {};
    resumeFields.forEach(name => body[name] = String(data.get(name) || '').trim());
    resumeSections.forEach(section => body[`include${section}`] = data.has(`include${section}`));
    return body;
  }
  async function saveResume() {
    const generation = epoch, draft = { ...resumeDraft };
    await api('/api/resume', 'PUT', draft);
    if (generation !== epoch) return;
    resumeDirty = JSON.stringify(resumeDraft) !== JSON.stringify(draft);
    toast('Resume saved.');
    await load('resume', true);
  }
  async function downloadPdf() {
    if (resumeDirty) {
      openModal('Save resume before downloading?', '<p>Your PDF will use your saved resume.</p>', 'Save and download', async () => { await saveResume(); await fetchPdf(); });
      return;
    }
    await fetchPdf();
  }
  async function fetchPdf() {
    const generation = epoch;
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch('/api/resume/pdf', { credentials: 'same-origin', headers: { Accept: 'application/pdf' }, signal: controller.signal });
      if (!response.ok) { if (response.status === 401) await api('/api/me'); throw new Error(`Could not download PDF (${response.status}). Please retry.`); }
      if (!response.headers.get('content-type')?.includes('application/pdf')) throw new Error('The server did not return a PDF.');
      const blob = await response.blob(); if (epoch !== generation) return;
      const url = URL.createObjectURL(blob), link = document.createElement('a'); link.href = url; link.download = 'CareerForge-resume.pdf'; document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) { throw new Error(error.name === 'AbortError' ? 'PDF download timed out. Please retry.' : error.message); }
    finally { clearTimeout(timer); }
  }
  function compareView() {
    const path = endpoint('compare');
    const selection = `<form id="compare-form" class="compare-controls">${selectField('First career', 'left', careerOptions(), compareIds?.[0])}${selectField('Second career', 'right', careerOptions(), compareIds?.[1])}<button type="submit" class="btn btn-primary">${icon('layout-split')} Compare</button>${errorBox()}</form>`;
    return view('compare', 'Compare career paths', 'Explore the skills and learning time behind two directions.', data => `${selection}<div class="split">${[data.left, data.right].map(side => `<section class="v2-section"><h2>${esc(side.career?.name)}</h2><p>${esc(side.career?.description)}</p><div class="compare-stats"><strong>${esc(side.gap?.readiness ?? 0)}%<small>readiness</small></strong><strong>${esc(side.estimatedHours)}<small>estimated hours</small></strong></div>${progress(pct(side.gap?.readiness), 'Career readiness')}<h3 class="mt-4">Skills to build</h3><div class="tags">${list(side.gap?.missing).map(skill => badge(skill.name, 'blue')).join('') || badge('All requirements covered', 'green')}</div><h3 class="mt-4">Acquired skills</h3><div class="tags">${list(side.gap?.completed).map(skill => badge(skill.name, 'green')).join('') || badge('No acquired skills yet')}</div></section>`).join('')}</div>${panel('Shared skills', `<div class="tags">${list(data.sharedSkills).map(skill => badge(skill.name)).join('') || '<p>No shared skill requirements.</p>'}</div><p class="mt-3 text-secondary">${esc(data.estimateExplanation)}</p>`)}`) + (!path ? selection : '');
  }
  function recommendationsView() {
    return view('recommendations', 'Project recommendations', 'Turn your learning into portfolio experience.', data => `<div class="cards-grid">${list(data).map(item => `<article class="panel item-card"><div class="v2-meta"><span class="item-icon">${icon('lightbulb')}</span>${badge(item.difficulty, 'blue')}</div><h2>${esc(item.title)}</h2><p>${esc(item.description)}</p><div class="tags">${list(item.technologies).map(tech => badge(tech)).join('')}</div>${list(item.prerequisites).length ? `<div><h3>Prerequisites</h3><ul>${item.prerequisites.map(prerequisite => `<li>${esc(prerequisite)}</li>`).join('')}</ul></div>` : ''}${list(item.milestones).length ? `<details><summary>Project milestones</summary><ol>${item.milestones.map(milestone => `<li>${esc(milestone)}</li>`).join('')}</ol></details>` : ''}<div class="tags">${list(item.careerIds).map(id => badge(state.careers.find(career => eq(career.id, id))?.name || `Career ${id}`)).join('')}</div><div class="card-bottom">${iconButton('v2-save-idea', item.saved ? 'Unsave project idea' : 'Save project idea', item.saved ? 'bookmark-fill' : 'bookmark', item.id)}${item.addedProjectId ? '<a class="btn btn-outline-secondary" href="#projects">View portfolio</a>' : actionButton('v2-portfolio', 'Add to portfolio', 'folder-plus', item.id)}</div></article>`).join('') || empty('No recommendations yet', 'Choose a career goal to explore relevant projects.', '<a href="#careers">Explore careers</a>', 'lightbulb')}`);
  }
  function historyView() {
    return view('history', 'Progress history', 'Your learning journey, one milestone at a time.', data => `<div class="streak-band"><span class="item-icon">${icon('fire')}</span><div><strong>${esc(data.streak || 0)} day streak</strong><p>${esc(data.streakDefinition)}</p></div>${badge(data.currentTimezone || '')}</div>${panel('Readiness trend', list(data.trend).length ? `<div class="trend-list">${list(data.trend).map(point => `<div class="trend-row"><div><strong>${esc(dateLabel(point.date))}</strong><small>${esc(state.careers.find(career => eq(career.id, point.careerId))?.name || 'Career readiness')}</small></div>${progress(pct(point.readiness), 'Readiness')}<span>${esc(point.readiness)}%</span></div>`).join('')}</div>` : empty('Your trend starts with progress', 'Recorded readiness changes will appear here.', '', 'graph-up'))}${panel('Activity timeline', list(data.events).map(event => `<div class="list-row"><div class="row-main"><span class="step-symbol">${icon('check2')}</span><div><strong>${esc(event.title)}</strong><small>${esc(dateLabel(event.occurredAt))} &middot; ${esc(event.type)}${event.careerId ? ` &middot; ${esc(state.careers.find(career => eq(career.id, event.careerId))?.name || 'Career')}` : ''}</small></div></div>${event.readiness != null ? badge(`${event.readiness}%`, 'green') : ''}</div>`).join('') || empty('No activity recorded yet', 'Your next milestone will appear here.', '<a href="#roadmap">View roadmap</a>', 'clock-history'))}`);
  }
  function mentorView() {
    return view('mentor', 'Career mentor', 'A place to work through your next career decision.', data => `<div class="mentor-status">${badge(data.available ? 'Available' : 'Unavailable', data.available ? 'green' : '')}<span>${esc(data.message || '')}</span>${data.available ? badge(`${data.remaining} of ${data.dailyLimit} messages remaining`, 'blue') : ''}</div><section class="mentor-thread" aria-label="Mentor conversation" aria-live="polite">${list(data.messages).map(message => `<article class="mentor-message ${message.role === 'user' ? 'from-user' : 'from-mentor'}"><div><strong>${esc(message.role === 'user' ? 'You' : message.role === 'assistant' ? 'Career mentor' : message.role)}</strong><small>${esc(dateLabel(message.createdAt))}</small></div><p>${esc(message.content)}</p></article>`).join('') || empty(data.available ? 'Start a conversation' : 'Mentor is unavailable', data.message || (data.available ? 'What is on your mind about your career?' : 'The mentor service is not configured.'), '', 'chat-dots')}</section><form id="mentor-form" class="mentor-compose">${errorBox()}${textarea('Message', 'message', mentorDraft, true)}<div class="form-footer"><span class="text-secondary">${esc(data.remaining ?? 0)} messages remaining</span><button type="submit" class="btn btn-primary" ${!data.available || Number(data.remaining) <= 0 ? 'disabled' : ''}>${icon('send')} Send</button></div></form>`, actionButton('v2-clear-mentor', 'Clear history', 'trash3', '', 'outline-secondary'));
  }
  async function ensureConfig() {
    if (config || configTask) return configTask;
    const generation = epoch;
    configTask = (async () => { try { const data = await api('/api/auth/config'); if (generation === epoch) { config = data; configError = ''; } } catch (error) { if (generation === epoch) configError = error.message; } finally { if (generation === epoch) configTask = null; } })();
    return configTask;
  }
  function emailStatus() { return config ? `${badge(config.emailAvailable ? config.localEmail ? 'Local email delivery' : 'Email available' : 'Email unavailable', config.emailAvailable ? 'green' : '')}<p class="text-secondary">${esc(config.message || '')}</p>` : configError ? `<div class="error-box" role="alert">${esc(configError)} ${actionButton('v2-config-retry', 'Retry', 'arrow-clockwise', '', 'outline-secondary')}</div>` : '<p role="status">Checking email availability...</p>'; }
  function renderRecovery() {
    const name = routeName(), reset = name === 'reset-password', verify = name === 'verify-email', direct = reset || verify;
    const token = new URLSearchParams(location.hash.split('?')[1] || '').get('token') || '';
    const title = reset ? 'Set a new password' : verify ? 'Verify your email' : name === 'verification' ? 'Request verification email' : 'Reset your password';
    document.title = `${title} | CareerForge`;
    $('#app').innerHTML = `<main id="main" class="auth-page"><div class="auth-wrap"><a class="brand auth-brand" href="${state.user ? '#profile' : '#login'}"><img src="assets/brand.png" alt="">CareerForge</a><section class="auth-panel"><h1>${esc(title)}</h1>${!direct ? `<div id="email-status">${emailStatus()}</div>` : ''}<form id="recovery-form" data-mode="${esc(name)}">${errorBox()}${direct ? token ? reset ? field('New password', 'password', '', 'password', true, 72) + field('Confirm password', 'confirmPassword', '', 'password', true, 72) : '<p>Confirm your email address to complete verification.</p>' : '<div class="error-box" role="alert">This link is missing its token. Request a new email.</div>' : field('Email address', 'email', state.user?.email || '', 'email', true)}<button type="submit" class="btn btn-primary submit" ${direct ? !token ? 'disabled' : '' : !config?.emailAvailable ? 'disabled' : ''}>${icon(reset ? 'lock' : 'envelope-check')} ${reset ? 'Save password' : verify ? 'Verify email' : 'Send email'}</button><p id="recovery-result" role="status" class="mt-3"></p></form><div class="recovery-links"><a href="${state.user ? '#profile' : '#login'}">${state.user ? 'Back to profile' : 'Back to sign in'}</a>${direct ? `<a href="#${reset ? 'forgot-password' : 'verification'}">Request a new email</a>` : ''}</div></section></div></main>`;
    if (!direct && !config && !configTask && !configError) ensureConfig().then(() => { if (routeName() === name) renderRecovery(); });
    if (reset && token) { $('#field-password').autocomplete = 'new-password'; $('#field-confirmPassword').autocomplete = 'new-password'; }
  }
  function profile() {
    if (!$('#field-timezone')) {
      const group = document.createElement('div'); group.innerHTML = field('Timezone', 'timezone', state.user.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone, 'text', true, 100); $('.form-grid', $('#profile-form')).append(group);
    }
    if (!$('#account-recovery')) {
      const section = document.createElement('section'); section.id = 'account-recovery'; section.className = 'v2-section account-recovery'; section.innerHTML = `<h2>Account security</h2><div class="tags">${badge(state.user.emailVerified ? 'Email verified' : 'Email not verified', state.user.emailVerified ? 'green' : 'amber')}</div><div id="email-status">${emailStatus()}</div>${buttons(`<a class="btn btn-outline-secondary" href="#forgot-password">${icon('lock')} Reset password</a>${!state.user.emailVerified ? `<a class="btn btn-outline-secondary" href="#verification">${icon('envelope-check')} Verify email</a>` : ''}`)}`; $('#profile-form').after(section);
    }
    if (!config && !configTask && !configError) ensureConfig().then(() => { if (routeName() === 'profile' && $('#email-status')) $('#email-status').innerHTML = emailStatus(); });
  }
  document.addEventListener('input', event => {
    if (event.target.closest('#resume-form')) {
      resumeDraft = readResume($('#resume-form')); resumeDirty = true;
      $('#resume-save-state').textContent = 'Unsaved changes';
      if ($('#refresh-label')) $('#refresh-label').textContent = 'Unsaved resume changes';
      $('#resume-preview').innerHTML = resumePreview(cache.resume.data, resumeDraft);
    }
    if (event.target.closest('#mentor-form')) mentorDraft = event.target.value;
  });
  document.addEventListener('change', async event => {
    const target = event.target;
    if (target.dataset.question) answers[target.dataset.question] = Number(target.value);
    if (target.id === 'planner-week' && target.value) { week = target.value; reload('planner'); }
    if (target.dataset.taskStatus) {
      if (state.busy) return;
      const task = plannerItem('task', target.dataset.taskStatus); if (!task) return;
      target.disabled = true; state.busy = true;
      try { await mutate('planner', `/api/planner/tasks/${pathId(task.id)}`, 'PUT', { title: task.title, skillId: task.skillId ?? null, roadmapStepId: task.roadmapStepId ?? null, dueDate: task.dueDate, estimatedMinutes: task.estimatedMinutes, status: target.value, weekStart: task.weekStart }, 'Task updated.'); }
      catch (error) { target.value = task.status; toast(error.message, true); }
      finally { target.disabled = false; state.busy = false; }
    }
  });
  document.addEventListener('submit', event => {
    const form = event.target;
    if (!['quiz-form', 'resume-form', 'compare-form', 'mentor-form', 'recovery-form'].includes(form.id)) return;
    event.preventDefault();
    submitForm(form, async () => {
      if (form.id === 'quiz-form') {
        const questions = list(quiz.questions);
        if (quiz.expiresAt && Date.now() >= new Date(quiz.expiresAt).getTime()) throw new Error('This assessment has expired. Start a new attempt.');
        const missing = questions.findIndex(question => answers[question.id] === undefined);
        if (missing !== -1) { quizIndex = missing; render(); throw new Error('Answer every question before submitting.'); }
        const generation = epoch, current = quiz;
        const attempt = await api(`/api/assessments/sessions/${pathId(quiz.id)}/submit`, 'POST', { answers: questions.map(question => ({ questionId: question.id, optionIndex: answers[question.id] })) });
        if (generation !== epoch) return;
        result = { ...(attempt.attempt || attempt), feedback: attempt.feedback, questions: current.questions }; quiz = null; answers = {}; quizIndex = 0;
        toast('Assessment submitted.'); await load('assessments', true); if (routeName() === 'assessments') render();
      }
      if (form.id === 'resume-form') { resumeDraft = readResume(form); if (resumeDraft.website && !/^https?:$/.test(new URL(resumeDraft.website).protocol)) throw new Error('Enter an http or https website URL.'); await saveResume(); }
      if (form.id === 'compare-form') { const data = new FormData(form); if (eq(data.get('left'), data.get('right'))) throw new Error('Choose two different careers.'); compareIds = [data.get('left'), data.get('right')]; reload('compare'); }
      if (form.id === 'mentor-form') {
        if (!cache.mentor?.data.available || Number(cache.mentor.data.remaining) <= 0) throw new Error(cache.mentor?.data.message || 'Mentor is unavailable or your daily limit has been reached.');
        const message = String(new FormData(form).get('message') || '').trim(); if (!message) throw new Error('Enter a message.');
        const generation = epoch;
        const data = await api('/api/mentor', 'POST', { message }); if (generation !== epoch) return;
        cache.mentor = { path: '/api/mentor', data }; mentorDraft = ''; if (routeName() === 'mentor') render();
      }
      if (form.id === 'recovery-form') {
        const mode = form.dataset.mode, data = new FormData(form);
        const token = new URLSearchParams(location.hash.split('?')[1] || '').get('token');
        if (mode === 'reset-password' && data.get('password') !== data.get('confirmPassword')) throw new Error('Passwords do not match.');
        if (['reset-password', 'verify-email'].includes(mode) && !token) throw new Error('Request a new email link.');
        if (!['reset-password', 'verify-email'].includes(mode) && !config?.emailAvailable) throw new Error(config?.message || 'Email is unavailable.');
        const body = mode === 'reset-password' ? { token, password: data.get('password') } : mode === 'verify-email' ? { token } : { email: String(data.get('email')).trim() };
        const response = await api(`/api/auth/${mode}`, 'POST', body);
        const message = response?.message || (mode === 'reset-password' ? 'Password updated. You can sign in with your new password.' : mode === 'verify-email' ? 'Email verified.' : 'If this address is eligible, an email will be sent.');
        if (mode === 'reset-password' || mode === 'verify-email') {
          history.replaceState(null, '', location.pathname + location.search + (state.user && mode !== 'reset-password' ? '#profile' : '#login'));
          form.reset();
          if (mode === 'reset-password') ui.endSession();
          else if (state.user) { const me = await api('/api/me'); state.user = me.user || me; await refresh(); }
          else $('#app').innerHTML = `<main id="main" class="auth-page"><div class="auth-panel"><h1>Email verified</h1><p>${esc(message)}</p><a class="btn btn-primary" href="#login">Sign in</a></div></main>`;
        } else if (form.isConnected) $('#recovery-result').textContent = message;
        toast(message);
      }
    });
  });
  document.addEventListener('click', async event => {
    const button = event.target.closest('[data-action^="v2-"]'); if (!button) return;
    const action = button.dataset.action.slice(3), id = button.dataset.id;
    if (state.busy) return;
    if (action === 'config-retry') { configError = ''; await ensureConfig(); if (isRecovery()) renderRecovery(); else if ($('#email-status')) $('#email-status').innerHTML = emailStatus(); return; }
    if (!state.user) return;
    if (action === 'retry') return reload(id);
    if (action === 'quiz-prev' || action === 'quiz-next') { quizIndex = Math.max(0, Math.min(quiz.questions.length - 1, quizIndex + (action === 'quiz-next' ? 1 : -1))); render(); return; }
    if (action === 'dismiss-result') { result = null; render(); return; }
    if (action === 'abandon') return openModal('End this attempt?', '<p>Your answers will be discarded. This attempt will not be submitted.</p>', 'End attempt', () => { quiz = null; answers = {}; quizIndex = 0; render(); });
    if (/^(add|edit)-(goal|task)$/.test(action)) { const [verb, kind] = action.split('-'); return plannerDialog(kind, verb === 'edit' ? id : ''); }
    if (/^delete-(goal|task)$/.test(action)) { const kind = action.split('-')[1], item = plannerItem(kind, id); return openModal(`Delete ${kind}?`, `<p>Delete <strong>${esc(item?.title)}</strong>?</p>`, 'Delete', () => mutate('planner', `/api/planner/${kind}s/${pathId(id)}`, 'DELETE', undefined, `${kind === 'goal' ? 'Goal' : 'Task'} removed.`), true); }
    if (action.startsWith('week-')) { const date = new Date(`${week}T12:00:00Z`); date.setUTCDate(date.getUTCDate() + (action === 'week-next' ? 7 : -7)); week = date.toISOString().slice(0, 10); reload('planner'); return; }
    if (action === 'clear-mentor') return openModal('Clear mentor history?', '<p>This removes your saved conversation.</p>', 'Clear history', () => mutate('mentor', '/api/mentor/history', 'DELETE', undefined, 'Mentor history cleared.'), true);
    button.disabled = true; state.busy = true;
    const generation = epoch;
    try {
      if (action === 'start') {
        const session = await api(`/api/assessments/${pathId(id)}/start`, 'POST');
        if (generation !== epoch) return;
        quiz = session; quizIndex = 0; answers = {}; result = null; if (routeName() === 'assessments') render();
      }
      if (action === 'pdf') await downloadPdf();
      if (action === 'save-idea') { const item = list(cache.recommendations?.data).find(item => eq(item.id, id)); await mutate('recommendations', `/api/recommendations/${pathId(id)}/save`, item.saved ? 'DELETE' : 'POST', undefined, item.saved ? 'Project idea unsaved.' : 'Project idea saved.'); }
      if (action === 'portfolio') { await mutate('recommendations', `/api/recommendations/${pathId(id)}/portfolio`, 'POST', undefined, 'Project added to your portfolio.'); try { await refresh(); } catch (error) { toast(`Project added. ${error.message}`, true); } }
    } catch (error) { if (error.status !== 401) toast(error.message, true); }
    finally { state.busy = false; button.disabled = false; }
  });
  window.addEventListener('beforeunload', event => { if (quiz || resumeDirty) { event.preventDefault(); event.returnValue = ''; } });
  return {
    views: { assessments: assessmentView, planner: plannerView, resume: resumeView, compare: compareView, recommendations: recommendationsView, history: historyView, mentor: mentorView },
    isRecovery, renderRecovery, profile, reload,
    afterRender() {
      if ($('#refresh-label')) $('#refresh-label').textContent = resumeDirty ? 'Unsaved resume changes' : 'All changes saved';
      if ($('#resume-form')) {
        $('#resume-form').querySelectorAll('textarea').forEach(input => input.maxLength = 4000);
        $('#field-phone').maxLength = 60; $('#field-location').maxLength = 200;
      }
    },
    activate(route) { if (route === activeRoute) return; activeRoute = route; if (['assessments', 'planner', 'resume', 'compare', 'recommendations', 'history', 'mentor'].includes(route)) load(route, true); },
    clear() { epoch++; activeRoute = ''; cache = {}; pending = {}; errors = {}; quiz = null; result = null; answers = {}; quizIndex = 0; resumeDraft = null; resumeDirty = false; mentorDraft = ''; week = ''; compareIds = null; config = null; configError = ''; configTask = null; }
  };
};
