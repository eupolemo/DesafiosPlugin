# ADR-0001 — Salas sorteadas por seed, com caminho garantido por construção

Data: 2026-08-29
Status: aceito

## Contexto

O plugin precisa criar obstáculos atravessáveis: labirinto, parkour, armadilhas. Duas coisas
brigam entre si.

A primeira é a variedade. Um desafio que é sempre igual acaba na segunda vez que alguém o joga: a
graça está em não saber o que vem na próxima sala.

A segunda é a garantia de que dá para atravessar. Uma sala sem saída não é difícil, é quebrada — e
não há como o servidor perceber isso a tempo. O jogador fica preso, sem entender se errou ou se o
plugin errou, e não existe mensagem de erro que conserte a sensação.

## Decisão

Cada arena guarda uma `seed`. As salas são sorteadas a partir dela, e cada gerador desenha a sua
sala com um `Random` derivado da seed mais o índice da sala.

Todo gerador constrói o caminho **primeiro** e enfeita em volta **depois**, de forma que a
travessia seja consequência do algoritmo, e não algo a ser verificado no fim:

- o labirinto usa backtracking, que produz um labirinto perfeito — sem ciclo, com exatamente um
  caminho entre dois pontos quaisquer;
- o parkour sorteia cada salto dentro do alcance real do jogo e costura os últimos metros com
  passos curtos garantidos, para a última plataforma sempre encostar na porta;
- as pistas por linha (gelo, lava, ponte) movem o centro da faixa no máximo dois blocos por linha,
  o que faz linhas vizinhas sempre se tocarem;
- o enigma de portas abre a passagem certa antes de fechar as erradas.

Os obstáculos que dependem de sorte na hora — a posição dos olhos que atiram, o lado da rajada de
vento — recalculam o sorteio a partir da mesma seed, em vez de gravar posição em disco.

## Consequências

Boas:

- a mesma seed reconstrói a mesma arena, o que torna um desafio reproduzível e um bug relatável;
- não existe passo de validação depois da construção, que é justamente o passo que costuma não
  cobrir o caso raro;
- nada do estado de armadilha precisa sobreviver a um restart: ele é recalculado.

Ruins:

- a garantia mora no algoritmo, não num teste que roda sozinho. Mexer num gerador exige entender
  por que ele fecha o caminho, e por isso cada um traz esse motivo escrito no comentário da
  classe;
- o desenho fica limitado ao que dá para provar por construção. Uma sala com alavanca, porta que
  abre outra e chave escondida daria um enigma melhor e não caberia nesta decisão sem mais
  ferramenta.

## Alternativas descartadas

**Salas prontas em arquivo (schematic).** Garantia total de que dá para atravessar, e nenhuma
variedade: a terceira corrida já é decorada. Também exigiria formato de arquivo, editor e uma
biblioteca a mais.

**Sortear livre e validar com busca de caminho depois.** Seria o mais flexível, e é o que
permitiria a sala com chave e alavanca. Custa uma busca em grafo por sala construída e, quando ela
reprova, alguém tem de decidir o que fazer — sortear de novo (quantas vezes?) ou remendar a sala
(como?). Fica na mesa para quando um gerador precisar de mais liberdade do que a construção
garantida oferece.

**Mundo separado só para os desafios.** Isolaria a arena do mundo de sobrevivência e dispensaria o
registro de blocos. Custa gerar e carregar um mundo inteiro, teleporte entre mundos no meio da
brincadeira e um lugar a mais onde o jogador pode ficar preso. O registro de blocos que desfaz a
arena já resolve o problema que motivaria isso.
