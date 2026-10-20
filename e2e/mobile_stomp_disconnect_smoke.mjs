#!/usr/bin/env node
/** Transport lifecycle tests for Uni-app STOMP without real sockets. */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../mobile/utils/stomp.js', import.meta.url), 'utf8');
const { StompClient } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));

let current;
globalThis.uni = {
  connectSocket() {
    const listeners = {};
    current = {
      onOpen: cb => { listeners.open = cb },
      onMessage: cb => { listeners.message = cb },
      onError: cb => { listeners.error = cb },
      onClose: cb => { listeners.close = cb },
      send: () => {},
      close: () => { listeners.close?.() },
      emitOpen: () => listeners.open(),
      emitMessage: data => listeners.message({data}),
      emitClose: () => listeners.close()
    };
    return current;
  }
};

async function connected() {
  const client = new StompClient();
  const pending = client.connect('ws://localhost/api/ws/chat', undefined, 5000);
  const socket = current;
  socket.emitOpen();
  socket.emitMessage('CONNECTED\nversion:1.2\n\n\x00');
  await pending;
  assert.equal(client.connected, true);
  return {client, socket};
}

{
  const {client, socket} = await connected();
  let disconnects = 0;
  client.onDisconnect = () => { disconnects++ };
  socket.emitClose();
  assert.equal(client.connected, false);
  assert.equal(disconnects, 1, 'Unexpected network drop is surfaced to the store');
}
{
  const {client} = await connected();
  let disconnects = 0;
  client.onDisconnect = () => { disconnects++ };
  client.disconnect();
  assert.equal(disconnects, 0, 'Explicit logout must not schedule a reconnect');
  assert.equal(client.connected, false);
}
{
  const client = new StompClient();
  const pending = client.connect('ws://localhost/api/ws/chat', undefined, 5000);
  const socket = current;
  socket.emitOpen();
  socket.emitMessage('ERROR\nmessage:token expired\n\nauth failed\x00');
  await assert.rejects(pending, error => error.isAuthError === true);
  assert.equal(client._connectReject, null, 'Rejected handshake must clear timeout callbacks');
  client.disconnect();
}

console.log('PASS: network loss callback, manual logout isolation and STOMP auth error rejection');
