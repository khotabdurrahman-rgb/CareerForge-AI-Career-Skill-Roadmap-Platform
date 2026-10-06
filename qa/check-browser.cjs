const { chromium } = require('../.tools/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = process.env.CAREERFORGE_URL || 'http://localhost:8090';
const screenshots = 'docs/screenshots';
const errors = [];
let checks = 0;
const check = (value, label) => { assert.ok(value, label); checks++; };

async function main() {
  fs.mkdirSync(screenshots, {recursive:true});
  const browser = await chromium.launch({headless:true});
  const context = await browser.newContext({viewport:{width:1440,height:1050}});
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => {
    if(response.status()>=500) errors.push(response.status()+' '+response.url());
  });
  try {
    await page.goto(base, {waitUntil:'networkidle'});
    await page.getByRole('button',{name:'Student demo',exact:true}).click();
    await page.locator('.metrics').waitFor();
    check(await page.getByText('62.5%',{exact:true}).count()>=1,'Dashboard renders actual readiness');
    await page.screenshot({path:screenshots+'/dashboard-desktop.png',fullPage:true});
    for(const route of ['skills','careers','gap','roadmap','projects','resources','profile']) {
      await page.locator('[data-route="'+route+'"]').click();
      await page.locator('#main h1').waitFor();
      check(await page.locator('#main h1').textContent(),'Route '+route+' renders');
      check(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),'No desktop page overflow '+route);
    }
    await page.locator('[data-route="skills"]').click();
    await page.getByRole('button',{name:'Add a skill',exact:true}).first().click();
    await page.getByLabel('Skill',{exact:true}).selectOption({label:'Python'});
    await page.getByLabel('Current proficiency').selectOption('Intermediate');
    await page.locator('dialog').getByRole('button',{name:'Add skill',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    const pythonRow=page.locator('tr').filter({has:page.locator('strong',{hasText:'Python'})});
    await pythonRow.waitFor();
    check(await pythonRow.getByText('Intermediate',{exact:true}).count()===1,'Skill proficiency persisted');
    await page.getByRole('button',{name:'Remove Python',exact:true}).click();
    await page.locator('dialog').getByRole('button',{name:'Remove',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    await pythonRow.waitFor({state:'hidden'});

    await page.locator('[data-route="roadmap"]').click();
    const step=page.locator('[data-step]').first();
    const previous=await step.inputValue();
    await step.selectOption('COMPLETED');
    await page.waitForFunction(()=>document.querySelector('[data-step]')?.value==='COMPLETED' && !document.querySelector('[data-step]')?.disabled);
    await page.locator('[data-route="dashboard"]').click();
    await page.getByText('75%',{exact:true}).first().waitFor();
    check(await page.getByText('75%',{exact:true}).count()>=1,'Roadmap changes dashboard readiness');
    await page.locator('[data-route="roadmap"]').click();
    await page.locator('[data-step]').first().selectOption(previous);
    await page.waitForFunction(value=>document.querySelector('[data-step]')?.value===value && !document.querySelector('[data-step]')?.disabled,previous);

    await page.locator('[data-route="projects"]').click();
    await page.getByRole('button',{name:'Add project',exact:true}).first().click();
    await page.getByLabel('Project name',{exact:true}).fill('Browser QA Project');
    await page.getByLabel('Description',{exact:true}).fill('A project created through the user interface.');
    await page.getByLabel('Technologies',{exact:true}).fill('Java, Spring Boot');
    await page.getByLabel('Status',{exact:true}).selectOption('COMPLETED');
    await page.locator('dialog').getByRole('button',{name:'Add project',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    await page.getByRole('heading',{name:'Browser QA Project',exact:true}).waitFor();
    check(true,'Project form saves and renders');
    await page.getByRole('button',{name:'Delete Browser QA Project',exact:true}).click();
    await page.locator('dialog').getByRole('button',{name:'Remove',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    await page.getByRole('heading',{name:'Browser QA Project',exact:true}).waitFor({state:'hidden'});

    await page.locator('[data-route="careers"]').click();
    const frontend=page.locator('article').filter({has:page.getByRole('heading',{name:'Frontend Developer',exact:true})});
    await frontend.getByRole('button',{name:'Choose this path',exact:true}).click();
    await page.locator('dialog').getByRole('button',{name:'Choose career',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    await frontend.getByText('Your career goal',{exact:true}).waitFor();
    check(true,'Career selection persists');
    const java=page.locator('article').filter({has:page.getByRole('heading',{name:'Full Stack Java Developer',exact:true})});
    await java.getByRole('button',{name:'Choose this path',exact:true}).click();
    await page.locator('dialog').getByRole('button',{name:'Choose career',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    await java.getByText('Your career goal',{exact:true}).waitFor();

    await page.setViewportSize({width:390,height:844});
    await page.goto(base+'/#dashboard',{waitUntil:'networkidle'});
    await page.reload({waitUntil:'networkidle'});
    await page.locator('.metrics').waitFor();
    check(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),'Mobile dashboard fits viewport');
    await page.screenshot({path:screenshots+'/dashboard-mobile.png',fullPage:true});
    for(const route of ['skills','careers','gap','roadmap','projects','resources','profile']) {
      await page.getByRole('button',{name:'Open navigation',exact:true}).click();
      await page.locator('[data-route="'+route+'"]').click();
      check(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),'Mobile route fits '+route);
    }
    await page.getByRole('button',{name:'Open navigation',exact:true}).click();
    await page.getByRole('button',{name:'Sign out',exact:true}).click();
    await page.getByRole('button',{name:'Admin demo',exact:true}).waitFor();
    await page.setViewportSize({width:1440,height:1050});
    await page.getByRole('button',{name:'Admin demo',exact:true}).click();
    await page.locator('.metrics').waitFor();
    await page.locator('[data-route="admin"]').click();
    await page.getByText('student@careerforge.dev',{exact:true}).waitFor();
    check(true,'Admin users view loads');
    await page.screenshot({path:screenshots+'/admin-desktop.png',fullPage:true});
    await page.getByRole('tab',{name:'Careers',exact:true}).click();
    await page.getByRole('button',{name:'Add career',exact:true}).click();
    await page.getByLabel('Career name',{exact:true}).fill('QA Career Path');
    await page.getByLabel('Description',{exact:true}).fill('Temporary catalog test.');
    await page.locator('input[name="skillIds"]').first().check();
    await page.locator('dialog').getByRole('button',{name:'Save career',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    await page.getByRole('button',{name:'Delete QA Career Path',exact:true}).click();
    await page.locator('dialog').getByRole('button',{name:'Remove',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    check(true,'Admin career create and delete work');
    await page.getByRole('tab',{name:'Resources',exact:true}).click();
    await page.getByRole('button',{name:'Add resource',exact:true}).click();
    await page.getByLabel('Related skill',{exact:true}).selectOption({label:'Java'});
    await page.getByLabel('Resource title',{exact:true}).fill('QA Resource');
    await page.getByLabel('Resource URL',{exact:true}).fill('https://dev.java/learn/');
    await page.locator('dialog').getByRole('button',{name:'Save resource',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    await page.getByRole('button',{name:'Delete QA Resource',exact:true}).click();
    await page.locator('dialog').getByRole('button',{name:'Remove',exact:true}).click();
    await page.locator('dialog').waitFor({state:'hidden'});
    check(true,'Admin resource create and delete work');
    check(errors.length===0,'No browser exceptions or server errors: '+errors.join('; '));
    console.log(JSON.stringify({base,checks,result:'PASS',screenshots},null,2));
  } catch(error) {
    await page.screenshot({path:'.tools/browser-failure.png',fullPage:true});
    console.error('Browser URL:',page.url());
    throw error;
  } finally {await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
