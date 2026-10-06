const assert = require('node:assert/strict');
const base = process.env.CAREERFORGE_URL || 'http://localhost:8090';
let checks = 0;
function check(condition, message) { assert.ok(condition, message); checks++; }
async function request(path, {method='GET', body, cookie, headers={}}={}) {
  const response = await fetch(base+path, {
    method, headers:{...(body ? {'Content-Type':'application/json'} : {}), ...(cookie ? {Cookie:cookie}:{}), ...headers},
    body:body ? JSON.stringify(body) : undefined
  });
  const text=await response.text();
  return {status:response.status, data:text ? JSON.parse(text) : null, cookie:response.headers.get('set-cookie')?.split(';')[0]};
}
async function main() {
  check((await request('/api/dashboard')).status===401,'Workspace requires authentication');
  check((await request('/api/auth/login',{method:'POST',body:{email:'student@careerforge.dev',password:'wrong'}})).status===401,'Invalid credentials rejected');
  const student=await request('/api/auth/login',{method:'POST',body:{email:'student@careerforge.dev',password:'Career123!'}});
  check(student.status===200 && student.cookie,'Student session established');
  check(!('passwordHash' in student.data),'Password hash stays private');
  const cookie=student.cookie;
  let dashboard=(await request('/api/dashboard',{cookie})).data;
  check(dashboard.gap.readiness===62.5,'Seed readiness uses 5 / 8 required skills');
  check(dashboard.roadmap.length===4,'Missing skills plus capstone generate roadmap');
  check((await request('/api/admin/users',{cookie})).status===403,'Students cannot access admin users');
  const step=dashboard.roadmap.find(s=>s.skill);
  await request('/api/roadmap/'+step.id,{method:'PUT',cookie,body:{status:'COMPLETED'}});
  dashboard=(await request('/api/dashboard',{cookie})).data;
  check(dashboard.gap.readiness===75,'Completing a learning step improves readiness');
  await request('/api/roadmap/'+step.id,{method:'PUT',cookie,body:{status:step.status}});
  check((await request('/api/dashboard',{cookie})).data.gap.readiness===62.5,'Reverting completion restores readiness');
  const created=await request('/api/projects',{method:'POST',cookie,body:{name:'QA temporary project',description:'Live persistence verification',technology:'Java',githubUrl:'https://github.com/topics/java',status:'COMPLETED'}});
  check(created.status===200,'Project creation succeeds');
  check((await request('/api/projects',{cookie})).data.some(p=>p.id===created.data.id),'New project persists');
  check((await request('/api/projects/'+created.data.id,{method:'DELETE',cookie})).status===200,'Project deletion succeeds');
  check((await request('/api/projects',{method:'POST',cookie,body:{name:'Unsafe URL',githubUrl:'javascript:alert(1)',status:'COMPLETED'}})).status===400,'Non-http project URLs rejected');
  check((await request('/api/profile',{method:'PUT',cookie,headers:{Origin:'https://unrelated.example'},body:{name:'QA',careerId:1}})).status===403,'Cross-origin writes rejected');
  const admin=await request('/api/auth/login',{method:'POST',body:{email:'admin@careerforge.dev',password:'Career123!'}});
  check((await request('/api/admin/users',{cookie:admin.cookie})).status===200,'Administrator can list users');
  check((await request('/api/users/'+student.data.id+'/projects',{cookie:admin.cookie})).status===403,'Record ownership enforced even across roles');
  await request('/api/auth/logout',{method:'POST',cookie});
  check((await request('/api/me',{cookie})).status===401,'Logout invalidates session');
  console.log(JSON.stringify({base,checks,result:'PASS'},null,2));
}
main().catch(error=>{console.error(error);process.exitCode=1;});
