/**
 * Server-side Pixabay proxy. Keep PIXABAY_API_KEY in Vercel's sensitive
 * environment variables; never send it to the browser or Android client.
 */
export default async function handler(req, res) {
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Cache-Control", "s-maxage=86400, stale-while-revalidate=86400, max-age=0");

  if (req.method !== "GET") {
    res.setHeader("Allow", "GET");
    res.statusCode = 405;
    res.end(JSON.stringify({ ok: false, error: "Use GET." }));
    return;
  }

  const apiKey = process.env.PIXABAY_API_KEY;
  if (!apiKey) {
    res.statusCode = 503;
    res.setHeader("Cache-Control", "no-store");
    res.end(JSON.stringify({ ok: false, error: "Pixabay search is not configured." }));
    return;
  }

  const type = String(req.query?.type || "images").toLowerCase();
  const query = String(req.query?.q || "").trim().slice(0, 100);
  if (!(["images", "videos"].includes(type))) {
    res.statusCode = 400;
    res.setHeader("Cache-Control", "no-store");
    res.end(JSON.stringify({ ok: false, error: "Choose images or videos and enter a search term." }));
    return;
  }

  try {
    const endpoint = type === "videos" ? "https://pixabay.com/api/videos/" : "https://pixabay.com/api/";
    const upstreamUrl = new URL(endpoint);
    upstreamUrl.searchParams.set("key", apiKey);
    if (query) upstreamUrl.searchParams.set("q", query);
    upstreamUrl.searchParams.set("per_page", "20");
    upstreamUrl.searchParams.set("safesearch", "true");
    if (type === "images") {
      upstreamUrl.searchParams.set("image_type", "all");
      upstreamUrl.searchParams.set("orientation", "all");
    }

    const upstream = await fetch(upstreamUrl, {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(15000),
    });
    if (!upstream.ok) {
      res.statusCode = upstream.status === 429 ? 429 : 502;
      res.setHeader("Cache-Control", "no-store");
      res.end(JSON.stringify({ ok: false, error: upstream.status === 429 ? "Pixabay search limit reached. Try again shortly." : "Pixabay search is temporarily unavailable." }));
      return;
    }

    const data = await upstream.json();
    const hits = (Array.isArray(data.hits) ? data.hits : []).map((hit) => ({
      id: Number(hit.id) || -1,
      pageURL: typeof hit.pageURL === "string" ? hit.pageURL : "",
      tags: typeof hit.tags === "string" ? hit.tags.slice(0, 140) : "Pixabay media",
      user: typeof hit.user === "string" ? hit.user.slice(0, 80) : "Pixabay contributor",
      previewURL: typeof hit.previewURL === "string" ? hit.previewURL : "",
      webformatURL: typeof hit.webformatURL === "string" ? hit.webformatURL : "",
      videos: type === "videos" ? { medium: { url: hit.videos?.medium?.url || "", thumbnail: hit.videos?.medium?.thumbnail || "" } } : undefined,
    })).filter((hit) => hit.id > 0 && hit.pageURL.startsWith("https://pixabay.com/"));

    res.statusCode = 200;
    res.setHeader("X-Pixabay-RateLimit-Remaining", upstream.headers.get("X-RateLimit-Remaining") || "unknown");
    res.end(JSON.stringify({ totalHits: Number(data.totalHits) || 0, hits }));
  } catch (error) {
    res.statusCode = 502;
    res.setHeader("Cache-Control", "no-store");
    res.end(JSON.stringify({ ok: false, error: "Pixabay search is temporarily unavailable." }));
  }
}
