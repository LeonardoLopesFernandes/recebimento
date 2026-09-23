# Changelog

Todas as mudanças notáveis do Recebimento. O topo deste arquivo é publicado
automaticamente como nota (notes) no release/patch do Shorebird.

## [Não lançado]
- SnackBars de geração/salvamento de Excel/PDF com ícones (arquivo branco + checkbox)
- Header de itens mais compacto (sem seta de voltar, ícones XLS/PDF brancos)
- Logout limpa cookies da WebView (trocar de conta autentica a conta correta)
- Ícone de Excel da toolbar (detalhes da viagem) pintado de branco
- Ícones de Excel e PDF com assets próprios (excel.png/pdf.png) nos botões
- Correção do patch OTA: versões de plugins travadas no lock do release base (native changes) + remoção do assets/fonts inexistente
- Correção do patch OTA: verificação de token por cookie via JavaScript (getCookies removido do webview_flutter)
- Correção do travamento ao escolher foto no perfil (decodificação redimensionada)
- Botões de Excel/PDF com ícones no lugar dos textos (itens e detalhes)
- Ícone "Imagens do Recebimento" atualizado (galeria)
- Excluir pasta de imagens agora apaga também as fotos salvas
- Diálogos com fundo branco
- Busca das Recebidas com debounce e filtro por placa/número/origem
- Campo de busca da viagem com ícone de lupa e hint à esquerda
- Ícones com fundo transparente (logo do caminhão no login/carregamento e menu lateral)
- Tela de perfil aberta pela engrenagem (foto persistente, nome, e-mail, loja, sair da conta)
- Tela de carregando credenciais com ícone pulsando (escala + fade), igual ao Papeleta63
- Integração Shorebird (OTA Code Push)