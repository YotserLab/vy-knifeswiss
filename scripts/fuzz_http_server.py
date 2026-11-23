#!/usr/bin/env python3
"""Socket-level fuzzer for VYKnifeSwiss InternalHttpFileServer.

Targets the LAN-facing file server (GET/HEAD /download/<token>) and probes
parser edge cases + resource-exhaustion weaknesses found in review:

  M1  unbounded thread-per-connection, no write timeout, slowloris window
  M2  method prefix-match: "GETX"/"HEADER" treated as GET/HEAD
  M3  temp-file TTL race (hard to hit externally; not covered here)
  +   431 budget: >16KB head, >64 header lines
  +   parseRange mutations, path/token weirdness, obs-fold, LF-only heads

Usage:
  python scripts/fuzz_http_server.py --host 192.168.1.50 --port 8080
  python scripts/fuzz_http_server.py --host 192.168.1.50 --port 8080 \
      --token <valid-token>            # enables 200/416/206 expectations
  python scripts/fuzz_http_server.py --host 192.168.1.50 --port 8080 --slow 40

The server port is dynamic — read it from logcat ("CastHttp") or add a debug
print. --token can be captured the same way from a logged request line.
Stdlib only, no deps.
"""
import argparse
import socket
import sys
import time

DEADLINE = 5.0  # per-connection read timeout


def send_recv(host, port, payload, recv_bytes=4096):
    """Send raw payload, read until close or timeout. Returns (status_line, elapsed, err)."""
    t0 = time.time()
    try:
        with socket.create_connection((host, port), timeout=DEADLINE) as s:
            s.settimeout(DEADLINE)
            s.sendall(payload)
            data = b""
            try:
                while len(data) < recv_bytes:
                    chunk = s.recv(min(2048, recv_bytes - len(data)))
                    if not chunk:
                        break
                    data += chunk
            except socket.timeout:
                pass  # server held connection open — note it, not fatal
            line = data.split(b"\r\n", 1)[0].decode("iso-8859-1", "replace")
            return line or "(no response)", round(time.time() - t0, 2), None
    except Exception as e:
        return "(conn error)", round(time.time() - t0, 2), str(e)


def head(n_bytes):
    """HEAD of n_bytes of 'A' padding — fills the request head budget."""
    return b"A" * n_bytes


def many_headers(n):
    return b"".join(b"X-Pad-%d: v\r\n" % i for i in range(n))


