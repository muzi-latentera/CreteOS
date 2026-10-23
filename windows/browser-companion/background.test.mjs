import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import test from 'node:test';
const source=readFileSync(new URL('./background.js',import.meta.url),'utf8');
function harness(session={nonce:'test-only',asin:'B0HLN4GG5K',tab:null}) {
  let listener;const calls=[];
  runInNewContext(source,{URL,AbortSignal,console:{info(){}},chrome:{runtime:{onMessage:{addListener:fn=>{listener=fn;}}}},fetch:async(url,options)=>{
    calls.push({url,options});return {ok:true,status:200,json:async()=>session};
  }});
  return {calls,send:(message,sender)=>new Promise(resolve=>{if(listener(message,sender,resolve)!==true)resolve(undefined);})};
}
const message={type:'luna-state',launchAsin:'B0HLN4GG5K',state:{phase:'playing',sequence:1}};
const sender={url:'https://luna.amazon.co.uk/player',tab:{id:42,url:'https://luna.amazon.co.uk/player'},frameId:0,documentId:'document-test'};
test('uses POST for the origin-bearing handshake and sends only session metadata',async()=>{
  const h=harness();await h.send(message,sender);
  assert.equal(h.calls[0].options.method,'POST');assert.equal(h.calls[0].options.mode,'cors');
  assert.equal(h.calls[1].url,'http://127.0.0.1:59265/state');
  const body=JSON.parse(h.calls[1].options.body);
  assert.deepEqual(Object.keys(body).sort(),['document','frame','nonce','phase','sequence','tab']);
  assert.equal(body.phase,'playing');assert.equal(body.tab,42);
});
test('rejects unrelated origins and malformed states without requesting a session',async()=>{
  const h=harness();await h.send(message,{...sender,url:'https://example.test/player'});
  await h.send({...message,state:{phase:'cookie',sequence:1}},sender);
  await h.send({...message,state:{phase:'playing',sequence:0}},sender);
  assert.equal(h.calls.length,0);
});
test('does not bind a different game or post from a different already-bound tab',async()=>{
  const h=harness();await h.send({...message,launchAsin:'OTHER'},sender);assert.equal(h.calls.length,1);
  const bound=harness({nonce:'test-only',asin:'B0HLN4GG5K',tab:99});await bound.send(message,sender);assert.equal(bound.calls.length,1);
});
test('continues reporting the bound tab after it returns to Luna home',async()=>{
  const h=harness({nonce:'test-only',asin:'B0HLN4GG5K',tab:42});
  await h.send({type:'luna-state',state:{phase:'ended',sequence:3}},{...sender,url:'https://luna.amazon.co.uk/'});
  assert.equal(JSON.parse(h.calls[1].options.body).phase,'ended');
});
