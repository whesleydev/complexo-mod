# O Complexo Favela — mapa do binario

Dump feito com Il2CppDumper 6.7.46 (net7) sobre:
- `libil2cpp.so` 56 MB, ELF 64 bits, arm64
- `global-metadata.dat` 16 MB, magic `0xFAB11BAF`, versao 31

Saida: `dump.cs` (24 MB), `il2cpp.h` (34 MB), `script.json` (55 MB), DummyDll.

## Stack

| item | valor |
|---|---|
| engine | Unity, IL2CPP |
| rede | Photon (PUN + Realtime + Voice) |
| anti-cheat | ACTk (Anti-Cheat Toolkit) — 99 referencias |
| render | URP |
| ads | GoogleMobileAds |
| 3289 classes publicas | |

Genero: FPS tatico multiplayer (Tps*, Fp*, weapons, scope, ragdoll).

## Alvos encontrados

### PlayerBehaviour (TypeDefIndex 895)

| membro | offset |
|---|---|
| `instance` (static) | 0x0 |
| `movementSettings` | 0x58 |
| `_characterController` | 0x68 |
| `IsAlive` (backing) | 0x78 |
| `hp` | 0x88 |
| `regenEnabled` | 0x8C |
| `lastDamageTime` | 0xA0 |
| `IsRunning` | 0xB0 |
| `IsCrouching` | 0xB1 |
| `IsFire` | 0xF0 |
| `CurrentWeaponIndex` | 0x108 |

`MaxHP = 100` (const)
`GetInstance()` — acesso ao player local

### PlayerMovementSettings (TypeDefIndex 899)

| campo | offset |
|---|---|
| `AirSpeed` | 0x14 |
| `JumpSpeed` | 0x18 |
| `JumpTime` | 0x1C |
| `ForwardSpeed` | 0x24 |
| `StrafeSpeed` | 0x28 |
| `SprintSpeed` | 0x2C |
| `CrouchForwardSpeed` | 0x30 |
| `CrouchStrafeSpeed` | 0x34 |

### Outros

- `EnemyBehaviour`, `ZombieBehaviour` — IA de NPC
- `PlayerAutoshoot` — tiro automatico
- `PlayerBullet : AbstractBullet`, `PlayerGrenade`
- `TPSShooter` — sistema de tiro (interface `IPlayerTarget`)
- `SpawnManager`, `LobbyManager` — Photon

## Risco: ACTk

Anti-Cheat Toolkit instala detectores que vigiam:
- velocidade do jogador (SpeedHackDetector)
- timeScale / relogio
- integridade da memoria
- teclas injetadas

Editar `ForwardSpeed` direto na memoria e exatamente o padrao que o
SpeedHackDetector procura. Caminho mais seguro: hook na funcao que
consome o valor, com clamp, em vez de escrever no campo.

## O que e viavel com Photon

| feature | viavel | motivo |
|---|---|---|
| ESP de jogadores | sim | posicoes replicam no cliente |
| aimbot / silent aim | sim | mira e client-side |
| no recoil / no spread | sim | calculo local |
| fast fire | parcial | cadencia pode ser validada no servidor |
| god mode | nao | hp validado no master |
| dinheiro / moedas | nao | server-side |
| speed | com cuidado | ACTk + validacao |
