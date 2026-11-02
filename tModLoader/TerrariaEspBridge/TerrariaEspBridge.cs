using Microsoft.Xna.Framework;
using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using Terraria;
using Terraria.ModLoader;

namespace TerrariaEspBridge;

public sealed class TerrariaEspBridge : Mod
{
    public static TerrariaEspBridge Instance = null!;
    private UdpClient? udp;
    private Thread? thread;
    private volatile bool running;
    private IPEndPoint? receiver;

    // Set this to the Android phone's LAN IP. Both devices must be on the same WiFi.
    public const string ReceiverHost = "192.168.1.100";
    public const int ReceiverPort = 47831;

    public override void Load()
    {
        Instance = this;
        if (Main.dedServ)
            return;
        try
        {
            receiver = new IPEndPoint(IPAddress.Parse(ReceiverHost), ReceiverPort);
            udp = new UdpClient();
            running = true;
            thread = new Thread(ReceiveLoop) { IsBackground = true, Name = "TerrariaEspBridge-UDP" };
            thread.Start();
            Logger.Info($"Terraria ESP Bridge: UDP receiver {ReceiverHost}:{ReceiverPort}");
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
        udp = null;
        thread = null;
        receiver = null;
        Instance = null!;
    }

    private void ReceiveLoop()
    {
        // The Android app does not need to be contacted first; this socket only keeps
        // the bridge independent from Terraria's game-network protocol.
        while (running)
            Thread.Sleep(250);
    }

    public override void PostUpdateEverything()
    {
        if (Main.dedServ || !running || udp == null || receiver == null)
            return;
        if (Main.gameMenu || Main.LocalPlayer == null || !Main.LocalPlayer.active)
            return;
        if ((Main.GameUpdateCount % 3) != 0)
            return;

        try
        {
            var list = new List<EntityRecord>(64);
            Vector2 camera = Main.screenPosition;
            Rectangle screen = new(0, 0, Main.screenWidth, Main.screenHeight);

            // Players visible to this client.
            for (int i = 0; i < Main.maxPlayers; i++)
            {
                Player p = Main.player[i];
                if (!p.active || p.dead)
                    continue;
                Rectangle hit = p.Hitbox;
                Vector2 screenPos = p.position - camera;
                Rectangle sr = new((int)screenPos.X, (int)screenPos.Y, hit.Width, hit.Height);
                if (screen.Intersects(sr))
                    list.Add(new EntityRecord(0, i, screenPos.X, screenPos.Y, hit.Width, hit.Height, p.statLife, p.statLifeMax2, p.name));
            }

            // NPCs visible to this client.
            for (int i = 0; i < Main.maxNPCs; i++)
            {
                NPC n = Main.npc[i];
                if (!n.active || n.friendly || n.lifeMax <= 0)
                    continue;
                Vector2 screenPos = n.position - camera;
                Rectangle sr = new((int)screenPos.X, (int)screenPos.Y, n.width, n.height);
                if (screen.Intersects(sr))
                    list.Add(new EntityRecord(1, i, screenPos.X, screenPos.Y, n.width, n.height, n.life, n.lifeMax, n.FullName));
            }

            Send(list);
        }
        catch (Exception ex)
        {
            Logger.Warn("ESP bridge send failed: " + ex.Message);
        }
    }

    private void Send(List<EntityRecord> entities)
    {
        if (udp == null || receiver == null)
            return;
        using var ms = new MemoryStream(2048);
        using var bw = new BinaryWriter(ms, Encoding.UTF8, true);
        bw.Write(0x54535045u); // Android reads little-endian: 45 50 53 54
        bw.Write((byte)1);
        bw.Write((ushort)Math.Min(entities.Count, 512));
        int count = Math.Min(entities.Count, 512);
        for (int i = 0; i < count; i++)
        {
            var e = entities[i];
            bw.Write((byte)e.Kind);
            bw.Write((ushort)e.Id);
            bw.Write(e.X);
            bw.Write(e.Y);
            bw.Write(e.W);
            bw.Write(e.H);
            bw.Write(e.Life);
            bw.Write(e.MaxLife);
            byte[] name = Encoding.UTF8.GetBytes(e.Name ?? "");
            int len = Math.Min(name.Length, 255);
            bw.Write((byte)len);
            bw.Write(name, 0, len);
        }
        bw.Flush();
        byte[] data = ms.ToArray();
        udp.Send(data, data.Length, receiver);
    }

    private sealed class EntityRecord
    {
        public int Kind, Id;
        public float X, Y, W, H;
        public int Life, MaxLife;
        public string Name;
        public EntityRecord(int kind, int id, float x, float y, float w, float h, int life, int maxLife, string name)
        {
            Kind = kind; Id = id; X = x; Y = y; W = w; H = h; Life = life; MaxLife = maxLife; Name = name;
        }
    }
}
