#!/usr/bin/env node
/** Pure Node regression of Uni-app STOMP frame parsing; no network needed. */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../mobile/utils/stomp.js', import.meta.url), 'utf8');
const imported = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
const client = new imported.StompClient();
const received = [];
client._subscriptions['s1'] = {
  destination: '/user/queue/chat',
  callback: (body, headers) => received.push({body, headers})
};

client._onFrame('MESSAGE\nsubscription:s1\ncontent-type:application/json\n\n{"text":"chunk');
assert.equal(received.length, 0, 'Do not parse a frame missing its NUL terminator');
client._onFrame('ed"}\x00');
assert.equal(received.length, 1);
assert.deepEqual(JSON.parse(received[0].body), {text: 'chunked'});

client._onFrame('\nMESSAGE\nsubscription:s1\n\nfirst\x00MESSAGE\nsubscription:s1\n\nsecond\x00');
assert.equal(received.length, 3, 'One callback per complete frame, even when batched');
assert.deepEqual(received.slice(1).map(x => x.body), ['first', 'second']);

client._onFrame('MESSAGE\nsubscription:s1\n\nawaiting');
assert.equal(received.length, 3);
client._onFrame('\nremaining\x00');
assert.equal(received[3].body, 'awaiting\nremaining');

client._onFrame('X'.repeat(130 * 1024));
assert.equal(client._receiveBuffer, '', 'Oversized untrusted frames must not accumulate forever');
client._onFrame('MESSAGE\nsubscription:s1\n\nrecovered\x00');
assert.equal(received.at(-1).body, 'recovered');

client._onFrame('MESSAGE\nsubscription:s1\n\nunfinished');
client.disconnect();
assert.equal(client._receiveBuffer, '', 'Disconnect resets partial messages');
console.log('PASS: fragmented STOMP frames, combined frames, heartbeats, recovery and cleanup');
