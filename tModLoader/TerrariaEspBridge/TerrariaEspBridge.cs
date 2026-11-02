using Microsoft.Xna.Framework;
using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using Terraria;
using Terraria.ModLoader;

namespace TerrariaEspBridge;

/// <summary>
/// Protocolo v2 (UDP, little-endian), mod -> app, porta 47831:
///   magic "EPST" (4) | versao=2 (1) | seq u16 | telaW u16 | telaH u16 | count u16
///   por entidade: kind u8 (0=player, 1=NPC hostil, 2=voce) | id u16 | x,y,w,h f32 (fracao 0..1 da tela do jogo)
///                 | life i32 | maxLife i32 | distancia em blocos u16 | nomeLen u8 | nome utf8
/// Coordenadas normalizadas: funcionam com qualquer resolucao/proporcao do celular.
/// App -> mod, porta 47832: "TESP-HELLO" a cada 1s (o mod descobre o IP do celular sozinho).
/// </summary>
public sealed class TerrariaEspBridge : Mod
{
    // Opcional: IP fixo do celular. Deixe "" para descobrir sozinho.
    public const string ReceiverHost = "";
    public const int ReceiverPort = 47831;
    public const int HelloPort = 47832;

    private const int MaxPacket = 1400;      // cabe em 1 pacote WiFi, sem fragmentacao
    private const int MaxName = 16;

    private UdpClient? udp;
    private UdpClient? listener;
    private Thread? thread;
    private volatile bool running;
    private volatile IPEndPoint? receiver;

    private readonly byte[] buf = new byte[MaxPacket];
    private readonly List<Ent> ents = new List<Ent>(128);
    private readonly Dictionary<int, byte[]> npcNames = new Dictionary<int, byte[]>();
    private readonly string?[] playerNameCache = new string?[256];
    private readonly byte[]?[] playerNameBytes = new byte[256][];
    private ushort seq;
    private uint lastSentTick;
    private int lastCount;

    private struct Ent
    {
        public byte Kind;
        public int Id;
        public float X, Y, W, H;
        public int Life, MaxLife;
        public int Dist;
        public byte[]? Name;
    }

    public override void Load()
    {
        if (Main.dedServ)
            return;
        try
        {
            if (ReceiverHost.Length > 0)
                receiver = new IPEndPoint(IPAddress.Parse(ReceiverHost), ReceiverPort);
            udp = new UdpClient();
            running = true;
            thread = new Thread(HelloLoop) { IsBackground = true, Name = "TerrariaEspBridge-UDP" };
            thread.Start();
            Logger.Info("Terraria ESP Bridge: aguardando o app (UDP " + HelloPort + ")");
        }
        catch (Exception ex)
        {
            Logger.Error("Could not start UDP bridge: " + ex);
        }
    }

    public override void Unload()
    {
        running = false;
        try { udp?.Close(); } catch { }
        try { listener?.Close(); } catch { }
        udp = null;
        listener = null;
        thread = null;
        receiver = null;
    }

    // Escuta o "TESP-HELLO" do app e passa a enviar os dados para o IP de quem mandou.
    private void HelloLoop()
    {
        try
        {
            listener = new UdpClient(AddressFamily.InterNetwork);
            listener.ExclusiveAddressUse = false;
            listener.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            listener.Client.Bind(new IPEndPoint(IPAddress.Any, HelloPort));
            while (running)
            {
                var from = new IPEndPoint(IPAddress.Any, 0);
                byte[] data = listener.Receive(ref from);
                if (Encoding.UTF8.GetString(data) != "TESP-HELLO")
                    continue;
                var ep = new IPEndPoint(from.Address, ReceiverPort);
                var cur = receiver;
                if (cur == null || !cur.Equals(ep))
                {
                    receiver = ep;
                    Logger.Info("Terraria ESP Bridge: app encontrado em " + ep);
                }
            }
        }
        catch (Exception ex)
        {
            if (running)
                Logger.Warn("ESP bridge listener stopped: " + ex.Message);
        }
    }

    public override void PostUpdateEverything()
    {
        if (Main.dedServ || !running || udp == null)
            return;
        IPEndPoint? target = receiver;
        if (target == null)
            return;
        if (Main.gameMenu || Main.LocalPlayer == null || !Main.LocalPlayer.active)
            return;

        try
        {
            Collect();

            // Sem entidades: manda 1 pacote vazio ao esvaziar e depois so um "batimento" 4x/s.
            uint tick = Main.GameUpdateCount;
            if (ents.Count == 0 && lastCount == 0 && tick - lastSentTick < 15u)
                return;
            lastSentTick = tick;
            lastCount = ents.Count;

            int len = Encode();
            udp.Send(buf, len, target);
        }
        catch (Exception ex)
        {
            Logger.Warn("ESP bridge send failed: " + ex.Message);
        }
    }

