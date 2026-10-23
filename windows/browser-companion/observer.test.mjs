import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import test from 'node:test';
const observer=readFileSync(new URL('./observer.js',import.meta.url),'utf8');
function harness(href='https://luna.amazon.co.uk/play?asin=TEST'){
 let tick;let clock=1000;let videos=[];let attention=false;let promptVisible=true;const messages=[];const events={};
 class Stream {getVideoTracks(){return [{readyState:'live'}];}}
 const launchIds=[];
 const location={href};
 runInNewContext(observer,{URL,location,MediaStream:Stream,document:{querySelectorAll:()=>videos,querySelector:()=>attention?{checkVisibility:()=>promptVisible}:null},performance:{now:()=>clock},chrome:{runtime:{sendMessage:async m=>{messages.push(m.state);launchIds.push(m.launchAsin);}}},setInterval:fn=>{tick=fn;},addEventListener:(n,fn)=>{events[n]=fn;}});
 return {messages,launchIds,Stream,navigate:url=>{location.href=url;},setVideos:v=>{videos=v;},setAttention:v=>{attention=v;},setPromptVisible:v=>{promptVisible=v;},advance:async n=>{clock+=n;await Promise.resolve();await tick();},events};
}

test('SPA game changes replace the old game identity and playback history',async()=>{
 const h=harness('https://luna.amazon.co.uk/game/old/B0HLN4GG5K');await h.advance(750);
 h.setVideos([{srcObject:new h.Stream(),currentTime:1,paused:false,readyState:4}]);await h.advance(750);
 h.navigate('https://luna.amazon.co.uk/game/new/B012345678');h.setVideos([]);await h.advance(750);await h.advance(1500);
 assert.equal(h.launchIds.at(-1),'B012345678');assert.equal(h.messages.at(-1).phase,'waiting');
 h.navigate('https://luna.amazon.co.uk/player');await h.advance(750);assert.equal(h.launchIds.at(-1),'B012345678');
});

test('a live track disappearing before any advancing playback is not a completed session',async()=>{
 const h=harness();h.setVideos([{srcObject:new h.Stream(),currentTime:0,paused:true,readyState:1}]);await h.advance(750);
 h.setVideos([]);await h.advance(750);await h.advance(1500);assert.equal(h.messages.at(-1).phase,'waiting');
});
test('hidden prompts and a zero frame counter do not suppress advancing cross-realm media',async()=>{
 const h=harness();await h.advance(750);const video={srcObject:{getVideoTracks:()=>[{readyState:'live'}]},getVideoPlaybackQuality:()=>({totalVideoFrames:0}),currentTime:0,paused:false,readyState:4};
 h.setVideos([video]);h.setAttention(true);h.setPromptVisible(false);await h.advance(750);
 video.currentTime=0.75;await h.advance(750);assert.equal(h.messages.at(-1).phase,'playing');
 h.setPromptVisible(true);video.currentTime=1.5;await h.advance(750);assert.equal(h.messages.at(-1).phase,'attention');
});

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


