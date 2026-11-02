# Terraria ESP Bridge

Client-side tModLoader 1.4.4 mod. It reads Terraria's public in-process entity state through the normal mod API and sends visible Players/NPCs as UDP packets to the configured Android phone.

1. Put this folder under `ModSources/TerrariaEspBridge`.
2. (Opcional) `ReceiverHost` fica vazio para descobrir o app sozinho.
3. Build/Reload the mod in tModLoader.
4. On Android, allow overlay and press `Iniciar ESP WiFi`.
5. Open the Terraria world.

Portas: UDP 47831 (mod -> app) e 47832 (app -> mod, descoberta).
