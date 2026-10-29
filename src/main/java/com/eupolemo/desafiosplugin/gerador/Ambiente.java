package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.DesafiosPlugin;
import com.eupolemo.desafiosplugin.arena.Arena;
import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.corrida.Corrida;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * O que um gerador ve enquanto alguem esta correndo a sala dele: serve para disparar flecha,
 * soprar vento, soltar o mob e abrir a grade.
 *
 * <p>O {@code Random} e recriado com a mesma semente do momento da construcao, entao as posicoes
 * de armadilha calculadas agora batem com as que foram construidas — inclusive depois de um
 * restart do servidor, sem nada disso ter ido para o disco.
 */
public record Ambiente(DesafiosPlugin plugin, World mundo, Arena arena, int indice, Caixa interior,
                       Dificuldade dificuldade, Corrida corrida, Player jogador, int tick,
                       Random rnd) {

    public int pisoY() {
        return interior.y1();
    }

    public int centroX() {
        return arena.centroX();
    }

    /** Chave para o gerador guardar estado por corrida sem colidir com outra sala. */
    public String chaveEstado(String nome) {
        return indice + ":" + nome;
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
