import { useId, useState } from "react";
import type { NewsPost } from "./news";

// A deliberate community illustration keeps unavailable/blocked articles useful
// without passing off unrelated game art as the article's own photograph.
export function NewsArtwork({ post, onImageError }: { post: NewsPost; onImageError: (post: NewsPost) => void }) {
  const id = useId();
  const [failed, setFailed] = useState<string>();
  const pc = post.subreddit === "r/pcgaming";
  const image = post.thumbnail && post.thumbnail !== failed ? post.thumbnail : undefined;
  return <>
    <svg className="crete-news-illustration" viewBox="0 0 600 360" preserveAspectRatio="xMidYMid slice" aria-hidden="true" data-news-fallback={!image}>
      <defs><linearGradient id={id} x2="1" y2="1"><stop stopColor={pc ? "#1c454b" : "#63492c"} /><stop offset="1" stopColor="#10171e" /></linearGradient></defs>
      <path fill={`url(#${id})`} d="M0 0h600v360H0z" />
      <g fill="none" stroke={pc ? "#6fb7bd" : "#e5b46c"} strokeWidth="2" opacity=".18"><circle cx="455" cy="95" r="160"/><circle cx="455" cy="95" r="125"/><path d="M0 55h600M0 110h600M0 165h600M0 220h600M80 0v360M160 0v360M240 0v360M320 0v360M400 0v360M480 0v360M560 0v360"/></g>
      <g transform="translate(330 48) rotate(-14 100 80)" fill="#121c24" stroke={pc ? "#83b8bf" : "#d5ae76"} strokeWidth="5" opacity=".85">
        {pc ? <><rect x="0" y="0" width="204" height="125" rx="12"/><path d="M102 125v24m-46 0h92M18 103h168"/><path strokeWidth="3" d="m79 35-20 20 20 20m46-40 20 20-20 20m-15-44-15 48"/></> : <><path d="M37 26h132c22 0 31 18 38 44l14 60c5 28-18 38-37 17l-28-29H51l-28 29c-19 21-42 11-37-17L0 70c7-26 16-44 37-44Z"/><path d="M40 53v43M18 75h44"/><circle cx="156" cy="59" r="7"/><circle cx="181" cy="83" r="7"/></>}
      </g>
    </svg>
    {image && <img key={image} src={image} alt="" referrerPolicy="no-referrer" loading="lazy" decoding="async" onError={() => { setFailed(image); onImageError(post); }} />}
  </>;
}
