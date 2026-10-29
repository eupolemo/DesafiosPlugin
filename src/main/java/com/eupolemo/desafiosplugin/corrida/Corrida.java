package com.eupolemo.desafiosplugin.corrida;

import com.eupolemo.desafiosplugin.arena.Arena;
import com.eupolemo.desafiosplugin.desafio.Desafio;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.GameMode;
import org.bukkit.Location;

import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Uma tentativa em andamento. Guarda o que precisa ser devolvido quando ela acabar de qualquer
 * jeito — vitoria, desistencia, tempo esgotado ou o jogador caindo do servidor: o lugar de onde
 * ele veio, o modo de jogo que ele tinha e a barra que apareceu na tela dele.
 */
public final class Corrida {

    private final UUID jogador;
    private final Desafio desafio;
    private final Arena arena;
    private final Location voltarPara;
    private final GameMode modoAnterior;
    private final BossBar barra;
    /** Os efeitos que o jogador tinha ao entrar; sao tirados na largada e devolvidos no fim. */
    private final Collection<PotionEffect> efeitosGuardados;
    private final boolean permitiaVoo;
    private final long inicio = System.currentTimeMillis();
    /** Espaco livre para os geradores guardarem estado da sala; a chave leva o indice dela. */
    private final Map<String, Object> estado = new HashMap<>();

    private int sala;
    private int mortes;
    private boolean avisouTempo;

    public Corrida(UUID jogador, Desafio desafio, Arena arena, Location voltarPara,
                   GameMode modoAnterior, BossBar barra,
                   Collection<PotionEffect> efeitosGuardados, boolean permitiaVoo) {
        this.jogador = jogador;
        this.desafio = desafio;
        this.arena = arena;
        this.voltarPara = voltarPara;
        this.modoAnterior = modoAnterior;
        this.barra = barra;
        this.efeitosGuardados = efeitosGuardados;
        this.permitiaVoo = permitiaVoo;
    }

    public Collection<PotionEffect> efeitosGuardados() {
        return efeitosGuardados;
    }

    public boolean permitiaVoo() {
        return permitiaVoo;
    }

    public UUID jogador() {
        return jogador;
    }

    public Desafio desafio() {
        return desafio;
    }

    public Arena arena() {
        return arena;
    }

    public Location voltarPara() {
        return voltarPara;
    }

    public GameMode modoAnterior() {
        return modoAnterior;
    }

    public BossBar barra() {
        return barra;
    }

    public Map<String, Object> estado() {
        return estado;
    }

    public int sala() {
        return sala;
    }

    public void sala(int sala) {
        this.sala = sala;
    }

    public int mortes() {
        return mortes;
    }

    public void contarMorte() {
        mortes++;
    }

    public boolean avisouTempo() {
        return avisouTempo;
    }

    public void marcarAvisoDeTempo() {
        avisouTempo = true;
    }

    public long tempoMs() {
        return System.currentTimeMillis() - inicio;
    }

    /** @return segundos que faltam, ou -1 quando o desafio nao tem limite */
    public long segundosRestantes() {
        if (!desafio.temTempoLimite()) {
            return -1;
        }
        return desafio.tempoLimite() - tempoMs() / 1000;
    }

    public static String formatarTempo(long ms) {
        long total = ms / 1000;
        return String.format("%02d:%02d.%d", total / 60, total % 60, (ms % 1000) / 100);
    }
}
