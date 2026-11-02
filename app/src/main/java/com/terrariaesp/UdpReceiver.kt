package com.terrariaesp

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class UdpReceiver(private val port: Int = 47831) {
    @Volatile var targets: List<NetTarget> = emptyList()
        private set
    @Volatile var lastPacketAt: Long = 0L
        private set
    @Volatile var sender: String = "-"
        private set

    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread {
            try {
                socket = DatagramSocket(port, InetAddress.getByName("0.0.0.0"))
                socket!!.soTimeout = 1500
                val buf = ByteArray(65507)
                while (running.get()) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        socket!!.receive(p)
                        val data = p.data.copyOfRange(p.offset, p.offset + p.length)
                        val decoded = decode(data)
                        if (decoded != null) {
                            targets = decoded
                            lastPacketAt = System.currentTimeMillis()
                            sender = p.address.hostAddress ?: "-"
                        }
                    } catch (_: java.net.SocketTimeoutException) { }
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
                socket = null
            }
        }.also { it.name = "terraria-udp"; it.isDaemon = true; it.start() }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        thread?.interrupt()
        thread = null
        targets = emptyList()
    }

    private fun decode(b: ByteArray): List<NetTarget>? {
        if (b.size < 7) return null
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.int != 0x54535045) return null // TESP magic
        val version = bb.get().toInt() and 255
        if (version != 1) return null
        val count = bb.short.toInt() and 65535
        if (count > 512) return null
        val out = ArrayList<NetTarget>(count)
        repeat(count) {
            if (bb.remaining() < 30) return@repeat
            val kind = bb.get().toInt() and 255
            val id = bb.short.toInt() and 65535
            val x = bb.float
            val y = bb.float
            val w = bb.float
            val h = bb.float
            val life = bb.int
            val maxLife = bb.int
            val len = bb.get().toInt() and 255
            if (len > bb.remaining()) return@repeat
            val nameBytes = ByteArray(len)
            bb.get(nameBytes)
            val name = nameBytes.toString(Charsets.UTF_8)
            out.add(NetTarget(kind, id, x, y, w, h, life, maxLife, name))
        }
        return out
    }
}
