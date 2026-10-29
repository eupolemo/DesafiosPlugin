package com.eupolemo.desafiosplugin.gerador;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Onde os geradores sao registrados e de onde as salas de um desafio sao sorteadas. */
public final class Geradores {

    private static final Map<String, Gerador> POR_ID = new LinkedHashMap<>();

    static {
        registrar(new GeradorLabirinto());
        registrar(new GeradorParkour());
        registrar(new GeradorLava());
        registrar(new GeradorGelo());
        registrar(new GeradorDardos());
        registrar(new GeradorMobs());
        registrar(new GeradorPortas());
        registrar(new GeradorPonte());
    }

    private Geradores() {
    }

    private static void registrar(Gerador gerador) {
        POR_ID.put(gerador.id(), gerador);
    }

    public static Gerador porId(String id) {
        return id == null ? null : POR_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static Set<String> ids() {
        return POR_ID.keySet();
    }

    /**
     * Sorteia a sequencia de salas de uma arena.
     *
     * <p>Evita repetir o mesmo gerador em salas seguidas: duas pistas de gelo coladas cansam mais
     * do que desafiam. Quando so ha um gerador disponivel, a repeticao passa — nao ha alternativa.
     *
     * @param permitidos ids do config.yml; vazio significa "todos os que aceitam a dificuldade"
     */
    public static List<String> sortear(int etapas, Dificuldade dificuldade,
                                       List<String> permitidos, Random rnd) {
        List<Gerador> possiveis = new ArrayList<>();
        for (Gerador gerador : POR_ID.values()) {
            boolean liberado = permitidos == null || permitidos.isEmpty()
                    || permitidos.contains(gerador.id());
            if (liberado && gerador.aceita(dificuldade)) {
                possiveis.add(gerador);
            }
        }
        if (possiveis.isEmpty()) {
            possiveis.addAll(POR_ID.values());
        }

        List<String> sorteadas = new ArrayList<>(etapas);
        String anterior = null;
        for (int i = 0; i < etapas; i++) {
            Gerador escolhido = possiveis.get(rnd.nextInt(possiveis.size()));
            if (possiveis.size() > 1 && escolhido.id().equals(anterior)) {
                escolhido = possiveis.get(rnd.nextInt(possiveis.size()));
            }
            anterior = escolhido.id();
            sorteadas.add(escolhido.id());
        }
        return sorteadas;
    }
}
