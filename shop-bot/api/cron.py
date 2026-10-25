"""Периодический вызов (GitHub Actions раз в 15 минут): новые отзывы — владельцу на проверку."""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index  # noqa: E402


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        q = parse_qs(urlparse(self.path).query)
        if q.get("k", [""])[0] != index.cron_secret():
            self.send_response(403)
            self.end_headers()
            return
        try:
            result = index.run_cron(force=q.get("force", [""])[0] == "1")
            code = 200
        except Exception as e:
            result, code = {"error": type(e).__name__, "detail": str(e)[:200]}, 500
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(result, ensure_ascii=False).encode("utf-8"))
