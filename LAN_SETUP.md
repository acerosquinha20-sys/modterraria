# Configuração WiFi (protocolo v2)

1. Instale o APK no celular. Celular e PC com o Terraria na **mesma rede WiFi**.
2. Copie `tModLoader/TerrariaEspBridge` para `ModSources/TerrariaEspBridge` e faça Build + Reload no tModLoader.
3. No app: **Permitir sobreposição** e **Iniciar ESP WiFi**. No menu flutuante a linha de status mostra "Mod conectado - N pacotes/s".
4. Entre no seu mundo. No log do tModLoader aparece "app encontrado em ...".

Como funciona:
- App -> mod (UDP 47832): "TESP-HELLO" a cada 1 s. O mod descobre o IP do celular sozinho.
- Mod -> app (UDP 47831): até ~60 pacotes/s com jogadores/NPCs na tela. Cada pacote tem número de sequência (atrasados/duplicados são descartados), posições em fração da tela (funciona em qualquer resolução) e distância em blocos calculada pelo jogo.
- O app prevê a posição entre pacotes (até 40 ms) para o movimento ficar fluido.
- Sem nada na tela o mod só manda um batimento 4x/s.

Se não conectar: libere UDP 47831 e 47832 no firewall do PC. Se o roteador bloquear broadcast, ponha o IP do celular em `ReceiverHost` no `TerrariaEspBridge.cs`.

Limite conhecido: se você usar zoom do jogo diferente de 100%, as caixas podem ficar um pouco deslocadas.
