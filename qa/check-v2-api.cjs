const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const base = (process.env.CAREERFORGE_URL || 'http://localhost:8090').replace(/\/$/, '');
let checks = 0;
const check = (value, label) => { assert.ok(value, label); checks++; };

async function request(path, { method = 'GET', account, body, expected = 200 } = {}) {
  const headers = { Accept: path.endsWith('/pdf') ? 'application/pdf, application/json' : 'application/json' };
  if (account?.cookie) headers.Cookie = account.cookie;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30000) });
  const bytes = Buffer.from(await response.arrayBuffer());
  const data = response.headers.get('content-type')?.includes('application/json') ? JSON.parse(bytes.toString()) : null;
  assert.equal(response.status, expected, `${method} ${path}: ${bytes.toString().slice(0, 400)}`);
  checks++;
  const cookie = response.headers.get('set-cookie')?.split(';')[0];
  if (account && cookie) account.cookie = cookie;
  return { data, bytes, response, cookie };
}
async function register() {
  const result = await request('/api/auth/register', { method: 'POST', body: { name: 'V2 API QA', email: `qa-v2-${randomUUID()}@example.org`, password: 'QaV2Password123!' } });
  return { ...result.data, cookie: result.cookie };
}
function noSecrets(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, item] of Object.entries(value)) {
    check(!['password', 'passwordHash', 'sessionVersion', 'token', 'tokenHash', 'questionSet'].includes(key), `No secret ${key}`);
    noSecrets(item);
  }
}
async function main() {
  for (const path of ['/api/assessments', '/api/planner', '/api/resume', '/api/resume/pdf', '/api/recommendations', '/api/progress', '/api/mentor', '/api/careers/compare?left=1&right=2'])
    await request(path, { expected: 401 });
  const owner = await register(), other = await register();
  const get = async path => (await request(path, { account: owner })).data;
  const mutate = async (path, method, body) => (await request(path, { account: owner, method, body })).data;
  const before = await get('/api/dashboard');
  const assessment = await get('/api/assessments');
  check(assessment.skills.length > 0, 'Assessment bank is available');
  const quiz = await mutate(`/api/assessments/${assessment.skills[0].skillId}/start`, 'POST');
  noSecrets(quiz);
  check(quiz.questions.length >= 3, 'Quiz has questions');
  for (const q of quiz.questions) {
    check(q.prompt && q.options.length >= 2, 'Question is usable');
    check(!('correctOptionIndex' in q) && !('explanation' in q) && !('answer' in q), 'No premature quiz answers');
  }
  const answers = quiz.questions.map(q => ({ questionId: q.id, optionIndex: 0 }));
  const submitPath = `/api/assessments/sessions/${quiz.id}/submit`;
  await request(submitPath, { method: 'POST', account: other, body: { answers }, expected: 404 });
  await request(submitPath, { method: 'POST', account: owner, body: { answers: [] }, expected: 400 });
  const result = await mutate(submitPath, 'POST', { answers });
  check(result.feedback.length === quiz.questions.length, 'Feedback covers every question');
  check(result.attempt.correct === result.feedback.filter(f => f.correct).length, 'Score uses actual feedback');
  await request(submitPath, { method: 'POST', account: owner, body: { answers }, expected: 409 });
  check((await get('/api/assessments')).attempts.some(a => a.id === result.attempt.id), 'Attempt survives subsequent request');
  check((await get('/api/dashboard')).gap.readiness === before.gap.readiness, 'Assessment does not change declared readiness');

  const week = '2026-10-05';
  const goal = await mutate('/api/planner/goals', 'POST', { title: 'V2 QA goal', weekStart: week, targetMinutes: 120 });
  const step = (await get('/api/roadmap'))[0];
  const taskBody = { title: 'V2 QA task', skillId: step.skill?.id ?? null, roadmapStepId: step.id, dueDate: week, estimatedMinutes: 30, status: 'NOT_STARTED', weekStart: week };
  const task = await mutate('/api/planner/tasks', 'POST', taskBody);
  try {
    for (const [path, method, body] of [[`/api/planner/goals/${goal.id}`, 'DELETE'], [`/api/planner/tasks/${task.id}`, 'PUT', taskBody]])
      await request(path, { account: other, method, body, expected: 404 });
    await mutate(`/api/planner/tasks/${task.id}`, 'PUT', { ...taskBody, status: 'COMPLETED' });
    check((await get('/api/roadmap')).find(s => s.id === step.id).status === 'COMPLETED', 'Planner completion updates linked roadmap');
    const planner = await get(`/api/planner?week=${week}`);
    check(planner.tasks.some(t => t.id === task.id && t.skillId === taskBody.skillId), 'Task links survive separate request');
    check(planner.completedMinutes >= 30, 'Planner counts completed minutes');
  } finally {
    await mutate(`/api/planner/tasks/${task.id}`, 'DELETE');
    await mutate(`/api/planner/goals/${goal.id}`, 'DELETE');
  }
  const profile = { headline: 'V2 QA Engineer', summary: 'Verified API workflow', phone: '', location: 'Mumbai', website: 'https://example.org', education: 'QA education', experience: 'QA experience', achievements: 'QA achievements', includeSkills: true, includeProjects: true, includeEducation: true, includeExperience: true, includeAchievements: true };
  await mutate('/api/resume', 'PUT', profile);
  const resume = await get('/api/resume');
  assert.deepEqual(resume.profile, profile); checks++;
  noSecrets(resume);
  check((await request('/api/resume', { account: other })).data.profile.headline !== profile.headline, 'Resume is isolated');
  const pdf = await request('/api/resume/pdf', { account: owner });
  check(pdf.response.headers.get('content-type').includes('application/pdf') && pdf.bytes.subarray(0, 5).toString() === '%PDF-', 'Export is a PDF');
  const careers = await get('/api/careers');
  const comparison = await get(`/api/careers/compare?left=${careers[0].id}&right=${careers[1].id}`);
  check(comparison.left.career.id === careers[0].id && comparison.right.career.id === careers[1].id, 'Comparison returns requested careers');
  const ideas = await get('/api/recommendations');
  check(ideas.length > 0, 'Project recommendations are available');
  const path = `/api/recommendations/${ideas[0].id}`;
  check((await mutate(path + '/save', 'POST')).saved, 'Recommendation saved');
  const first = await mutate(path + '/portfolio', 'POST'), second = await mutate(path + '/portfolio', 'POST');
  check(first.id === second.id, 'Portfolio addition is idempotent');
  await mutate(path + '/save', 'DELETE');
  const progress = await get('/api/progress');
  for (const path of ['/api/dashboard', '/api/assessments', '/api/planner', '/api/resume', '/api/recommendations']) await get(path);
  assert.deepEqual((await get('/api/progress')).events, progress.events); checks++;
  check(progress.currentTimezone && progress.streakDefinition && Array.isArray(progress.trend), 'Progress describes timezone and streak');
  const mentor = await get('/api/mentor');
  if (!mentor.available) {
    await request('/api/mentor', { account: owner, method: 'POST', body: { message: 'Advice please' }, expected: 503 });
    assert.deepEqual((await get('/api/mentor')).messages, mentor.messages); checks++;
  }
  await mutate('/api/mentor/history', 'DELETE');
  noSecrets((await request('/api/auth/config')).data);
  await request('/api/auth/reset-password', { method: 'POST', body: { token: 'invalid-qa-token', password: 'ChangedQa123!' }, expected: 400 });
  await request('/api/auth/verify-email', { method: 'POST', body: { token: 'invalid-qa-token' }, expected: 400 });
  await mutate('/api/auth/logout', 'POST');
  await request('/api/me', { account: owner, expected: 401 });
  console.log(JSON.stringify({ base, checks, result: 'PASS', paidAiRequests: 0 }, null, 2));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
