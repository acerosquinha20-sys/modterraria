# LAN setup

1. Install the generated APK on the Android phone.
2. Put the phone and the Terraria PC on the same Wi-Fi network.
3. Find the phone's local IPv4 address (Android Wi-Fi details).
4. Edit `tModLoader/TerrariaEspBridge/TerrariaEspBridge.cs` and set `ReceiverHost` to that address.
5. Build/reload `TerrariaEspBridge` in tModLoader.
6. Open the Android app and tap `Permitir sobreposição`, then `Iniciar ESP WiFi`.
7. Enter your Terraria world and the overlay should receive packets on UDP `47831`.

If nothing appears, allow UDP 47831 through the PC firewall and verify both devices are on the same LAN. The bridge sends screen-space entity rectangles; the Android app does not inspect Terraria memory or the game's network traffic.
