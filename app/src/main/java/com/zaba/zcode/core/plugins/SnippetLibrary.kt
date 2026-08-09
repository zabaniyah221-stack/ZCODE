package com.zaba.zcode.core.plugins

/**
 * SnippetLibrary — Snippet Pack (batch anti-sepi, S5).
 *
 * Template di-port dari SNIPPET_TEMPLATES ZABACODE (GPLv3, same author —
 * lihat docs/PLAN_BATCH_ANTI_SEPI.md §2.5).
 *
 * CATATAN SINKRONISASI: konten di sini IDENTIK dengan array SNIPPETS di
 * editor-src/src/editor.js (item autocomplete). Bila diedit, ubah keduanya
 * + rebuild bundle.
 */
data class Snippet(val id: String, val name: String, val description: String, val code: String)

object SnippetLibrary {

    val snippets: List<Snippet> = listOf(
        Snippet(
            "flask_app", "Flask Web App", "Server Flask minimal + 2 endpoint JSON",
            """
            |from flask import Flask, jsonify
            |
            |app = Flask(__name__)
            |
            |@app.route("/")
            |def index():
            |    return jsonify({"message": "Hello from ZCODE!"})
            |
            |@app.route("/api/data")
            |def get_data():
            |    return jsonify({"items": []})
            |
            |if __name__ == "__main__":
            |    app.run(host="127.0.0.1", port=5000)
            |""".trimMargin()
        ),
        Snippet(
            "web_scraper", "Web Scraper (BS4)", "requests + BeautifulSoup, butuh: pip install requests beautifulsoup4",
            """
            |import requests
            |from bs4 import BeautifulSoup
            |
            |url = "https://example.com"
            |response = requests.get(url, timeout=10)
            |
            |if response.status_code == 200:
            |    soup = BeautifulSoup(response.text, "html.parser")
            |    titles = soup.find_all("h1")
            |    for title in titles:
            |        print(title.get_text(strip=True))
            |else:
            |    print(f"Error: {response.status_code}")
            |""".trimMargin()
        ),
        Snippet(
            "async_fetch", "Async HTTP Fetcher", "asyncio + gather, tanpa dependensi eksternal",
            """
            |import asyncio
            |import urllib.request
            |
            |async def fetch(url):
            |    loop = asyncio.get_event_loop()
            |    req = urllib.request.Request(url)
            |    response = await loop.run_in_executor(None, lambda: urllib.request.urlopen(req, timeout=10))
            |    data = response.read().decode("utf-8", errors="replace")
            |    print(f"Fetched {len(data)} bytes from {url}")
            |    return data
            |
            |async def main():
            |    urls = ["https://httpbin.org/get", "https://httpbin.org/ip"]
            |    results = await asyncio.gather(*[fetch(u) for u in urls])
            |    for r in results:
            |        print(r[:200])
            |
            |asyncio.run(main())
            |""".trimMargin()
        ),
        Snippet(
            "rest_api", "REST API Client", "GET/POST JSON via urllib, tanpa dependensi eksternal",
            """
            |import json
            |import urllib.request
            |
            |def api_get(url, headers=None):
            |    req = urllib.request.Request(url, headers=headers or {})
            |    with urllib.request.urlopen(req, timeout=10) as resp:
            |        return json.loads(resp.read())
            |
            |def api_post(url, data, headers=None):
            |    body = json.dumps(data).encode()
            |    hdrs = {"Content-Type": "application/json"}
            |    if headers:
            |        hdrs.update(headers)
            |    req = urllib.request.Request(url, data=body, headers=hdrs)
            |    with urllib.request.urlopen(req, timeout=10) as resp:
            |        return json.loads(resp.read())
            |
            |# Example
            |result = api_get("https://httpbin.org/get")
            |print(json.dumps(result, indent=2))
            |""".trimMargin()
        ),
    )
}
