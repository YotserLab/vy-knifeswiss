import http.server
import os
import socketserver
import threading
import time
import uuid

TOKEN_TTL = 5 * 60  # seconds


class FileHandler(http.server.BaseHTTPRequestHandler):
    files = {}  # token -> (path, expires_at)

    def do_GET(self):
        try:
            client = self.client_address[0]
            if self.path.startswith("/download/"):
                token = self.path.split("/")[-1].split("?")[0]
                self._log(f"[http] GET from {client} token={token}")
                entry = FileHandler.files.get(token)
                if not entry:
                    self._log(f"[http] token not found")
                    self._send(404, b"Not found")
                    return
                path, expires_at = entry
                if time.time() > expires_at:
                    FileHandler.files.pop(token, None)
                    self._send(404, b"Expired")
                    return
                if not os.path.exists(path):
                    self._send(404, b"Not found")
                    return
                file_size = os.path.getsize(path)
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", str(file_size))
                self.send_header("Connection", "close")
                self.end_headers()
                with open(path, "rb") as f:
                    while True:
                        chunk = f.read(8192)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                FileHandler.files.pop(token, None)
                self._log(f"[http] sent {path} ({file_size} bytes)")
                return
            self._send(404, b"Not found")
        except Exception as e:
            self._log(f"[http] error: {e}")

    def _log(self, msg: str):
        try:
            import os, sys
            base = os.path.dirname(sys.executable) if getattr(sys, "frozen", False) else os.path.dirname(__file__)
            with open(os.path.join(base, "debug.log"), "a", encoding="utf-8") as f:
                f.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} {msg}\n")
        except Exception:
            pass

    def _send(self, code: int, body: bytes):
        self.send_response(code)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args, **kwargs):
        pass


class FileServer:
    def __init__(self, host: str = "0.0.0.0", port: int = 0):
        self.host = host
        self.port = port
        self._server = None
        self._thread = None

    def start(self):
        if self._server:
            return
        try:
            self._server = socketserver.TCPServer((self.host, self.port), FileHandler)
            self.port = self._server.server_address[1]
            self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)
            self._thread.start()
        except Exception:
            pass

    def stop(self):
        if self._server:
            try:
                self._server.shutdown()
                self._server.server_close()
            except Exception:
                pass
            self._server = None

    def host_file(self, path: str) -> str:
        token = str(uuid.uuid4())
        FileHandler.files[token] = (path, time.time() + TOKEN_TTL)
        return token

    def url_for(self, token: str, my_ip: str) -> str:
        return f"http://{my_ip}:{self.port}/download/{token}"
