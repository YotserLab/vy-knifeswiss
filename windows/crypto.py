import base64
import hashlib
import os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

PASSPHRASE = b"VYKnIFE-SWISS-CHAT-KEY"
KEY = hashlib.sha256(PASSPHRASE).digest()
IV_LENGTH = 12


def encrypt(plaintext: str) -> str:
    """Encrypt plaintext using the same AES-GCM format as the Android app."""
    iv = os.urandom(IV_LENGTH)
    aesgcm = AESGCM(KEY)
    ciphertext = aesgcm.encrypt(iv, plaintext.encode("utf-8"), None)
    # ciphertext already includes the 16-byte auth tag appended
    combined = iv + ciphertext
    return "ENC:" + base64.b64encode(combined).decode("utf-8")


def decrypt(ciphertext_b64: str) -> str:
    """Decrypt a payload produced by the Android app or by encrypt()."""
    if not ciphertext_b64.startswith("ENC:"):
        return ciphertext_b64
    try:
        raw = base64.b64decode(ciphertext_b64[4:].encode("utf-8"))
        if len(raw) <= IV_LENGTH:
            return ciphertext_b64
        iv = raw[:IV_LENGTH]
        payload = raw[IV_LENGTH:]
        aesgcm = AESGCM(KEY)
        return aesgcm.decrypt(iv, payload, None).decode("utf-8")
    except Exception:
        return ciphertext_b64
