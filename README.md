# GlassLauncher

GlassLauncher — Projeto de launcher com código-fonte aberto, disponibilizado publicamente para a comunidade.

Launcher Android com visual Liquid Glass, modo desktop (para DeX / telas grandes) e muita personalizacao.

## Gerar o APK

**Opcao A - Android Studio (PC)**
1. Abra a pasta do projeto no Android Studio e aguarde o Sync.
2. Menu Build > Build APK(s). O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.

**Opcao B - Pelo celular, sem PC (GitHub Actions)**
1. Abra a aba Actions do repositorio.
2. Execute o workflow "Build APK" usando Run workflow.
3. Quando terminar, baixe o artefato `GlassLauncher-debug-apk` e instale o `app-debug.apk`.

## Usar

Instale, aperte Home e escolha "Glass Launcher" > Sempre. Ajustes: engrenagem (cartao do relogio ou barra de tarefas).

**Celular:** relogio em vidro, busca, grade e dock. Segure um app para fixar no dock, na area de trabalho, ver informacoes ou desinstalar.

**Modo desktop:** area de trabalho com atalhos, barra de tarefas (botao Iniciar, apps fixados, relogio), menu Iniciar com busca, apps abrindo em janelas, clique direito e hover com mouse, ESC fecha o menu.
Liga sozinho no DeX / telas grandes. Para testar no proprio celular: Ajustes > Modo desktop / DeX > Ativacao > Sempre.

Observacao: o desktop proprio da Samsung (DeX) continua sendo da Samsung; este modo aparece quando o Glass Launcher e quem esta sendo exibido.
