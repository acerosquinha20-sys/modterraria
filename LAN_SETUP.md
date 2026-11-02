# Configuração WiFi (sem editar IP)

1. Instale o APK no celular. Celular e PC com o Terraria precisam estar na **mesma rede WiFi**.
2. Copie a pasta `tModLoader/TerrariaEspBridge` para `ModSources/TerrariaEspBridge` e faça Build + Reload no tModLoader.
3. No app: **Permitir sobreposição** e depois **Iniciar ESP WiFi**.
4. Entre no seu mundo. O app avisa o IP dele por broadcast (UDP 47832) e o mod começa a enviar jogadores/NPCs (UDP 47831). No log do tModLoader aparece "app encontrado em ...".

Se não aparecer nada:
- Libere UDP 47831 e 47832 no firewall do PC.
- Alguns roteadores/WiFi de visitantes bloqueiam broadcast ("isolamento de cliente"). Nesse caso, ponha o IP do celular em `ReceiverHost` no `TerrariaEspBridge.cs`.

O app não lê a memória do jogo nem o tráfego de rede dele; só recebe o que o mod envia.
