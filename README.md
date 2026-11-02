# Terraria ESP v5 — WiFi/tModLoader

Projeto atualizado com dois modos:

1. **WiFi/tModLoader (principal):** um mod local do tModLoader coleta apenas entidades visíveis na câmera do jogador e envia por UDP para o app Android. Não lê memória, não intercepta tráfego do jogo e não altera o cliente.
2. **Captura local:** o detector antigo por MediaProjection continua disponível como fallback.

## Estrutura

- `app/` — APK Android/overlay.
- `tModLoader/TerrariaEspBridge/` — mod C# para tModLoader 1.4.4.

## WiFi

O app escuta UDP na porta **47831**. No arquivo `TerrariaEspBridge.cs`, defina `ReceiverHost` como o IP do telefone na mesma rede WiFi. O mod envia caixas já convertidas para coordenadas de tela, então o app só desenha.

O pacote usa um protocolo binário simples: magic `0x54535045`, versão 1, quantidade e registros de Player/NPC.

### tModLoader

A árvore do mod é compatível com a linha 1.4.4. O tModLoader continua mantendo a linha 1.4.4 enquanto 1.4.5 passa por mudanças de API. citeturn199512search0turn199512search1

Para usar: abra o mod em `Mod Sources` no tModLoader, altere `ReceiverHost`, compile/reload e entre no seu mundo.

## Android

Abra o app → permita sobreposição → **Iniciar ESP WiFi**. Não é necessário conceder captura de tela para o modo WiFi.

### GitHub Actions

O workflow existente continua gerando `app-debug.apk`.
