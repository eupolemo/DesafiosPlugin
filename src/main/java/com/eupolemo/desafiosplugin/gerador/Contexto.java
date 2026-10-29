package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.arena.Arena;
import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.arena.Molde;
import org.bukkit.World;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Tudo que um gerador precisa para desenhar a sua sala. Ele escreve no {@link Molde} e nunca
 * encosta no mundo: quem despeja os blocos e o construtor, em lotes por tick.
 *
 * <p>O {@code Random} vem da seed da arena somada ao indice da sala, entao a mesma seed sempre
 * desenha a mesma sala — e o comportamento em tempo de execucao consegue redescobrir as mesmas
 * posicoes de armadilha depois de um restart, sem guardar nada em disco.
 *
 * @param depois acoes que precisam do mundo ja construido (placa com texto, bau, spawner)
 */
public record Contexto(Molde molde, Arena arena, int indice, Caixa interior,
                       Dificuldade dificuldade, Random rnd, List<Consumer<World>> depois) {

    /** Y dos pes de quem esta em pe no piso da sala. */
    public int pisoY() {
        return interior.y1();
    }

    /** Y do bloco de piso, logo abaixo dos pes. */
    public int blocoPisoY() {
        return interior.y1() - 1;
    }

    public int tetoY() {
        return interior.y2();
    }

    public int centroX() {
        return arena.centroX();
    }

    /** Z da primeira linha jogavel, colada na porta de entrada. */
    public int zEntrada() {
        return interior.z1();
    }

    /** Z da ultima linha jogavel, colada na porta de saida. */
    public int zSaida() {
        return interior.z2();
    }

    /** Chao do poco, quando a sala nao tem piso solido. */
    public int fundoPocoY() {
        return interior.y1() - 1 - Arena.PROFUNDIDADE_POCO;
    }

    public void aposConstruir(Consumer<World> acao) {
        depois.add(acao);
    }

    public int sorteio(int minimoInclusive, int maximoInclusive) {
        return minimoInclusive + rnd.nextInt(maximoInclusive - minimoInclusive + 1);
    }

    /**
     * Um sorteio a parte, ancorado na seed da arena e no indice da sala. Serve para o que
     * precisa dar o mesmo resultado na construcao e depois, em tempo de corrida: as posicoes
     * dos olhos que atiram sao recalculadas assim, em vez de irem para o disco.
     */
    public java.util.Random rndDe(String etiqueta) {
        return new java.util.Random(arena.seed() * 31L + indice * 8191L + etiqueta.hashCode());
    }
}
