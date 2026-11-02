# Terraria ESP (overlay flutuante)

<p align="center"><img src="icon.png" width="140"></p>

App Android com **botão flutuante pequeno** que abre um mini-menu com:

- Linha ESP
- Box ESP
- Vida (experimental)
- Distância (em blocos aproximados)
- Filtros: Bichos e Humanos (NPCs)
- Ajuste de tamanho do bloco (− / +)

Feito para o **Terraria offline (single player)**.

## Como funciona
O app usa captura de tela (MediaProjection) e detecta **o que se move** comparando frames.
Não lê memória nem modifica o jogo.

## Como filtra (v1.1)
- Compensa o movimento da câmera, então andar não gera alvo no cenário todo.
- Ignora o próprio overlay (cores magenta/ciano) e o próprio personagem no centro.
- Junta partes próximas, descarta alvos grandes demais, finos demais ou esparsos.
- Só mostra alvos confirmados por vários frames.
- **Calibre o botão "Bloco" (− / +)** até os NPCs aparecerem como *Humano*.

## Limitações (importante)
- Só vê o que está na tela.
- Bicho / Humano é separado por **tamanho e posição**, não por identificação real.
- Vida só aparece se o jogo estiver mostrando a barra de vida do inimigo.
- Para dados 100% precisos (tipo, vida real, fora da tela), o caminho é um mod tModLoader.

## Gerar o APK pelo GitHub
1. Crie um repositório e envie todos estes arquivos.
2. Aba **Actions** > **Build APK** (roda sozinho a cada push).
3. Ao terminar, baixe o artefato **TerrariaESP-apk** (`app-debug.apk`).

## Usar
1. Instale o APK, abra o app e toque **Permitir sobreposição**.
2. Toque **Iniciar ESP** e aceite a captura de tela.
3. Abra o Terraria. Toque no botão flutuante para abrir/fechar o menu; arraste para mover.
