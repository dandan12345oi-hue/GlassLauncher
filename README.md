# GlassLauncher

Launcher Android de código aberto com visual Liquid Glass, tela inicial personalizável e modo desktop adaptável para telas grandes e Samsung DeX.

## O que funciona

- **Tela inicial do Android:** declara as categorias `HOME` e `DEFAULT` e pode ser escolhido como launcher padrão.
- **Gaveta de aplicativos:** botão de grade no dock, busca por nome, abertura de apps e menu contextual.
- **Atalhos pessoais:** adicione apps à tela inicial a partir da gaveta; remova ou fixe favoritos no dock.
- **Páginas:** deslize horizontalmente para navegar entre páginas de atalhos. As páginas surgem conforme a quantidade de atalhos.
- **Pastas:** segure um ícone e solte sobre outro para agrupá-los. Pastas podem ser abertas, renomeadas, desagrupadas ou removidas.
- **Widgets Android:** use `+ Widget` na tela inicial para escolher entre os provedores instalados. O Android solicita a vinculação quando necessária.
- **Personalização:** papel de parede, tema, cor, Liquid Glass, formato e tamanho dos ícones, dock e ajustes de grade.
- **Modo desktop do GlassLauncher:** área própria com atalhos, menu Iniciar, barra de tarefas, relógio e abertura de apps com limites de janela quando o Android/aplicativo aceita.
- **Mouse e teclado:** menu de contexto, hover e tecla Esc no modo desktop.

## Instalar como tela inicial

1. Instale o APK.
2. Pressione o botão/gesto Home.
3. Escolha **GlassLauncher** como aplicativo de tela inicial padrão.
4. Para trocar depois, abra Ajustes do Android → Aplicativos → Aplicativos padrão → App de início (o nome pode variar conforme o fabricante).

## Gerar o APK

### Pelo GitHub Actions (sem computador)

1. Abra a aba **Actions** deste repositório.
2. Selecione **Build APK** e execute **Run workflow**.
3. Abra a execução concluída e baixe o artefato **GlassLauncher-debug-apk**.
4. Extraia o ZIP e instale `app-debug.apk`.

### Pelo Android Studio

Abra o projeto, aguarde a sincronização do Gradle e execute **Build → Build APK(s)**.

## Limites de integração

O GlassLauncher é um launcher Android selecionável como tela inicial. O modo desktop é uma interface própria exibida pelo GlassLauncher; ele não substitui a interface de sistema do Samsung DeX nem força aplicativos de terceiros a oferecerem janelas redimensionáveis. A compatibilidade de widgets, configuração e abertura em janelas depende das APIs e permissões oferecidas pelo Android e por cada aplicativo.

## Requisitos

- Android 8.0 (API 26) ou superior.
- Para modo desktop automático: Samsung DeX ou tela com largura lógica de pelo menos 1000 dp. Também é possível selecionar o modo manualmente nos ajustes.
