# complexo-mod

Menu flutuante para **O Complexo Favela** (`com.BrunoSanta.OComplexo`), Android, sem root.

## Por que nao da so com Shizuku

Shizuku roda comandos como usuario `shell` (uid 2000). Isso da `pm`, `am`,
`appops` e `app_process`. Nao da para ler a memoria de outro app: o SELinux
bloqueia `ptrace` de `shell` para dentro de um processo de app, e sem root
nao existe bypass disso.

Entao o menu tem que estar **dentro** do processo do jogo. A forma de fazer
isso sem root e injetar um `ContentProvider` no APK. Uma vez.

## O que o Shizuku faz aqui (e e muito)

Ele e o **banco de testes**. Depois do primeiro build:

```bash
pm install -r mod.apk
appops set com.BrunoSanta.OComplexo SYSTEM_ALERT_WINDOW allow
am force-stop com.BrunoSanta.OComplexo
am start -n com.BrunoSanta.OComplexo/<activity>
```

Tudo isso via `Shizuku.newProcess(...)` dentro de um app companheiro. Teste
completo em ~10 segundos, sem tocar na tela, sem repackar de novo.

## O que muda a cada teste

Nada no APK. O menu le a config do proprio diretorio externo do jogo:

```
/sdcard/Android/data/com.BrunoSanta.OComplexo/files/mod.cfg
```

```
speed=2.5
fastattack=true
esp=false
esp_name=true
esp_distance=true
log=true
```

Edita o arquivo, reinicia o jogo, menu novo. **O 1 GB e repackado uma vez so.**

## Build

```bash
python3 build_mod.py --base OComplexo.apk --out build
```

Saida: `build/mod-OComplexo.apk`. Instala com `adb install -r` ou pelo Shizuku.

O script:
1. compila o menu em `classes.dex` (javac + d8)
2. `apktool d -s -r` (so o manifest, nao decodifica o jogo)
3. injeta `<provider android:name="com.lunar.mod.Boot">`
4. `apktool b` com os recursos e o dex originais intactos
5. adiciona o menu como `classesN.dex` (multidex nativo, API 21+)
6. zipalign + apksigner com chave propria

## Estrutura

| arquivo | papel |
|---|---|
| `Boot.java` | ContentProvider, roda antes da Activity |
| `Config.java` | le/escreve `mod.cfg` |
| `Menu.java` | bolha arrastavel + painel com switches e slider |
| `Features.java` | ponte menu -> motor |
| `Engine.java` | JNI para `liblunar.so` (opcional) |

Sem `liblunar.so` o menu funciona em modo registro: mostra, loga, salva a
config, mas nao altera o jogo. A lib e a proxima peca.

## Proximo passo: motor

O jogo e Unity. O motor vai:

1. `dlopen` do `libil2cpp.so` do jogo
2. achar as funcoes por symbol/pattern (dump com Il2CppDumper)
3. hook com Dobby (`DobbyHook`) ou patch de GOT
4. expor via JNI o que o `Engine.java` ja espera

Depois disso cada feature e um hook: velocidade, dano, ESP lendo a lista de
objetos do jogo.

## Aviso tecnico

Repackar muda a assinatura. Se o jogo tiver Play Integrity ou checagem de
assinatura no servidor, ele recusa. O primeiro teste responde isso: se abrir
normal, nao tem; se fechar na tela de loading, tem, e o caminho muda para
container virtual.
