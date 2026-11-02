# Terraria ESP Bridge

Client-side tModLoader 1.4.4 mod. It reads Terraria's public in-process entity state through the normal mod API and sends visible Players/NPCs as UDP packets to the configured Android phone.

1. Put this folder under `ModSources/TerrariaEspBridge`.
2. Edit `ReceiverHost` in `TerrariaEspBridge.cs` to the phone LAN IP.
3. Build/Reload the mod in tModLoader.
4. On Android, allow overlay and press `Iniciar ESP WiFi`.
5. Open the Terraria world.

Port: UDP 47831.
