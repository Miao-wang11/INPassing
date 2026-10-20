#!/usr/bin/env node
/**
 * Real native-WebSocket/STOMP smoke test using Node 22 built-ins.
 * Uses existing ephemeral CI fixtures. Tests server-side user routing
 * and persistence, not just Java unit mocks.
 */
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

const API = 'http://127.0.0.1:8080/api';
const WS = 'ws://127.0.0.1:8080/api/ws/chat';
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

function sql(statement) {
  return execFileSync('docker', [
    'exec', '-e', 'MYSQL_PWD=' + process.env.MYSQL_ROOT_PASSWORD,
    'rr-mysql', 'mysql', '-N', '-B', '-uroot', 'runningerrands', '-e', statement
  ], { encoding: 'utf8' }).trim();
}

async function identity(username) {
  const response = await fetch(API + '/user/login', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ loginType: 1, username, password: '123456' })
  });
  const payload = await response.json();
  assert.equal(payload.code, 1, username + ' login');
  return {
    id: Number(sql("SELECT id FROM user WHERE username='" + username + "'")),
    token: payload.data.token
  };
}

function openSocket(url) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    const timer = setTimeout(() => reject(new Error('WebSocket handshake timeout')), 8000);
    socket.addEventListener('open', () => { clearTimeout(timer); resolve(socket); }, { once: true });
    socket.addEventListener('error', () => {
      clearTimeout(timer);
      reject(new Error('WebSocket handshake rejected'));
    }, { once: true });
  });
}

function frame(command, headers = {}, body = '') {
  return command + '\n' + Object.entries(headers)
    .map(([k,v]) => k + ':' + v).join('\n') + '\n\n' + body + '\0';
}

class Client {
  constructor(ws) {
    this.ws = ws;
    this.inbox = [];
    this.waiters = [];
    this.pending = '';
    ws.addEventListener('message', (event) => {
      // Native Node WebSocket message data is a string for STOMP text frames.
      this.pending += typeof event.data === 'string' ? event.data : '';
      while (this.pending.includes('\0')) {
        const split = this.pending.indexOf('\0');
        const raw = this.pending.slice(0, split).trimStart();
        this.pending = this.pending.slice(split + 1);
        if (!raw) continue;
        const idx = raw.indexOf('\n\n');
        const headers = raw.slice(0, idx).split('\n');
        const packet = { command: headers[0].trim(), body: raw.slice(idx + 2) };
        this.inbox.push(packet);
        for (const waiter of [...this.waiters]) {
          if (waiter.test(packet)) {
            clearTimeout(waiter.timer);
            this.waiters.splice(this.waiters.indexOf(waiter), 1);
            waiter.resolve(packet);
          }
        }
      }
    });
  }
  send(command, headers, body = '') { this.ws.send(frame(command, headers, body)); }
  wait(test, ms = 8000) {
    const existing = this.inbox.find(test);
    if (existing) return Promise.resolve(existing);
    return new Promise((resolve, reject) => {
      const waiter = { test, resolve, timer: null };
      waiter.timer = setTimeout(() => {
        this.waiters = this.waiters.filter(w => w !== waiter);
        reject(new Error('STOMP frame not received before timeout'));
      }, ms);
      this.waiters.push(waiter);
    });
  }
  close() { this.ws.close(); }
}

async function connect(token) {
  const ws = await openSocket(WS + '?token=' + encodeURIComponent(token));
  const client = new Client(ws);
  client.send('CONNECT', { 'accept-version': '1.2', host: 'localhost', 'heart-beat': '0,0' });
  await client.wait(p => p.command === 'CONNECTED');
  client.send('SUBSCRIBE', { id: 'sub-chat', destination: '/user/queue/chat', ack: 'auto' });
  await pause(700);
  return client;
}

async function main() {
  assert.equal(typeof WebSocket, 'function', 'Node 22+ WebSocket required');
  const alice = await identity('ci_offer_provider');
  const bob = await identity('ci_offer_buyer');
  const outsider = await identity('ci_offer_other_campus');
  const a = await connect(alice.token);
  const b = await connect(bob.token);
  const c = await connect(outsider.token);
  try {
    await pause(250);
    const content = 'CI_STOMP_PRIVATE_CHAT_20261020';
    a.send('SEND', { destination: '/app/chat.send',
      'content-type': 'application/json' }, JSON.stringify({
        receiverId: bob.id, content, messageType: 1
      }));
    let received;
    try {
      received = await b.wait(p => p.command === 'MESSAGE' && p.body.includes(content));
    } catch (error) {
      console.error('DIAG sender frames:', JSON.stringify(a.inbox).slice(0,3000));
      console.error('DIAG recipient frames:', JSON.stringify(b.inbox).slice(0,3000));
      console.error('DIAG database chat rows:', sql("SELECT COUNT(*) FROM chat_message WHERE content='CI_STOMP_PRIVATE_CHAT_20261020'"));
      throw error;
    }
    const message = JSON.parse(received.body);
    assert.equal(Number(message.senderId), alice.id);
    assert.equal(Number(message.receiverId), bob.id);
    await a.wait(p => p.command === 'MESSAGE' && p.body.includes(content)).catch(error => {
      console.error('DIAG sender echo timeout:', JSON.stringify(a.inbox).slice(0,3000));
      throw error;
    });
    assert.equal(sql("SELECT COUNT(*) FROM chat_message WHERE content='" + content + "'"
      + ' AND sender_id=' + alice.id + ' AND receiver_id=' + bob.id), '1');
    console.log('PASS: authenticated STOMP direct message, isolated user queue and MySQL persistence');

    const forbidden = 'CI_STOMP_CROSS_CAMPUS_DENIED_20261020';
    c.send('SEND', { destination: '/app/chat.send', 'content-type': 'application/json' },
      JSON.stringify({ receiverId: bob.id, content: forbidden, messageType: 1 }));
    await pause(600);
    assert.equal(sql("SELECT COUNT(*) FROM chat_message WHERE content='" + forbidden + "'"), '0');
    console.log('PASS: cross-campus STOMP SEND never persisted');
  } finally {
    a.close(); b.close(); c.close();
  }
}
main().catch(err => { console.error(err); process.exitCode = 1; });
