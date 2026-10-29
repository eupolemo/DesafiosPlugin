package com.eupolemo.desafiosplugin.gerador;

/**
 * Uma sala de obstaculo. Cada gerador desenha a sua sala no molde e, se precisar, se comporta
 * durante a corrida — disparar flecha, soltar mob, empurrar quem esta na ponte.
 *
 * <p>Contrato que todo gerador tem de cumprir: existe pelo menos um caminho da porta de entrada
 * ate a porta de saida. Sala impossivel nao e desafio, e travar o jogador dentro dela nao tem
 * como ser detectado a tempo — por isso os algoritmos aqui constroem o caminho primeiro e so
 * depois enfeitam em volta.
 */
public interface Gerador {

    /** Id usado no config.yml e no arquivo de arenas. Minusculo, sem espaco. */
    String id();

    /** Nome mostrado na placa da porta e na barra da corrida. */
    String nome();

    boolean aceita(Dificuldade dificuldade);

    /**
     * @return false se a sala e um poco: o construtor tira o piso e afunda as paredes ate o
     *         fundo, e cair passa a devolver ao checkpoint
     */
    default boolean pisoSolido() {
        return true;
    }

    void construir(Contexto ctx);

    /** Chamado uma vez, quando o jogador cruza a porta de entrada desta sala. */
    default void aoEntrar(Ambiente amb) {
    }

    /** Chamado a cada tique da corrida enquanto o jogador esta nesta sala. */
    default void tick(Ambiente amb) {
    }

    /** Chamado quando o jogador sai da sala, termina ou abandona. Serve para limpar o que ficou. */
    default void aoSair(Ambiente amb) {
    }
}
