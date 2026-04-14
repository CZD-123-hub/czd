
const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 200, height: 200 } });
  await page.setContent(`
    <div style="width:200px;height:200px;background:linear-gradient(135deg,#667eea,#764ba2);display:flex;align-items:center;justify-content:center;font-size:80px;color:white;font-family:Arial;">
      Z
    </div>
  `);
  await page.screenshot({ path: 'test-avatar.jpg', type: 'jpeg', quality: 90 });
  await browser.close();
  console.log('Avatar created!');
})();
