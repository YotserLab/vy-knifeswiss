package com.example.plugin.bridge

object PluginJsRuntime {
    val BOOTSTRAP_JS = """
        (function() {
            if (window.VYKnife) return;
            const callbacks = {};
            let callbackCounter = 0;

            window.VYKnife = {
                _handleCallback: function(callbackId, success, dataStr) {
                    if (callbacks[callbackId]) {
                        try {
                            const parsed = JSON.parse(dataStr);
                            if (success) {
                                callbacks[callbackId].resolve(parsed);
                            } else {
                                callbacks[callbackId].reject(parsed);
                            }
                        } catch(e) {
                            if (success) callbacks[callbackId].resolve(dataStr);
                            else callbacks[callbackId].reject(dataStr);
                        }
                        delete callbacks[callbackId];
                    }
                },

                toast: function(msg) {
                    if (window.VyBridge) window.VyBridge.showToast(String(msg));
                },

                copy: function(text) {
                    if (window.VyBridge) window.VyBridge.copyToClipboard(String(text));
                },

                close: function() {
                    if (window.VyBridge) window.VyBridge.close();
                },

                getInfo: function() {
                    if (window.VyBridge) {
                        try { return JSON.parse(window.VyBridge.getPluginInfo()); }
                        catch(e) { return {}; }
                    }
                    return {};
                },

                storage: {
                    get: function(key) {
                        if (!window.VyBridge) return null;
                        return window.VyBridge.getStorage(key);
                    },
                    set: function(key, val) {
                        if (window.VyBridge) window.VyBridge.setStorage(key, String(val));
                    },
                    remove: function(key) {
                        if (window.VyBridge) window.VyBridge.removeStorage(key);
                    }
                },

                ping: function(host, timeoutMs) {
                    return new Promise((resolve, reject) => {
                        const id = 'cb_' + (++callbackCounter);
                        callbacks[id] = { resolve, reject };
                        if (window.VyBridge) {
                            window.VyBridge.ping(host, timeoutMs || 3000, id);
                        } else {
                            reject(new Error("VyBridge not available"));
                        }
                    });
                },

                checkPort: function(host, port, timeoutMs) {
                    return new Promise((resolve, reject) => {
                        const id = 'cb_' + (++callbackCounter);
                        callbacks[id] = { resolve, reject };
                        if (window.VyBridge) {
                            window.VyBridge.checkPort(host, port, timeoutMs || 2500, id);
                        } else {
                            reject(new Error("VyBridge not available"));
                        }
                    });
                },

                fetch: function(url, options) {
                    options = options || {};
                    const method = options.method || 'GET';
                    const headers = JSON.stringify(options.headers || {});
                    const body = options.body ? (typeof options.body === 'string' ? options.body : JSON.stringify(options.body)) : '';
                    return new Promise((resolve, reject) => {
                        const id = 'cb_' + (++callbackCounter);
                        callbacks[id] = { resolve, reject };
                        if (window.VyBridge) {
                            window.VyBridge.httpFetch(url, method, headers, body, id);
                        } else {
                            reject(new Error("VyBridge not available"));
                        }
                    });
                }
            };
        })();
    """.trimIndent()
}
