# DesafiosPlugin

Plugin Paper de desafios de obstáculos. Cada desafio constrói uma arena — um corredor de salas
encadeadas — e cada sala é um obstáculo diferente, sorteado na hora da construção: labirinto,
parkour, rio de lava, pista de gelo, corredor de dardos, sala de guardiões, enigma de portas e
ponte sobre o vazio. Quem atravessa todas ganha o prêmio do fim.

O que a construção sobrescreveu é gravado em disco, então `/desafio remover` continua funcionando
depois de o servidor reiniciar.

- Paper API `1.21.4-R0.1-SNAPSHOT`
- Java 21
- Maven

## Build

```
mvn clean package
```

Jar em `target/DesafiosPlugin-1.0.0.jar`.

## Instalar no servidor

Copie o jar para `plugins/` e reinicie o servidor. Ou use o script:

```
./deploy.sh /caminho/do/servidor/plugins
./deploy.sh usuario@servidor:/caminho/do/servidor/plugins
```

Grave o destino em `.deploy.env` (`DEST=...`) para rodar só `./deploy.sh`.

## Como usar

Um op constrói a arena uma vez, de pé no lugar onde ela deve nascer:

```
/desafio criar iniciante
```

A arena cresce no sentido **+Z** a partir de alguns blocos à frente de quem chamou, e nasce com um
**pórtico** na frente: uma alcova com placa, botão e placa de pressão. Depois disso ninguém mais
precisa de comando — quem chega andando lê o nome do desafio na placa e entra pisando na placa de
pressão ou apertando o botão.

O comando continua valendo para quem preferir:

```
/desafio entrar iniciante
```

Só os gatilhos do pórtico dão entrada. Um botão que alguém colocar por fora não vira porta de
desafio, e nada dentro da arena serve de gatilho.

## Comandos

| Comando | Permissão | Efeito |
|---|---|---|
| `/desafio` | `desafios.use` (todos) | Lista os desafios e quais já têm arena |
| `/desafio info <id>` | `desafios.use` | Dificuldade, etapas, prêmio e as salas sorteadas |
| `/desafio entrar <id>` | `desafios.use` | Começa a corrida |
| `/desafio sair` | `desafios.use` | Abandona — e tira da arena quem ficou preso dentro |
| `/desafio ranking <id>` | `desafios.use` | Os dez melhores tempos |
| `/desafio criar <id> [seed]` | `desafios.criar` (op) | Constrói a arena à sua frente |
| `/desafio remover <id>` | `desafios.criar` (op) | Desfaz, restaurando os blocos originais |
| `/desafio reload` | `desafios.reload` (op) | Recarrega o `config.yml` |

A mesma `seed` reconstrói a mesma arena. Sem seed, cada construção sorteia salas diferentes.
Palavra também serve como seed (`/desafio criar torre aniversario`).

## As salas

| Gerador | O obstáculo |
|---|---|
| `labirinto` | Labirinto perfeito, escavado por backtracking. Sem ciclo, então tem caminho e não tem atalho. Teia de aranha a partir do normal, e cada vez menos lanterna |
| `parkour` | Plataformas sobre um poço de lava. Cada salto sai de uma tabela de alcance real do jogo |
| `lava` | Lago de lava com trilha de pedras, algumas de magma, e vãos para pular |
| `gelo` | Piso de gelo azul cheio de buracos: o obstáculo é a velocidade, não o salto |
| `dardos` | Olhos nas paredes cospem flechas rápidas (3 blocos por tick) em salvas; há colunas para se abrigar |
| `mobs` | A grade da saída só abre quando todos os guardiões caem |
| `portas` | Três portas, uma passa. A cor certa está marcada no chão — discreta no difícil |
| `ponte` | Ponte estreita sobre o vazio. No difícil e no insano, rajadas de vento empurram |

Cair, queimar ou levar o golpe final **não mata**: devolve ao começo da sala e conta uma queda.
Quem prefere o contrário liga `corrida.morte-real` no `config.yml`.

As flechas do corredor de dardos machucam de verdade — 2 corações no fácil, 2,5 no normal, 3 no
difícil e 3,5 no insano, sem perder força ao atravessar a sala. Armadura ainda conta.

## Sem buffs dentro do desafio

Na largada o plugin tira, e enquanto a corrida dura ele barra:

- **efeitos de poção** — todos, inclusive os que o jogador já trazia; poção bebida no meio da
  corrida é recusada, e nuvem persistente ou beacon não pegam;
- **voo** — inclusive o de criativo, porque criativo e espectador viram sobrevivência na largada;
- **élitro**;
- **pérola do fim e fruto do corvo** — nada de pular sala por teleporte. A pérola nem é gasta;
- **montaria** — o jogador desce do que estiver montando.

Os efeitos que ele trazia voltam para ele quando a corrida acaba, de qualquer jeito que acabe.
Sem isso, salto reforçado transformaria o parkour num passeio e o tempo no ranking não
significaria nada.

Habilidade que outro plugin implemente fora do sistema de efeitos do jogo — um item que teleporta
por conta própria, por exemplo — este plugin não tem como enxergar; se houver alguma assim no
servidor, vale testar antes de abrir o desafio.

## Dificuldade

`facil`, `normal`, `dificil` e `insano` são um número só que cada gerador interpreta: salto maior
no parkour, menos luz no labirinto, pista mais estreita no gelo, mais guardiões, pista de cor
menor no enigma, vento na ponte.

## Prêmios

No `config.yml`, por desafio:

```yaml
premio:
  mensagem: "Voce atravessou a Trilha do Aprendiz!"
  xp: 30
  itens:
    - material: DIAMOND
      quantidade: 3
  comandos: []
```

`comandos` roda no console com `%jogador%` trocado pelo nome de quem venceu — é assim que o
prêmio vira um item de outro plugin sem este depender dele:

```yaml
  comandos:
    - "itens dar espada-do-infinito %jogador%"
```

## Limites que valem saber

- **Uma corrida por arena.** As salas guardam estado no próprio mundo — a grade dos guardiões, os
  mobs vivos —, então dois corredores ao mesmo tempo abririam a grade um do outro. Para duas
  pessoas correrem juntas, construa a arena de outro desafio.
- **A arena ocupa espaço de verdade.** Com os valores padrão, um desafio de 7 etapas gasta
  17 × 16 × 200 blocos no mundo. Escolha um lugar vazio, ou alto, e confira o `/desafio info`
  antes de construir perto de algo que importa.
- **Remover durante a construção não funciona.** A lista de blocos originais só é gravada quando
  a construção termina; interromper no meio deixa o que já foi escrito no mundo.

## Jogadores de Bedrock

Como no PlanetasPlugin: a construção e a corrida são todas server-side, então Geyser + Floodgate
resolvem. Se um jogador Bedrock não consegue `/desafio criar`, é permissão, não compatibilidade —
`desafios.criar` é `default: op` e o Floodgate prefixa o nome dele (padrão `.`):

```
op .NomeDoJogador
```

## Configuração

`config.yml` traz `arena` (medidas, blocos por tick, modo aventura), `corrida` (morte real, volta
por queimadura, aviso de tempo, anúncio de vitória) e `desafios`. Cada campo está comentado no
próprio arquivo.

Decisão de projeto registrada em [ADR-0001](docs/adr/0001-salas-sorteadas-por-seed.md).
