import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import test from 'node:test';
const observer=readFileSync(new URL('./observer.js',import.meta.url),'utf8');
function harness(href='https://luna.amazon.co.uk/play?asin=TEST'){
 let tick;let clock=1000;let videos=[];let attention=false;const messages=[];const events={};
 class Stream {getVideoTracks(){return [{readyState:'live'}];}}
 const launchIds=[];
 runInNewContext(observer,{URL,location:{href},MediaStream:Stream,document:{querySelectorAll:()=>videos,querySelector:()=>attention?{}:null},performance:{now:()=>clock},chrome:{runtime:{sendMessage:async m=>{messages.push(m.state);launchIds.push(m.launchAsin);}}},setInterval:fn=>{tick=fn;},addEventListener:(n,fn)=>{events[n]=fn;}});
 return {messages,launchIds,Stream,setVideos:v=>{videos=v;},setAttention:v=>{attention=v;},advance:async n=>{clock+=n;await Promise.resolve();await tick();},events};
}

test('binds the actual Luna game path before SPA navigation',async()=>{
 const h=harness('https://luna.amazon.co.uk/game/star-wars-galactic-racer/B0HLN4GG5K?autoplay=1');
 await h.advance(750);assert.equal(h.launchIds.at(-1),'B0HLN4GG5K');
 const home=harness('https://luna.amazon.co.uk/player');await home.advance(750);assert.equal(home.launchIds.at(-1),null);
});
test('only advancing live game media counts; homepage/trailer do not',async()=>{
 const h=harness();await h.advance(750);assert.equal(h.messages.at(-1).phase,'waiting');
 let frames=0;const video={srcObject:new h.Stream(),getVideoPlaybackQuality:()=>({totalVideoFrames:frames}),paused:false,readyState:4};
 h.setVideos([video]);await h.advance(750);assert.equal(h.messages.at(-1).phase,'buffering');
 frames+=45;await h.advance(750);assert.equal(h.messages.at(-1).phase,'playing');
 await h.advance(750);assert.equal(h.messages.at(-1).phase,'buffering');
 h.setAttention(true);frames+=45;await h.advance(750);assert.equal(h.messages.at(-1).phase,'attention');h.setAttention(false);
 h.setVideos([]);await h.advance(750);await h.advance(1500);assert.equal(h.messages.at(-1).phase,'ended');
 assert.deepEqual(Object.keys(h.messages.at(-1)).sort(),['phase','sequence']);
});
test('normal trailer video never starts a Luna game session',async()=>{
 const h=harness();h.setVideos([{srcObject:null,currentTime:3,paused:false,readyState:4}]);await h.advance(750);await h.advance(750);assert.equal(h.messages.at(-1).phase,'waiting');
});
test('navigation pauses rather than inventing a game exit',async()=>{
 const h=harness();await h.advance(750);h.setVideos([{srcObject:new h.Stream(),currentTime:3,paused:false,readyState:4}]);await h.advance(750);h.events.pagehide();await Promise.resolve();assert.equal(h.messages.at(-1).phase,'buffering');
});


