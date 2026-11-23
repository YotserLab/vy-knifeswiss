import os
import subprocess
import tkinter as tk
from tkinter import messagebox, ttk

import adb_tools


def _version() -> str:
    try:
        import sys
        base = os.path.dirname(sys.executable) if getattr(sys, "frozen", False) else os.path.dirname(__file__)
        with open(os.path.join(base, "version.txt"), "r", encoding="utf-8") as f:
            return f.read().strip()
    except Exception:
        return "?"


class AdbWindow:
    def __init__(self, parent: tk.Tk, get_peers=None):
        self.parent = parent
        self.window = tk.Toplevel(parent)
        self.window.title(f"Ferramentas ADB - v{_version()}")
        self.window.geometry("600x700")
        self.window.minsize(500, 500)

        self.get_peers = get_peers or (lambda: {})
        self.selected_device = tk.StringVar()
        self.selected_peer = tk.StringVar()
        self.devices = []

        self._build_ui()
        self._refresh_devices()
        self._refresh_peers()

    def _build_ui(self):
        top = ttk.Frame(self.window, padding=8)
        top.pack(fill=tk.X)

        ttk.Label(top, text="Dispositivo:").pack(side=tk.LEFT)
        self.device_combo = ttk.Combobox(top, textvariable=self.selected_device, state="readonly", width=40)
        self.device_combo.pack(side=tk.LEFT, padx=4)
        ttk.Button(top, text="Atualizar", command=self._refresh_devices).pack(side=tk.RIGHT)

        info = ttk.Label(self.window, text="ADB precisa estar no PATH e depuração USB ativada.", foreground="gray")
        info.pack(anchor=tk.W, padx=8)

        # Peers from chat
        peer_frame = ttk.LabelFrame(self.window, text="Peers do chat", padding=8)
        peer_frame.pack(fill=tk.X, padx=8, pady=4)

        peer_top = ttk.Frame(peer_frame)
        peer_top.pack(fill=tk.X)
        self.peer_combo = ttk.Combobox(peer_top, textvariable=self.selected_peer, state="readonly", width=45)
        self.peer_combo.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 4))
        ttk.Button(peer_top, text="Atualizar", command=self._refresh_peers).pack(side=tk.LEFT)

        peer_bottom = ttk.Frame(peer_frame)
        peer_bottom.pack(fill=tk.X, pady=(4, 0))
        self.peer_ip_entry = ttk.Entry(peer_bottom, width=18)
        self.peer_ip_entry.pack(side=tk.LEFT, padx=(0, 4))
        ttk.Button(peer_bottom, text="Conectar ADB", command=self._connect_to_peer).pack(side=tk.LEFT)
        ttk.Button(peer_bottom, text="Conectar USB->TCP", command=self._tcpip_and_connect).pack(side=tk.LEFT, padx=(4, 0))

        # Actions
        actions = ttk.LabelFrame(self.window, text="Ações rápidas", padding=8)
        actions.pack(fill=tk.X, padx=8, pady=8)

        buttons = [
            ("Home", lambda: self._key("HOME")),
            ("Back", lambda: self._key("BACK")),
            ("Recent", lambda: self._key("RECENT")),
            ("Power", lambda: self._key("POWER")),
            ("Vol+", lambda: self._key("VOLUME_UP")),
            ("Vol-", lambda: self._key("VOLUME_DOWN")),
        ]
        for label, cmd in buttons:
            ttk.Button(actions, text=label, command=cmd).pack(side=tk.LEFT, padx=2)

        ttk.Button(actions, text="Screenshot", command=self._screenshot).pack(side=tk.LEFT, padx=(12, 2))
        ttk.Button(actions, text="scrcpy", command=self._open_scrcpy).pack(side=tk.LEFT, padx=(12, 2))

        # Text input
        text_frame = ttk.LabelFrame(self.window, text="Enviar texto", padding=8)
        text_frame.pack(fill=tk.X, padx=8, pady=4)
        self.text_entry = ttk.Entry(text_frame)
        self.text_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 4))
        ttk.Button(text_frame, text="Enviar", command=self._send_text).pack(side=tk.RIGHT)

        # Tap / Swipe
        touch_frame = ttk.LabelFrame(self.window, text="Toque (x y)", padding=8)
        touch_frame.pack(fill=tk.X, padx=8, pady=4)
        self.tap_x = ttk.Entry(touch_frame, width=8)
        self.tap_y = ttk.Entry(touch_frame, width=8)
        self.tap_x.pack(side=tk.LEFT)
        ttk.Label(touch_frame, text=",").pack(side=tk.LEFT)
        self.tap_y.pack(side=tk.LEFT)
        ttk.Button(touch_frame, text="Tap", command=self._tap).pack(side=tk.LEFT, padx=(8, 2))

        # Shell
        shell_frame = ttk.LabelFrame(self.window, text="Comando shell", padding=8)
        shell_frame.pack(fill=tk.X, padx=8, pady=4)
        self.shell_entry = ttk.Entry(shell_frame)
        self.shell_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 4))
        ttk.Button(shell_frame, text="Executar", command=self._shell).pack(side=tk.RIGHT)

        # Output
        ttk.Label(self.window, text="Saída:").pack(anchor=tk.W, padx=8)
        self.output = tk.Text(self.window, wrap=tk.WORD, height=15, state=tk.DISABLED)
        self.output.pack(fill=tk.BOTH, expand=True, padx=8, pady=4)

        # Wireless connect
        net_frame = ttk.LabelFrame(self.window, text="Conectar via rede (IP:porta)", padding=8)
        net_frame.pack(fill=tk.X, padx=8, pady=4)
        self.net_entry = ttk.Entry(net_frame)
        self.net_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 4))
        ttk.Button(net_frame, text="Conectar", command=self._connect).pack(side=tk.RIGHT)

    def _log(self, msg: str):
        self.output.configure(state=tk.NORMAL)
        self.output.insert(tk.END, f"{msg}\n")
        self.output.configure(state=tk.DISABLED)
        self.output.yview(tk.END)

    def _serial(self) -> str:
        return self.selected_device.get().split(" ")[0]

    def _refresh_devices(self):
        if not adb_tools.is_available():
            messagebox.showerror("ADB", "adb não encontrado no PATH.")
            return
        self.devices = adb_tools.devices()
        values = [f"{d['serial']} ({d['state']})" for d in self.devices]
        self.device_combo["values"] = values
        if values and not self.selected_device.get():
            self.selected_device.set(values[0])
        self._log(f"Dispositivos: {len(self.devices)}")

    def _key(self, name: str):
        serial = self._serial()
        if not serial:
            return
        code, out, err = adb_tools.input_key(serial, adb_tools.KEYCODES[name])
        self._log(f"[{name}] code={code} out={out.strip()} err={err.strip()}")

    def _send_text(self):
        serial = self._serial()
        text = self.text_entry.get().strip()
        if not serial or not text:
            return
        code, out, err = adb_tools.input_text(serial, text)
        self._log(f"[text] code={code} err={err.strip()}")

    def _tap(self):
        serial = self._serial()
        try:
            x = int(self.tap_x.get())
            y = int(self.tap_y.get())
        except ValueError:
            return
        code, out, err = adb_tools.input_tap(serial, x, y)
        self._log(f"[tap {x},{y}] code={code} err={err.strip()}")

    def _shell(self):
        serial = self._serial()
        cmd = self.shell_entry.get().strip()
        if not serial or not cmd:
            return
        code, out, err = adb_tools.shell(serial, cmd)
        self._log(f"[shell] {cmd}\ncode={code}\n{out.strip()}\n{err.strip()}")

    def _screenshot(self):
        serial = self._serial()
        if not serial:
            return
        import sys, tempfile, time
        base = os.path.dirname(sys.executable) if getattr(sys, "frozen", False) else os.path.dirname(__file__)
        path = os.path.join(base, f"screenshot_{int(time.time())}.png")
        code, msg = adb_tools.screenshot(serial, path)
        self._log(f"[screenshot] code={code} path={msg}")

    def _connect(self):
        addr = self.net_entry.get().strip()
        if not addr:
            return
        ip, _, port = addr.partition(":")
        port = int(port) if port else 5555
        code, out, err = adb_tools.connect_wireless(ip, port)
        self._log(f"[connect] code={code} {out.strip()} {err.strip()}")
        self._refresh_devices()

    def _open_scrcpy(self):
        scrcpy_path = r"D:\desenv\tools\scrcpy-win64-v2.4\scrcpy.exe"
        if not os.path.exists(scrcpy_path):
            messagebox.showinfo("scrcpy", f"scrcpy não encontrado em:\n{scrcpy_path}")
            return
        serial = self._serial()
        ip = self.peer_ip_entry.get().strip()
        try:
            if serial:
                subprocess.Popen([scrcpy_path, "-s", serial], creationflags=subprocess.CREATE_NO_WINDOW)
                self._log(f"[scrcpy] started for {serial}")
            elif ip:
                subprocess.Popen([scrcpy_path, "--tcpip=" + ip], creationflags=subprocess.CREATE_NO_WINDOW)
                self._log(f"[scrcpy] started over tcpip {ip}")
            else:
                subprocess.Popen([scrcpy_path], creationflags=subprocess.CREATE_NO_WINDOW)
                self._log("[scrcpy] started")
        except Exception as e:
            self._log(f"[scrcpy] error: {e}")

    def _refresh_peers(self):
        peers = self.get_peers()
        values = []
        self._peer_ips = {}
        for norm, data in peers.items():
            display = data.get("display", norm)
            for ip in data.get("ips", set()):
                label = f"{display} - {ip}"
                values.append(label)
                self._peer_ips[label] = ip
        self.peer_combo["values"] = values
        if values and not self.selected_peer.get():
            self.selected_peer.set(values[0])
            self._set_peer_ip(values[0])
        self.peer_combo.bind("<<ComboboxSelected>>", lambda e: self._set_peer_ip(self.peer_combo.get()))

    def _set_peer_ip(self, label: str):
        ip = self._peer_ips.get(label, "")
        self.peer_ip_entry.delete(0, tk.END)
        self.peer_ip_entry.insert(0, ip)

    def _connect_to_peer(self):
        ip = self.peer_ip_entry.get().strip()
        if not ip:
            return
        code, out, err = adb_tools.connect_wireless(ip, 5555)
        self._log(f"[connect peer] {ip}:5555 code={code} {out.strip()} {err.strip()}")
        self._refresh_devices()

    def _tcpip_and_connect(self):
        """Enable ADB over TCP on a USB connected device then connect to the peer IP."""
        serial = self._serial()
        ip = self.peer_ip_entry.get().strip()
        if not serial:
            messagebox.showinfo("ADB", "Selecione um dispositivo USB conectado primeiro.")
            return
        if not ip:
            return
        code, out, err = adb_tools.shell(serial, "setprop service.adb.tcp.port 5555")
        self._log(f"[tcpip setup] code={code} {err.strip()}")
        code2, out2, err2 = adb_tools._run(["-s", serial, "tcpip", "5555"])
        self._log(f"[adb tcpip] code={code2} {err2.strip()}")
        code3, out3, err3 = adb_tools.connect_wireless(ip, 5555)
        self._log(f"[connect wireless] {ip}:5555 code={code3} {out3.strip()} {err3.strip()}")
        self._refresh_devices()
