"""Точка входа бота на Vercel (вне России: Telegram доступен). Логика — в ../index.py."""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import index  # noqa: E402


class handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("content-length") or 0)
        body = self.rfile.read(length).decode("utf-8") if length else "{}"
        event = {"headers": dict(self.headers.items()), "body": body}
        result = index.handler(event, None)
        self.send_response(result.get("statusCode", 200))
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write((result.get("body") or "").encode("utf-8"))

    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.end_headers()
        self.wfile.write("Бот магазина Зикр-тайм работает.".encode("utf-8"))
