import base64
import json
import os
import re
import socket
import sys
import threading
import time
import tkinter as tk
from tkinter import filedialog, messagebox, ttk, scrolledtext

import adb_window
import crypto
import file_server
import file_transfer

UDP_PORT = 9988
TCP_PORT = 9989
BROADCAST_INTERVAL = 5


def _base_dir() -> str:
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.abspath(__file__))


def _messages_file() -> str:
    return os.path.join(_base_dir(), "messages.json")


def _version() -> str:
    try:
        with open(os.path.join(_base_dir(), "version.txt"), "r", encoding="utf-8") as f:
            return f.read().strip()
    except Exception:
        return "?"


def _received_dir() -> str:
    return os.path.join(_base_dir(), "received")


def _log(msg: str):
    try:
        with open(os.path.join(_base_dir(), "debug.log"), "a", encoding="utf-8") as f:
            f.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} {msg}\n")
    except Exception:
        pass


def _normalize(s: str) -> str:
    s = re.sub(r'[^\x20-\x7E]', '', s)
    return " ".join(s.strip().lower().split())


def get_my_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.settimeout(0.5)
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except Exception:
        return socket.gethostbyname(socket.gethostname())


def is_own_ip(ip: str) -> bool:
    return ip in ("", "127.0.0.1") or ip == get_my_ip()


class ChatApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title(f"VY Swiss Knife - Chat Local (Windows) v{_version()}")
        self.root.geometry("900x600")
        self.root.minsize(700, 450)

        self.my_ip = get_my_ip()
        self.device_name = tk.StringVar(value=socket.gethostname())
        self.peers = {}  # norm_name -> {"display": str, "ips": set}
        self.selected_peer = tk.StringVar()
        self.messages = {}  # norm_name -> list of messages
        self.running = True
        self.file_server = file_server.FileServer(host="0.0.0.0", port=0)

        self._load_messages()
        self._build_ui()
        self._start_networking()

    def _build_ui(self):
        top_frame = ttk.Frame(self.root, padding=8)
        top_frame.pack(fill=tk.X)

        ttk.Label(top_frame, text="Nome:").pack(side=tk.LEFT)
        self.name_entry = ttk.Entry(top_frame, textvariable=self.device_name, width=30)
        self.name_entry.pack(side=tk.LEFT, padx=4)

        ttk.Label(top_frame, text="Meu IP:").pack(side=tk.LEFT, padx=(16, 0))
        self.ip_label = ttk.Label(top_frame, text=self.my_ip)
        self.ip_label.pack(side=tk.LEFT, padx=4)

        ttk.Button(top_frame, text="Ferramentas ADB", command=self._open_adb).pack(side=tk.RIGHT, padx=(0, 8))
        ttk.Button(top_frame, text="Atualizar peers", command=self._send_discovery_now).pack(side=tk.RIGHT)

        paned = ttk.Panedwindow(self.root, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)

        left_frame = ttk.LabelFrame(paned, text="Dispositivos", padding=8)
        paned.add(left_frame, weight=1)

        self.peer_listbox = tk.Listbox(left_frame)
        self.peer_listbox.pack(fill=tk.BOTH, expand=True)
        self.peer_listbox.bind("<<ListboxSelect>>", self._on_peer_select)

        right_frame = ttk.Frame(paned, padding=8)
        paned.add(right_frame, weight=3)

        ttk.Label(right_frame, text="Conversa").pack(anchor=tk.W)
        self.history = scrolledtext.ScrolledText(right_frame, wrap=tk.WORD, state=tk.DISABLED)
        self.history.pack(fill=tk.BOTH, expand=True, pady=4)

        btn_frame = ttk.Frame(right_frame)
        btn_frame.pack(fill=tk.X)
        ttk.Button(btn_frame, text="Limpar conversa", command=self._clear_conversation).pack(side=tk.LEFT, padx=(0, 4))
        ttk.Button(btn_frame, text="Apagar última", command=self._delete_last).pack(side=tk.LEFT, padx=(0, 4))
        ttk.Button(btn_frame, text="Apagar selecionada", command=self._delete_selected).pack(side=tk.LEFT, padx=(0, 4))

        bottom_frame = ttk.Frame(right_frame)
        bottom_frame.pack(fill=tk.X, pady=(4, 0))

        ttk.Button(bottom_frame, text="Anexar arquivo", command=self._send_file).pack(side=tk.LEFT, padx=(0, 4))

        self.input_field = ttk.Entry(bottom_frame)
        self.input_field.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 4))
        self.input_field.bind("<Return>", lambda e: self._send_message())

        ttk.Button(bottom_frame, text="Enviar", command=self._send_message).pack(side=tk.RIGHT)

    def _start_networking(self):
        self.file_server.start()

        self.udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.udp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            self.udp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        except Exception:
            pass
        self.udp_socket.bind(("0.0.0.0", UDP_PORT))
        self.udp_socket.settimeout(1.0)

        self.tcp_server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.tcp_server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.tcp_server.bind(("0.0.0.0", TCP_PORT))
        self.tcp_server.listen(10)
        self.tcp_server.settimeout(1.0)

        threading.Thread(target=self._udp_listener, daemon=True).start()
        threading.Thread(target=self._udp_broadcaster, daemon=True).start()
        threading.Thread(target=self._tcp_server, daemon=True).start()

    def _load_messages(self):
        path = _messages_file()
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    self.messages = json.load(f)
            except Exception:
                self.messages = {}
        for name in self.messages:
            self.messages[name] = [
                {k: v for k, v in m.items() if k != "time"} | {"time": m.get("time", "")}
                for m in self.messages[name]
            ]

    def _save_messages(self):
        try:
            with open(_messages_file(), "w", encoding="utf-8") as f:
                json.dump(self.messages, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

    def _on_peer_select(self, event=None):
        selection = self.peer_listbox.curselection()
        if not selection:
            return
        label = self.peer_listbox.get(selection[0])
        display = label.split(" - ")[0].strip()
        name = _normalize(display)
        self.selected_peer.set(name)
        self._render_history()

    def _add_peer(self, name: str, ip: str):
        if is_own_ip(ip):
            return
        display = name.strip()
        norm = _normalize(display)
        if norm not in self.peers:
            self.peers[norm] = {"display": display, "ips": set()}
        if ip not in self.peers[norm]["ips"]:
            _log(f"[add_peer] display='{display}' norm='{norm}' ip={ip}")
            self.peers[norm]["ips"].add(ip)
            if self.peers[norm]["display"] != display and display:
                self.peers[norm]["display"] = display
            self.root.after(0, self._render_peer_list)

    def _find_peer_name(self, ip: str, sender: str = "") -> str:
        for norm, data in self.peers.items():
            if ip in data["ips"]:
                return norm
        if sender:
            norm = _normalize(sender)
            for n in self.peers:
                if n == norm:
                    return n
        return _normalize(sender) if sender else ""

    def _render_peer_list(self):
        selected = self.selected_peer.get()
        self.peer_listbox.delete(0, tk.END)
        for norm in sorted(self.peers.keys(), key=lambda x: x.lower()):
            data = self.peers[norm]
            ips = ", ".join(sorted(data["ips"]))
            label = f"{data['display']} - {ips}"
            self.peer_listbox.insert(tk.END, label)
            if norm == selected:
                self.peer_listbox.selection_set(tk.END)

    def _append_message(self, name: str, sender: str, content: str, is_me: bool = False):
        if not name:
            return
        if name not in self.messages:
            self.messages[name] = []
        self.messages[name].append({
            "sender": sender,
            "content": content,
            "is_me": is_me,
            "time": time.strftime("%H:%M:%S"),
        })
        _log(f"[append] name='{name}' sender='{sender}' is_me={is_me} content='{content[:40]}'")
        self._save_messages()
        if self.selected_peer.get() == name:
            self.root.after(0, self._render_history)

    def _render_history(self):
        name = self.selected_peer.get()
        self.history.configure(state=tk.NORMAL)
        self.history.delete("1.0", tk.END)
        for msg in self.messages.get(name, []):
            prefix = "Eu" if msg["is_me"] else msg["sender"]
            self.history.insert(tk.END, f"[{msg['time']}] {prefix}: {msg['content']}\n")
        self.history.configure(state=tk.DISABLED)
        self.history.yview(tk.END)

    def _clear_conversation(self):
        name = self.selected_peer.get()
        if not name:
            return
        display = self.peers.get(name, {}).get("display", name)
        if messagebox.askyesno("Confirmar", f"Apagar toda a conversa com {display}?"):
            self.messages[name] = []
            self._save_messages()
            self._render_history()

    def _delete_last(self):
        name = self.selected_peer.get()
        if not name or not self.messages.get(name):
            return
        self.messages[name].pop()
        self._save_messages()
        self._render_history()

    def _delete_selected(self):
        name = self.selected_peer.get()
        if not name or not self.messages.get(name):
            return
        try:
            self.history.focus_force()
            first = self.history.index("sel.first")
            line = int(first.split(".")[0])
            msgs = self.messages[name]
            if 1 <= line <= len(msgs):
                del msgs[line - 1]
                self._save_messages()
                self._render_history()
        except tk.TclError:
            messagebox.showinfo("Aviso", "Selecione uma linha no histórico primeiro.")

    def _send_message(self):
        text = self.input_field.get().strip()
        if not text:
            return
        name = self.selected_peer.get()
        if not name or name not in self.peers:
            return
        ips = self.peers[name]["ips"]
        if not ips:
            return
        self.input_field.delete(0, tk.END)
        self._append_message(name, self.device_name.get(), text, is_me=True)

        payload = json.dumps({
            "type": "TEXT",
            "sender": self.device_name.get(),
            "content": crypto.encrypt(text),
        }, separators=(",", ":")) + "\n"

        for ip in ips:
            threading.Thread(target=self._tcp_send, args=(name, ip, payload), daemon=True).start()

    def _tcp_send(self, name: str, ip: str, payload: str):
        try:
            with socket.create_connection((ip, TCP_PORT), timeout=5) as sock:
                sock.sendall(payload.encode("utf-8"))
        except Exception as e:
            self.root.after(0, lambda: self._append_message(
                name, "Sistema", f"Erro ao enviar para {ip}: {e}", is_me=False
            ))

    def _send_file(self):
        name = self.selected_peer.get()
        if not name or name not in self.peers:
            messagebox.showinfo("Aviso", "Selecione um dispositivo primeiro.")
            return
        path = filedialog.askopenfilename()
        if not path or not os.path.exists(path):
            return
        threading.Thread(target=self._transfer_file, args=(name, path), daemon=True).start()

    def _transfer_file(self, name: str, path: str):
        try:
            file_name = os.path.basename(path)
            file_size = os.path.getsize(path)
            ips = self.peers[name]["ips"]

            # Use base64 for files up to 10 MB to avoid HTTP/network issues
            if file_size <= 10 * 1024 * 1024:
                with open(path, "rb") as f:
                    data_b64 = base64.b64encode(f.read()).decode("utf-8")
                payload = json.dumps({
                    "type": "FILE",
                    "sender": self.device_name.get(),
                    "fileName": file_name,
                    "fileSize": file_size,
                    "fileBytesBase64": data_b64,
                }, separators=(",", ":")) + "\n"
            else:
                token = self.file_server.host_file(path)
                url = self.file_server.url_for(token, self.my_ip)
                payload = json.dumps({
                    "type": "FILE_STREAM",
                    "sender": self.device_name.get(),
                    "fileName": file_name,
                    "fileSize": file_size,
                    "url": url,
                }, separators=(",", ":")) + "\n"

            for ip in ips:
                threading.Thread(target=self._tcp_send, args=(name, ip, payload), daemon=True).start()

            self._append_message(name, self.device_name.get(), f"📤 Enviou: {file_name} ({file_size} bytes)", is_me=True)
        except Exception as e:
            self.root.after(0, lambda: self._append_message(name, "Sistema", f"Erro ao enviar arquivo: {e}", is_me=False))

    def _download_file(self, name: str, url: str, file_name: str):
        try:
            dest = _received_dir()
            final_path = file_transfer.download_file(url, dest)
            display_name = self.peers.get(name, {}).get("display", name)
            self._append_message(name, display_name, f"📥 Recebeu: {file_name} -> {final_path}", is_me=False)
        except Exception as e:
            _log(f"[download error] {e}")

    def _open_adb(self):
        adb_window.AdbWindow(self.root, get_peers=lambda: self.peers)

    def _send_discovery_now(self):
        threading.Thread(target=self._broadcast_once, daemon=True).start()

    def _broadcast_once(self):
        try:
            msg = f"CHAT_DISCOVER:{self.device_name.get()}:{self.my_ip}".encode("utf-8")
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                sock.sendto(msg, ("255.255.255.255", UDP_PORT))
        except Exception:
            pass

    def _udp_broadcaster(self):
        while self.running:
            self._broadcast_once()
            time.sleep(BROADCAST_INTERVAL)

    def _udp_listener(self):
        while self.running:
            try:
                data, addr = self.udp_socket.recvfrom(1024)
                received = data.decode("utf-8", errors="ignore").strip()
                if received.startswith("CHAT_DISCOVER:"):
                    parts = received.split(":")
                    if len(parts) >= 3:
                        name = parts[1].strip()
                        peer_ip = parts[2].strip()
                        _log(f"[UDP DISCOVER] name='{name}' ip={peer_ip}")
                        if not is_own_ip(peer_ip):
                            self._add_peer(name, peer_ip)
                            self._respond_to_peer(peer_ip)
                elif received.startswith("CHAT_ANNOUNCE:"):
                    parts = received.split(":")
                    if len(parts) >= 3:
                        name = parts[1].strip()
                        peer_ip = parts[2].strip()
                        _log(f"[UDP ANNOUNCE] name='{name}' ip={peer_ip}")
                        if not is_own_ip(peer_ip):
                            self._add_peer(name, peer_ip)
            except socket.timeout:
                continue
            except Exception:
                break

    def _respond_to_peer(self, peer_ip: str):
        try:
            msg = f"CHAT_ANNOUNCE:{self.device_name.get()}:{self.my_ip}".encode("utf-8")
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
                sock.sendto(msg, (peer_ip, UDP_PORT))
        except Exception:
            pass

    def _tcp_server(self):
        while self.running:
            try:
                client, addr = self.tcp_server.accept()
                threading.Thread(target=self._handle_client, args=(client, addr), daemon=True).start()
            except socket.timeout:
                continue
            except Exception:
                break

    def _handle_client(self, client: socket.socket, addr):
        try:
            with client:
                data = b""
                while True:
                    chunk = client.recv(4096)
                    if not chunk:
                        break
                    data += chunk
                if not data:
                    return
                try:
                    payload = json.loads(data.decode("utf-8"))
                    msg_type = payload.get("type", "TEXT")
                    sender = payload.get("sender", "Dispositivo").strip()
                    content = payload.get("content", "")
                    peer_ip = addr[0]
                    _log(f"[TCP recv] ip={peer_ip} sender='{sender}' type={msg_type}")

                    if msg_type == "TEXT":
                        content = crypto.decrypt(content)
                        name = self._find_peer_name(peer_ip, sender)
                        _log(f"[resolved] name='{name}'")
                        self._add_peer(sender, peer_ip)
                        self._append_message(name, sender, content, is_me=False)
                    elif msg_type == "NUDGE":
                        name = self._find_peer_name(peer_ip, sender)
                        self._append_message(name, sender, "🚨 Chamou sua atenção! 🚨", is_me=False)
                    elif msg_type in ("FILE", "FILE_STREAM"):
                        file_name = payload.get("fileName", "arquivo")
                        url = payload.get("url", "")
                        name = self._find_peer_name(peer_ip, sender)
                        _log(f"[file] name='{name}' file='{file_name}' url='{url}'")
                        self._add_peer(sender, peer_ip)
                        self._append_message(name, sender, f"📥 Recebendo: {file_name}", is_me=False)
                        if url:
                            threading.Thread(target=self._download_file, args=(name, url, file_name), daemon=True).start()
                except json.JSONDecodeError:
                    _log("[TCP recv] JSON decode error")
        except Exception as e:
            _log(f"[TCP handler error] {e}")

    def on_closing(self):
        self.running = False
        try:
            self.udp_socket.close()
        except Exception:
            pass
        try:
            self.tcp_server.close()
        except Exception:
            pass
        self.file_server.stop()
        self._save_messages()
        self.root.destroy()


def main():
    root = tk.Tk()
    app = ChatApp(root)
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    root.mainloop()


if __name__ == "__main__":
    main()
