import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

fun queryNetBios(ip: String) {
    try {
        val socket = DatagramSocket()
        socket.soTimeout = 200
        val payload = byteArrayOf(
            0x82.toByte(), 0x28.toByte(), 0x0, 0x0, 0x0, 0x1, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
            0x20, 0x43, 0x4b, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x0, 0x0, 0x21, 0x0, 0x1
        )
        val packet = DatagramPacket(payload, payload.size, InetAddress.getByName(ip), 137)
        socket.send(packet)

        val buffer = ByteArray(1024)
        val receivePacket = DatagramPacket(buffer, buffer.size)
        socket.receive(receivePacket)
        
        val data = receivePacket.data
        val numNames = data[56].toInt() and 0xFF
        val hostnameBytes = data.copyOfRange(57, 57 + 15)
        val hostname = String(hostnameBytes).trim()
        
        val macIndex = 57 + (numNames * 18)
        val macBytes = data.copyOfRange(macIndex, macIndex + 6)
        val mac = macBytes.joinToString(":") { "%02X".format(it) }
        
        println("IP: $ip, Hostname: $hostname, MAC: $mac")
        socket.close()
    } catch (e: Exception) {
        println("IP: $ip Timeout or Error: ${e.message}")
    }
}
fun main() {
    queryNetBios("192.168.7.6") // Assuming gateway or a test device
}