    private void Collect()
    {
        ents.Clear();
        float sw = Math.Max(1, Main.screenWidth);
        float sh = Math.Max(1, Main.screenHeight);
        Vector2 camera = Main.screenPosition;
        Vector2 me = Main.LocalPlayer.Center;
        Rectangle screen = new Rectangle(0, 0, Main.screenWidth, Main.screenHeight);

        for (int i = 0; i < Main.maxPlayers && i < 256; i++)
        {
            Player p = Main.player[i];
            if (!p.active || p.dead)
                continue;
            Vector2 sp = p.position - camera;
            Rectangle sr = new Rectangle((int)sp.X, (int)sp.Y, p.width, p.height);
            if (!screen.Intersects(sr))
                continue;
            if (playerNameCache[i] != p.name)
            {
                playerNameCache[i] = p.name;
                playerNameBytes[i] = EncodeName(p.name);
            }
            ents.Add(new Ent
            {
                Kind = (byte)(i == Main.myPlayer ? 2 : 0),
                Id = i,
                X = sp.X / sw, Y = sp.Y / sh, W = p.width / sw, H = p.height / sh,
                Life = p.statLife, MaxLife = p.statLifeMax2,
                Dist = (int)(Vector2.Distance(p.Center, me) / 16f),
                Name = playerNameBytes[i]
            });
        }

        for (int i = 0; i < Main.maxNPCs; i++)
        {
            NPC n = Main.npc[i];
            if (!n.active || n.friendly || n.lifeMax <= 0)
                continue;
            Vector2 sp = n.position - camera;
            Rectangle sr = new Rectangle((int)sp.X, (int)sp.Y, n.width, n.height);
            if (!screen.Intersects(sr))
                continue;
            if (!npcNames.TryGetValue(n.type, out byte[]? name))
            {
                name = EncodeName(n.FullName);
                npcNames[n.type] = name;
            }
            ents.Add(new Ent
            {
                Kind = 1,
                Id = i,
                X = sp.X / sw, Y = sp.Y / sh, W = n.width / sw, H = n.height / sh,
                Life = n.life, MaxLife = n.lifeMax,
                Dist = (int)(Vector2.Distance(n.Center, me) / 16f),
                Name = name
            });
        }

        // Se nao couber tudo em 1 pacote, os mais proximos tem prioridade.
        ents.Sort(CompareDist);
    }

    private static int CompareDist(Ent a, Ent b) => a.Dist.CompareTo(b.Dist);

    private static byte[] EncodeName(string? s)
    {
        if (string.IsNullOrEmpty(s))
            return Array.Empty<byte>();
        if (s.Length > MaxName)
            s = s.Substring(0, MaxName);
        return Encoding.UTF8.GetBytes(s);
    }

    private int Encode()
    {
        byte[] b = buf;
        int o = 0;
        PutI32(b, ref o, 0x54535045);
        b[o++] = 2;
        PutU16(b, ref o, seq++);
        PutU16(b, ref o, Main.screenWidth);
        PutU16(b, ref o, Main.screenHeight);
        int countPos = o;
        PutU16(b, ref o, 0);

        int written = 0;
        for (int i = 0; i < ents.Count; i++)
        {
            Ent e = ents[i];
            byte[] name = e.Name ?? Array.Empty<byte>();
            if (o + 30 + name.Length > MaxPacket)
                break;
            b[o++] = e.Kind;
            PutU16(b, ref o, e.Id);
            PutF32(b, ref o, e.X);
            PutF32(b, ref o, e.Y);
            PutF32(b, ref o, e.W);
            PutF32(b, ref o, e.H);
            PutI32(b, ref o, e.Life);
            PutI32(b, ref o, e.MaxLife);
            PutU16(b, ref o, Math.Min(e.Dist, 65535));
            b[o++] = (byte)name.Length;
            Buffer.BlockCopy(name, 0, b, o, name.Length);
            o += name.Length;
            written++;
        }
        b[countPos] = (byte)written;
        b[countPos + 1] = (byte)(written >> 8);
        return o;
    }

    private static void PutU16(byte[] b, ref int o, int v)
    {
        b[o++] = (byte)v;
        b[o++] = (byte)(v >> 8);
    }

    private static void PutI32(byte[] b, ref int o, int v)
    {
        b[o++] = (byte)v;
        b[o++] = (byte)(v >> 8);
        b[o++] = (byte)(v >> 16);
        b[o++] = (byte)(v >> 24);
    }

    private static void PutF32(byte[] b, ref int o, float f)
    {
        PutI32(b, ref o, BitConverter.SingleToInt32Bits(f));
    }
}
