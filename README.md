# Terraria ESP (overlay flutuante)

<p align="center"><img src="icon.png" width="140"></p>

App Android com **botão flutuante pequeno** que marca **só personagens humanoides em movimento** (NPCs/inimigos de forma humana) no Terraria offline:

- Linha ESP: do **topo da tela** até o alvo
- Box ESP
- Vida (experimental)
- Distância (em blocos aproximados)
- Botões de ajuste: **Bloco** (tamanho do bloco) e **Filtro** (sensibilidade)

## Como funciona
O app usa captura de tela (MediaProjection) e detecta **o que se move** comparando frames.
Não lê memória nem modifica o jogo.

## Como filtra (v1.2)
- Compensa o movimento da câmera.
- Ignora o próprio overlay (magenta) e o seu personagem no centro.
- Junta partes próximas e mantém só formas de humanoide (~3 blocos de altura, mais alto que largo).
- Só mostra alvos que duraram alguns frames e realmente se deslocaram.
- Calibre **Bloco** até a caixa ficar certa e use **Filtro +** se marcar coisas demais.

## Limitações (importante)
- Só vê o que está na tela.
- "Humano" é decidido pela **forma e tamanho**, não por identificação real.
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
