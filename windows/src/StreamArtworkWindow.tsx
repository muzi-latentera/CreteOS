import { useEffect, useState } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
type Artwork = { art: string; message: string };
export function StreamArtworkWindow() {
  const [state,setState]=useState<Artwork>({art:'',message:'Preparing stream…'});
  useEffect(()=>{
    let live=true;let remove:(()=>void)|undefined;
    const update=(data:Artwork)=>{if(!live)return;setState(data);requestAnimationFrame(()=>requestAnimationFrame(()=>{if(live)void invoke('stream_artwork_painted');}));};
    void listen<Artwork>('stream-artwork',e=>update(e.payload)).then(off=>{if(live)remove=off;else off();});
    void invoke<Artwork>('get_stream_artwork').then(update);
    return()=>{live=false;remove?.();};
  },[]);
  return <div aria-label="Stream preparation" style={{position:'fixed',inset:0,display:'grid',placeContent:'center',backgroundColor:'#08090b',backgroundImage:state.art?`linear-gradient(#0003,#0006),url(${JSON.stringify(state.art)})`:undefined,backgroundSize:'cover',backgroundPosition:'center',color:'white',fontFamily:'system-ui',fontSize:'clamp(16px,2vw,28px)'}}>{state.message}</div>;
}
