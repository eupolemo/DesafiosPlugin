package com.eupolemo.desafiosplugin.gerador;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

/**
 * O quanto cada gerador aperta. E um numero so, e cada gerador decide o que fazer com ele:
 * salto maior no parkour, menos tocha no labirinto, mais mob na sala de guardioes.
 */
public enum Dificuldade {

    FACIL("Facil", 0, NamedTextColor.GREEN),
    NORMAL("Normal", 1, NamedTextColor.YELLOW),
    DIFICIL("Dificil", 2, NamedTextColor.GOLD),
    INSANO("Insano", 3, NamedTextColor.RED);

    private final String rotulo;
    private final int nivel;
    private final NamedTextColor cor;

    Dificuldade(String rotulo, int nivel, NamedTextColor cor) {
        this.rotulo = rotulo;
        this.nivel = nivel;
        this.cor = cor;
    }

    public String rotulo() {
        return rotulo;
    }

    /** 0 (facil) a 3 (insano). */
    public int nivel() {
        return nivel;
    }

    public NamedTextColor cor() {
        return cor;
    }

    /** Interpola entre o valor de facil e o de insano. */
    public int entre(int noFacil, int noInsano) {
        return noFacil + (noInsano - noFacil) * nivel / 3;
    }

    public static Dificuldade porNome(String nome, Dificuldade padrao) {
        if (nome == null) {
            return padrao;
        }
        try {
            return valueOf(nome.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }
}
