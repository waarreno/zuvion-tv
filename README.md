<p align="center">
  <img src="docs/screenshots/tv-inicio.png" alt="Zuvion TV na TV: tela inicial" width="100%">
</p>

<h1 align="center">Zuvion TV</h1>
<p align="center"><b>Sua TV, seu universo.</b><br>
Player IPTV gratuito para <b>celular</b> e <b>Android TV / Google TV</b>, com visual de vidro líquido.</p>

<p align="center">
  <a href="https://github.com/waarreno/zuvion-tv/releases/latest"><b>⬇ Baixar a versão mais recente</b></a>
</p>

> O Zuvion TV é só um player: **não fornece canais, filmes nem listas**. Para assistir, use uma lista IPTV contratada com o seu provedor.

## Telas

| TV · Guia de programação | TV · Player |
|---|---|
| ![Guia](docs/screenshots/tv-guia.png) | ![Player](docs/screenshots/tv-player.png) |

| Celular · Entrar | Celular · Início | TV · Aviso de atualização |
|---|---|---|
| <img src="docs/screenshots/celular-login.png" width="260"> | <img src="docs/screenshots/celular-inicio.png" width="260"> | <img src="docs/screenshots/tv-atualizacao.png" width="420"> |

*Imagens do protótipo de design, com conteúdo de exemplo.*

## O que ele faz

- **Funciona com a sua lista:** Xtream Codes, link M3U/M3U8, arquivo M3U do aparelho e portal MAC (Stalker).
- **TV ao vivo, filmes e séries:** categorias, busca, favoritos e "continuar assistindo".
- **Guia de programação (EPG)** em grade, com **replay** (assistir do início) quando o provedor oferece.
- **Capas e sinopses do TMDB**, com notas do IMDb e do Rotten Tomatoes pelo OMDb (opcional).
- **Pular abertura** em séries e filmes, pelo TheIntroDB.
- **Perfis**, perfil infantil e **PIN** para conteúdo adulto.
- **Multi-tela:** até 4 canais ao mesmo tempo.
- **Gravação** do programa ao vivo.
- **Tema claro ou escuro.**
- **Digitar pelo celular:** na TV, um QR Code abre uma página no celular para digitar o login ou a busca, sem usar o controle.
- **Abre rápido:** a lista fica salva no aparelho e é atualizada a cada 24 horas, ou quando você quiser.
- **Atualizações automáticas:** o app avisa quando sai uma versão nova e só instala com a sua confirmação.

## Como instalar

### No celular

1. Abra a página de [versões](https://github.com/waarreno/zuvion-tv/releases/latest) no celular e baixe o arquivo **`ZuvionTV-…-universal.apk`**.
2. Toque no arquivo baixado. Se o Android pedir, permita **instalar apps desta fonte**.

### Na Android TV / Google TV

1. Na TV, instale o app **Downloader** (by AFTVnews) pela Play Store.
2. No Downloader, digite o endereço abaixo e baixe o APK universal:
   `github.com/waarreno/zuvion-tv/releases/latest`
3. Quando pedir, permita que o Downloader instale apps e confirme a instalação.

Se preferir, copie o APK para um pendrive e abra-o com um gerenciador de arquivos na TV.

O mesmo APK funciona em celulares, tablets, TV boxes e Google TV, de Android 5.0 em diante.

## Primeiros passos

1. Escolha o tipo da sua lista (Xtream, M3U, MAC ou Arquivo) e preencha os dados que o seu provedor enviou.
   Na TV, toque em **Digitar pelo celular** e escaneie o QR Code para digitar pelo celular.
2. Na primeira vez, o app mostra o carregamento de **TV ao vivo, Filmes, Séries e Guia**. Depois disso, ele abre direto.
3. Para atualizar a lista quando quiser, use o botão **↻** na tela inicial ou **Ajustes → Atualizar lista agora**.

### Capas e sinopses (opcional)

Em **Ajustes → Capas e sinopses** você pode colocar chaves gratuitas:

| Serviço | Para que serve | Onde conseguir |
|---|---|---|
| **TMDB** | Capas, fundos, sinopses em português, elenco e nomes dos episódios | [themoviedb.org](https://www.themoviedb.org/settings/api), em Configurações → API |
| **OMDb** | Notas do IMDb e do Rotten Tomatoes | [omdbapi.com](https://www.omdbapi.com/apikey.aspx) |
| **TheIntroDB** | Botão "Pular abertura". Funciona sem chave | [theintrodb.org](https://theintrodb.org) |

Sem chaves, o app usa as capas e sinopses que vêm da sua lista.

## Controle remoto (TV)

| Tecla | No player |
|---|---|
| ▲ / ▼ ou CH+ / CH− | Troca de canal |
| ◀ | Lista de canais (ao vivo) / volta 10 s (filmes e séries) |
| ▶ | Avança 10 s (filmes e séries) |
| OK | Mostra os controles · pula a abertura quando o botão aparece |
| Segurar OK numa lista | Adiciona ou remove dos favoritos |

## Dúvidas comuns

**Um canal não abre.** Toque no botão de trocar o motor de vídeo (ExoPlayer ↔ VLC) no player. Em **Ajustes → Player** você também pode trocar o formato dos canais entre MPEG-TS e HLS.

**O guia está vazio.** Nem todo provedor envia o guia. Em **Ajustes → Listas IPTV → EPG**, você pode informar uma URL XMLTV própria.

**A lista não mudou depois que o provedor adicionou canais.** Use **Atualizar lista agora**.

## Privacidade

O Zuvion TV não tem servidor próprio e não coleta dados. Seus logins ficam só no aparelho. O app se conecta apenas ao seu provedor IPTV, ao GitHub (para verificar atualizações) e, se você configurar, ao TMDB, OMDb e TheIntroDB. A página de digitar pelo celular funciona só na sua rede Wi-Fi e fica ativa apenas enquanto o QR Code está na tela.

## Para desenvolvedores

Feito em Kotlin com Jetpack Compose, Media3 ExoPlayer, libVLC, OkHttp, Coil e kotlinx.serialization.

```bash
gradle :app:assembleDebug
```

Requer JDK 17 e Android SDK 35. Cada tag `vX.Y.Z` gera um Release assinado pelo GitHub Actions, com as notas de `release-notes/X.Y.Z.md`.

Fontes Inter e Sora sob a SIL Open Font License. Metadados fornecidos pelo TMDB; este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB.
