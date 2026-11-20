import { writeFile } from 'node:fs/promises';

const [output] = process.argv.slice(2);
if (!output) throw new Error('Specify the screenshot output path');
const deadline = Date.now() + 60000;
let lastError;
while (Date.now() < deadline) {
  let socket;
  try {
    const pages = await (await fetch('http://127.0.0.1:9222/json/list', { signal: AbortSignal.timeout(3000) })).json();
    const page = pages.find(item => /^(https?:\/\/tauri\.localhost|tauri:)/.test(item.url));
    if (!page?.webSocketDebuggerUrl) throw new Error('Desktop WebView is not ready');
    socket = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Desktop inspection connection timed out')), 5000);
      socket.onopen = () => { clearTimeout(timer); resolve(); };
      socket.onerror = error => { clearTimeout(timer); reject(error); };
    });
    let id = 0;
    const pending = new Map();
    socket.onmessage = event => {
      const message = JSON.parse(event.data);
      const request = pending.get(message.id);
      if (!request) return;
      pending.delete(message.id);
      clearTimeout(request.timer);
      if (message.error) request.reject(new Error(message.error.message));
      else request.resolve(message.result);
    };
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const requestId = ++id;
      const timer = setTimeout(() => { pending.delete(requestId); reject(new Error(`Desktop inspection timed out: ${method}`)); }, 10000);
      pending.set(requestId, { resolve, reject, timer });
      socket.send(JSON.stringify({ id: requestId, method, params }));
    });
    const inspection = await send('Runtime.evaluate', {
      expression: `({passwordField: !!document.querySelector('input[type="password"]'), loginText: document.body.innerText.toLocaleLowerCase('vi').includes('đăng nhập')})`,
      returnByValue: true,
    });
    if (!inspection.result?.value?.passwordField || !inspection.result.value.loginText) throw new Error('Packaged login form has not rendered');
    const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    await writeFile(output, Buffer.from(screenshot.data, 'base64'));
    console.log('PASS: packaged WebView rendered the Vietnamese login form; screenshot saved.');
    socket.close();
    process.exit(0);
  } catch (error) {
    lastError = error;
    socket?.close();
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
}
throw lastError ?? new Error('Packaged desktop WebView could not be inspected');
