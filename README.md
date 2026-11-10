# Zuvion TV

**Sua TV, seu universo.**

Player IPTV para Android, feito para **celular** e **Android TV / Google TV**. É leve e tem um visual inspirado no *liquid glass*: painéis translúcidos sobre um fundo de espaço profundo.

> O Zuvion TV é só um player: não fornece canais nem conteúdo. Use apenas listas às quais você tem direito.

## Recursos

- **Listas:** Xtream Codes, M3U/M3U8 por URL, arquivo M3U do aparelho e portal MAC (Stalker)
- **Ao vivo, Filmes e Séries:** categorias, busca, favoritos e "continuar assistindo"
- **Guia de programação (EPG):** grade de horários pelo XMLTV do servidor ou por uma URL própria
- **Replay (catch-up):** volta ao início do programa, quando o servidor oferece
- **Perfis:** perfil infantil e PIN de controle parental para categorias adultas
- **Multi-tela:** até 4 canais ao mesmo tempo
- **Gravação:** grava o programa ao vivo em TS ou HLS
- **Player:** ExoPlayer (Media3) por padrão e libVLC como reserva automática; trilhas de áudio e legenda; ajuste de proporção
- **Atualização:** o app consulta os Releases deste repositório e só instala com a sua confirmação

## Controle remoto (TV)

| Tecla | No player |
|---|---|
| ▲ / ▼ ou CH+/CH− | Troca de canal |
| ◀ | Lista de canais (ao vivo) / volta 10 s (filmes) |
| ▶ | Avança 10 s (filmes) |
| OK | Mostra os controles |
| Segurar OK numa lista | Adiciona ou remove dos favoritos |

## Como publicar uma atualização

1. Opcional: escreva as novidades em `release-notes/X.Y.Z.md`. Esse texto aparece na tela de atualização do app.
2. Crie e envie a tag:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
3. O GitHub Actions compila, assina e publica o Release com um APK por arquitetura e um universal.
4. Os aparelhos com o app instalado verificam a cada 6 horas (ou em **Ajustes → Verificar atualizações**), mostram as novidades e instalam quando o usuário confirmar.

### Assinatura

A chave de assinatura fica nos *secrets* do repositório: `ZUVION_KEYSTORE_B64`, `ZUVION_KEYSTORE_PASSWORD`, `ZUVION_KEY_ALIAS` e `ZUVION_KEY_PASSWORD`.
**Guarde uma cópia da keystore em local seguro.** Sem ela, as próximas versões não instalam por cima das antigas.

## Compilar localmente

Precisa de JDK 17 e Android SDK 35. Abra a pasta no Android Studio, ou rode:

```bash
gradle :app:assembleDebug
```

## Tecnologia

Kotlin, Jetpack Compose, Media3 ExoPlayer, libVLC, OkHttp, Coil e kotlinx.serialization.
