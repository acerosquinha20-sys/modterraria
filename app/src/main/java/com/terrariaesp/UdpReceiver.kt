package com.terrariaesp

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Troca de informacoes com o mod tModLoader (TerrariaEspBridge), protocolo v2:
 *  - app -> mod: "TESP-HELLO" em broadcast (porta 47832) a cada 1s; o mod descobre o IP do celular.
 *  - mod -> app: jogadores/NPCs na porta 47831 (coordenadas normalizadas + numero de sequencia).
 * Pacotes velhos/duplicados sao descartados e a velocidade de cada alvo e calculada para o
 * EspView prever a posicao entre pacotes (movimento fluido, sem atraso).
 */
class UdpReceiver(private val port: Int = 47831, private val helloPort: Int = 47832) {
    @Volatile var snapshot: NetSnapshot? = null
        private set
    @Volatile var lastPacketAt: Long = 0L
        private set
    @Volatile var sender: String = "-"
        private set
    @Volatile var pps: Int = 0
        private set

    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    // estado usado so pela thread de recepcao
    private var lastSeq = -1
    private var prevMap = HashMap<Int, NetTarget>()
    private var prevAt = 0L
    private var ppsCount = 0
    private var ppsAt = 0L

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread {
            try {
                val s = DatagramSocket(null as java.net.SocketAddress?)
                s.reuseAddress = true
                s.bind(java.net.InetSocketAddress(port))
                s.broadcast = true
                s.soTimeout = 300
                socket = s
                val hello = "TESP-HELLO".toByteArray(Charsets.UTF_8)
                val bcast = InetAddress.getByName("255.255.255.255")
                val buf = ByteArray(4096)
                var lastHello = 0L
                while (running.get()) {
                    val now = System.currentTimeMillis()
                    if (now - lastHello >= 1000L) {
                        lastHello = now
                        try {
                            s.send(DatagramPacket(hello, hello.size, bcast, helloPort))
                        } catch (e: java.io.IOException) { }
                    }
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        val nowWall = System.currentTimeMillis()
                        // mod reiniciado: a sequencia recomeca do zero
                        if (lastPacketAt != 0L && nowWall - lastPacketAt > 1500L) lastSeq = -1
                        val data = p.data.copyOfRange(p.offset, p.offset + p.length)
                        val snap = decode(data, System.nanoTime() / 1_000_000L)
                        if (snap != null) {
                            lastSeq = snap.seq
                            snapshot = snap
                            lastPacketAt = nowWall
                            sender = p.address.hostAddress ?: "-"
                            ppsCount++
                            if (nowWall - ppsAt >= 1000L) {
                                pps = ppsCount
                                ppsCount = 0
                                ppsAt = nowWall
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) { }
                }
            } catch (e: Exception) {
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
        snapshot = null
    }

    private fun decode(b: ByteArray, nowMs: Long): NetSnapshot? {
        if (b.size < 13) return null
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.int != 0x54535045) return null // "EPST"
        if ((bb.get().toInt() and 255) != 2) return null
        val seq = bb.short.toInt() and 0xFFFF
        val gameW = bb.short.toInt() and 0xFFFF
        val gameH = bb.short.toInt() and 0xFFFF
        val count = bb.short.toInt() and 0xFFFF
        if (count > 512 || gameW == 0 || gameH == 0) return null

        // descarta pacote duplicado ou que chegou atrasado
        if (lastSeq >= 0) {
            val diff = (seq - lastSeq) and 0xFFFF
            if (diff == 0 || diff > 0x8000) return null
        }

        val dt = nowMs - prevAt
        val useVel = prevAt != 0L && dt in 1L..100L
        val out = ArrayList<NetTarget>(count)
        val newMap = HashMap<Int, NetTarget>()
        var i = 0
        while (i < count) {
            i++
            if (bb.remaining() < 30) break
            val kind = bb.get().toInt() and 255
            val id = bb.short.toInt() and 0xFFFF
            val x = bb.float
            val y = bb.float
            val w = bb.float
            val h = bb.float
            val life = bb.int
            val maxLife = bb.int
            val dist = bb.short.toInt() and 0xFFFF
            val len = bb.get().toInt() and 255
            if (len > bb.remaining()) break
            val nameBytes = ByteArray(len)
            bb.get(nameBytes)
            val name = nameBytes.toString(Charsets.UTF_8)
            val key = (kind shl 16) or id
            var vx = 0f
            var vy = 0f
            val pv = prevMap[key]
            if (useVel && pv != null) {
                vx = (x - pv.x) / dt.toFloat()
                vy = (y - pv.y) / dt.toFloat()
            }
            val t = NetTarget(kind, id, x, y, w, h, life, maxLife, dist, name, vx, vy)
            out.add(t)
            newMap[key] = t
        }
        prevMap = newMap
        prevAt = nowMs
        return NetSnapshot(nowMs, seq, out)
    }
}