def cases(host, port, token):
    tok = token or "deadbeef-dead-beef-dead-beefdeadbeef"
    expect_file = token is not None

    def expect(got, *codes):
        return any(got.startswith(f"HTTP/1.1 {c}") for c in codes)

    return [
        # (name, payload, check(got)->bool|None, note)
        ("baseline GET", f"GET /download/{tok} HTTP/1.1\r\n\r\n".encode(),
         (lambda g: expect(g, "200") if expect_file else expect(g, "404")), ""),
        ("M2 prefix GETX", f"GETX /download/{tok} HTTP/1.1\r\n\r\n".encode(),
         lambda g: expect(g, "404", "405"), "startsWith('GET') leak — full GET if token valid"),
        ("M2 prefix GETAAA", f"GETAAA /download/{tok} HTTP/1.1\r\n\r\n".encode(),
         lambda g: expect(g, "404", "405"), ""),
        ("M2 prefix HEADER", f"HEADER /download/{tok} HTTP/1.1\r\n\r\n".encode(),
         lambda g: expect(g, "404", "405"), "startsWith('HEAD') → headOnly=false → body served"),
        ("real HEAD", f"HEAD /download/{tok} HTTP/1.1\r\n\r\n".encode(), None, "headers only expected"),
        ("POST", f"POST /download/{tok} HTTP/1.1\r\nContent-Length: 3\r\n\r\nabc".encode(),
         lambda g: expect(g, "405"), ""),
        ("no version", f"GET /download/{tok}\r\n\r\n".encode(), None, "lenient — check manually"),
        ("junk version", f"GET /download/{tok} GARBAGE\r\n\r\n".encode(), None, ""),
        ("double space", f"GET  /download/{tok} HTTP/1.1\r\n\r\n".encode(),
         lambda g: expect(g, "404"), "parts[1]=='' → 404"),
        ("tab separator", f"GET\t/download/{tok}\tHTTP/1.1\r\n\r\n".encode(),
         lambda g: expect(g, "404", "405", "431"), "no ' ' → single token, not GET*"),
        # head budget
        ("head 16KB+1", b"GET /download/x HTTP/1.1\r\n" + head(16380) + b"\r\n\r\n",
         lambda g: expect(g, "431"), "exceeds MAX_REQUEST_HEAD_BYTES"),
        ("64 header lines ok", b"GET /download/x HTTP/1.1\r\n" + many_headers(63) + b"\r\n",
         lambda g: expect(g, "404"), "63 custom lines within limit"),
        ("65 header lines 431", b"GET /download/x HTTP/1.1\r\n" + many_headers(65) + b"\r\n",
         lambda g: expect(g, "431"), ">64 → 431"),
        # Range mutations (file only if token valid; otherwise still 404)
        ("Range bytes=-", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=-\r\n\r\n".encode(),
         None, "suffix '-' → 416 or 404"),
        ("Range bytes=-0", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=-0\r\n\r\n".encode(),
         None, "suffix 0 → 416"),
        ("Range bytes=-5", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=-5\r\n\r\n".encode(),
         None, "suffix len"),
        ("Range 5-3", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=5-3\r\n\r\n".encode(),
         None, "end<start → 416"),
        ("Range huge start", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=99999999999999999999-0\r\n\r\n".encode(),
         None, "Long overflow → null → 416"),
        ("Range huge end", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=0-99999999999999999999\r\n\r\n".encode(),
         None, "toLongOrNull null → 416"),
        ("Range clamp", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=0-999999\r\n\r\n".encode(),
         None, "end clamps to fileLen-1"),
        ("Range multi", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=0-1,3-4\r\n\r\n".encode(),
         None, "comma → 416"),
        ("Range alpha", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=a-b\r\n\r\n".encode(), None, ""),
        ("Range no dash", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=100\r\n\r\n".encode(), None, ""),
        ("Range wrong unit", f"GET /download/{tok} HTTP/1.1\r\nRange: items=0-1\r\n\r\n".encode(),
         None, "not bytes= → ignored → 200/404"),
        ("Range CRLF inject", f"GET /download/{tok} HTTP/1.1\r\nRange: bytes=0-1\r\nX-Evil: 1\r\n\r\n".encode(),
         None, "header value can't inject (value only parsed)"),
        # token/path weirdness
        ("path traversal", b"GET /download/../x HTTP/1.1\r\n\r\n",
         lambda g: expect(g, "404"), "token='../x' → map miss"),
        ("encoded traversal", b"GET /download/%2e%2e/x HTTP/1.1\r\n\r\n",
         lambda g: expect(g, "404"), ""),
        ("token with space", b"GET /download/a b HTTP/1.1\r\n\r\n", None, "parts[1]='a'"),
        ("query junk", f"GET /download/{tok}?foo=bar HTTP/1.1\r\n\r\n".encode(), None, "query stripped"),
        ("root path", b"GET / HTTP/1.1\r\n\r\n", lambda g: expect(g, "404"), ""),
        ("obs-fold", f"GET /download/{tok} HTTP/1.1\r\nX-A: 1\r\n Range: bytes=0-1\r\n\r\n".encode(),
         None, "leading space = continuation per RFC; here new header — watch for desync"),
        ("LF-only head", f"GET /download/{tok} HTTP/1.1\n\n".encode(),
         lambda g: expect(g, "431", "405", "404"), "no CRLFCRLF → 431 or silent close"),
        ("CR-only head", f"GET /download/{tok} HTTP/1.1\r\r".encode(),
         lambda g: expect(g, "431"), ""),
        ("empty request", b"", lambda g: g == "(no response)", "peer close → silent close expected"),
    ]


def slowloris(host, port, n):
    """M1 probe: N sockets each dribbling 1 byte every ~2s of a partial request."""
    socks = []
    for _ in range(n):
        try:
            s = socket.create_connection((host, port), timeout=DEADLINE)
            s.sendall(b"G")
            socks.append(s)
        except Exception as e:
            print(f"  connect failed at {len(socks)}: {e}")
            break
    print(f"  held {len(socks)}/{n} partial connections open")
    for s in socks:
        try:
            s.sendall(b"E")
        except Exception:
            pass
    print("  sent second byte — server threads stay parked inside readRequestHead")
    time.sleep(1.5)
    # Can the server still answer a fresh, valid request?
    line, _, err = send_recv(host, port, b"GET / HTTP/1.1\r\n\r\n")
    print(f"  fresh request during hold: {line} err={err}")
    for s in socks:
        try:
            s.close()
        except Exception:
            pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", required=True)
    ap.add_argument("--port", type=int, required=True)
    ap.add_argument("--token", help="valid hosted-file token (from logcat)")
    ap.add_argument("--slow", type=int, metavar="N", help="run slowloris probe with N sockets")
    args = ap.parse_args()

    print(f"target {args.host}:{args.port} token={'yes' if args.token else 'no'}\n")
    fails = 0
    for name, payload, check, note in cases(args.host, args.port, args.token):
        got, dt, err = send_recv(args.host, args.port, payload)
        mark = "   "
        if err:
            mark = " ! "
        elif check is not None:
            mark = " ✓ " if check(got) else " ✗ "
            if not check(got):
                fails += 1
        extra = f"  ({note})" if note else ""
        print(f"{mark}{name:<22} {got:<44} {dt:>5}s{extra}")

    if args.slow:
        print(f"\nslowloris probe (M1):")
        slowloris(args.host, args.port, args.slow)

    # liveness after the storm
    line, _, err = send_recv(args.host, args.port, b"GET / HTTP/1.1\r\n\r\n")
    print(f"\nserver alive: {line} err={err}")
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
