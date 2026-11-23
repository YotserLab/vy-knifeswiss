package com.example.ui.screens.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import com.example.utils.net.NetworkUtils
import java.text.SimpleDateFormat
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.Date
import java.util.Locale
import com.example.R
import com.example.Res

class FTPServer(private val scope: CoroutineScope, private val baseDir: File) {

    private var serverJob: Job? = null
    private var controlSocket: ServerSocket? = null
    var isRunning: Boolean = false
        private set
    val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var sessionPassword: String = ""

    private fun generateSessionPassword(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    fun start(port: Int = 2221) { // ARQ-R02
        if (isRunning) return
        isRunning = true
        sessionPassword = generateSessionPassword()
        log.clear()
        log.add(Res.get(R.string.iniciando_servidor_ftp_na_porta_x, port))
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                controlSocket = ServerSocket().apply { bind(InetSocketAddress(NetworkUtils.getWifiOrLocalIpSync(), port)) }
                val ip = getLocalIpFtp()
                log.add(Res.get(R.string.ftp_ativo_em_ftp_x_x, ip, port))
                log.add(Res.get(R.string.user_android_senha_x, sessionPassword))
                while (isActive && isRunning) {
                    val client = controlSocket?.accept() ?: break
                    log.add(Res.get(R.string.conexao_de_x, (client.inetAddress.hostAddress)))
                    launch(Dispatchers.IO) { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning) log.add(Res.get(R.string.erro_ftp_x, (e.message)))
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        try { controlSocket?.close() } catch (_: Exception) {}
        log.add(Res.get(R.string.servidor_ftp_parado))
    }

    private fun isWithinBaseDir(file: File): Boolean {
        return try {
            val canonicalBase = baseDir.canonicalFile
            val canonicalTarget = file.canonicalFile
            var parent: File? = canonicalTarget
            while (parent != null) {
                if (parent == canonicalBase) return true
                parent = parent.parentFile
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun getLocalIpFtp(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress ?: "127.0.0.1"
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun handleClient(socket: Socket) {
        var pasvServer: ServerSocket? = null
        try {
            socket.soTimeout = 60000
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val writer = OutputStreamWriter(socket.outputStream)
            var currentDir = baseDir
            var authenticated = false
            var user = "anonymous"
            val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)

            fun send(msg: String) { writer.write("$msg\r\n"); writer.flush() }

            fun getPasvDataSocket(): Socket? {
                return try { pasvServer?.accept() } catch (_: Exception) { null }
            }

            fun sendOverData(data: String, out: OutputStream) {
                out.write(data.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            send("220 VYKnifeSwiss FTP Server ready")

            loop@ while (true) {
                val line = reader.readLine() ?: break
                val cmd = line.trim()
                val upper = cmd.uppercase()
                log.add("> $cmd")

                when {
                    upper.startsWith("USER ") -> {
                        user = cmd.substring(5).trim()
                        send("331 Password required for $user")
                    }
                    upper.startsWith("PASS ") -> {
                        val provided = cmd.substring(5).trim()
                        authenticated = provided.isNotEmpty() && constantTimeEquals(provided, sessionPassword) // ARQ-R03
                        if (authenticated) send("230 User $user logged in")
                        else send("530 Login or password incorrect")
                    }
                    !authenticated -> send("530 Please login with USER and PASS") // ARQ-R03
                    upper == "SYST" -> send("215 UNIX Type: L8")
                    upper == "FEAT" -> {
                        send("211-Features:")
                        send(" SIZE")
                        send(" PASV")
                        send("211 End")
                    }
                    upper.startsWith("TYPE") -> send("200 Type set OK")
                    upper == "PWD" || upper == "XPWD" -> {
                        send("257 \"${currentDir.absolutePath}\" is current directory")
                    }
                    upper.startsWith("CWD ") -> {
                        val path = cmd.substring(4).trim()
                        val target = if (path.startsWith("/")) File(baseDir, path.removePrefix("/")) else File(currentDir, path)
                        if (isWithinBaseDir(target) && target.exists() && target.isDirectory && target.canRead()) {
                            currentDir = target
                            send("250 CWD successful. \"${currentDir.absolutePath}\"")
                        } else {
                            send("550 Access denied or no such directory")
                        }
                    }
                    upper == "CDUP" -> {
                        val parent = currentDir.parentFile
                        if (parent != null && isWithinBaseDir(parent)) {
                            currentDir = parent
                            send("200 CDUP command successful")
                        } else {
                            send("550 Already at root directory")
                        }
                    }
                    upper == "PASV" -> {
                        try { pasvServer?.close() } catch (_: Exception) {}
                        pasvServer = ServerSocket(0)
                        val pasvPort = pasvServer.localPort
                        val ip = getLocalIpFtp().replace('.', ',')
                        val p1 = pasvPort / 256
                        val p2 = pasvPort % 256
                        send("227 Entering Passive Mode ($ip,$p1,$p2)")
                    }
                    upper.startsWith("LIST") || upper.startsWith("NLST") -> {
                        val dataSocket = getPasvDataSocket()
                        if (dataSocket == null) {
                            send("425 Can't open data connection")
                        } else {
                            send("150 Opening ASCII data connection for directory listing")
                            val listing = buildString {
                                val files = currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
                                for (f in files) {
                                    val perms = if (f.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
                                    val size = f.length()
                                    val date = dateFormat.format(Date(f.lastModified()))
                                    append("$perms 1 $user $user $size $date ${f.name}\r\n")
                                }
                            }
                            dataSocket.use { sendOverData(listing, it.outputStream) }
                            send("226 Transfer complete")
                        }
                    }
                    upper.startsWith("RETR ") -> {
                        val name = cmd.substring(5).trim()
                        val file = if (name.startsWith("/")) File(baseDir, name.removePrefix("/")) else File(currentDir, name)
                        if (!isWithinBaseDir(file) || !file.exists() || !file.isFile) {
                            send("550 Access denied or file not found")
                        } else {
                            val dataSocket = getPasvDataSocket()
                            if (dataSocket == null) {
                                send("425 Can't open data connection")
                            } else {
                                send("150 Opening BINARY mode data connection for ${file.name} (${file.length()} bytes)")
                                dataSocket.use {
                                    FileInputStream(file).use { fis -> fis.copyTo(it.outputStream) }
                                }
                                send("226 Transfer complete")
                                log.add(Res.get(R.string.sent_x_x_bytes, (file.name), (file.length())))
                            }
                        }
                    }
                    upper.startsWith("STOR ") -> {
                        val name = cmd.substring(5).trim()
                        val file = File(currentDir, name)
                        if (!isWithinBaseDir(file)) {
                            send("550 Access denied: Out of directory bounds")
                        } else {
                            val dataSocket = getPasvDataSocket()
                            if (dataSocket == null) {
                                send("425 Can't open data connection")
                            } else {
                                send(Res.get(R.string.n_125_data_connection_already_open_transfe))
                                dataSocket.use {
                                    file.outputStream().use { fos -> it.inputStream.copyTo(fos) }
                                }
                                send("226 Transfer complete")
                                log.add(Res.get(R.string.received_x, (file.name)))
                            }
                        }
                    }
                    upper.startsWith("SIZE ") -> {
                        val name = cmd.substring(5).trim()
                        val file = if (name.startsWith("/")) File(baseDir, name.removePrefix("/")) else File(currentDir, name)
                        if (isWithinBaseDir(file) && file.exists() && file.isFile) send("213 ${file.length()}")
                        else send("550 File not found or access denied")
                    }
                    upper.startsWith("MKD ") || upper.startsWith("XMKD ") -> {
                        val name = cmd.substringAfter(" ").trim()
                        val newDir = File(currentDir, name)
                        if (isWithinBaseDir(newDir) && newDir.mkdir()) send("257 \"${newDir.absolutePath}\" created")
                        else send("550 Create directory operation failed or access denied")
                    }
                    upper.startsWith("DELE ") -> {
                        val name = cmd.substring(5).trim()
                        val file = File(currentDir, name)
                        if (isWithinBaseDir(file) && file.delete()) send("250 DELE command successful")
                        else send("550 Delete operation failed or access denied")
                    }
                    upper == "NOOP" -> send("200 NOOP ok")
                    upper == "QUIT" -> { send("221 Goodbye"); break@loop }
                    else -> send("502 Command not implemented: ${cmd.substringBefore(" ")}")
                }
            }
        } catch (e: Exception) {
            log.add(Res.get(R.string.erro_cliente_x, (e.message)))
        } finally {
            try { pasvServer?.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }
}

