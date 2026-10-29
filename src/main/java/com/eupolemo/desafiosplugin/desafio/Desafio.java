package com.eupolemo.desafiosplugin.desafio;

import com.eupolemo.desafiosplugin.gerador.Dificuldade;

import java.util.List;

/**
 * Um desafio configurado. Nao e a arena: e a receita dela. Um mesmo desafio pode ser construido
 * varias vezes, e cada construcao sorteia salas diferentes a partir da seed.
 *
 * @param tempoLimite segundos ate a corrida falhar; 0 desliga o cronometro
 * @param tipos       geradores permitidos; vazio libera todos
 */
public record Desafio(String id, String nome, Dificuldade dificuldade, int etapas,
                      int tempoLimite, List<String> tipos, Premio premio) {

    public boolean temTempoLimite() {
        return tempoLimite > 0;
    }
}
