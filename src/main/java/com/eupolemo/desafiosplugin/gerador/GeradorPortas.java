package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.arena.Blocos;
import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.arena.Molde;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.List;
import java.util.Random;

/**
 * Enigma de portas: uma parede fechada com tres passagens, e so uma continua. As erradas dao
 * num vao sem chao, e cair devolve ao checkpoint.
 *
 * <p>Nao e sorteio as cegas. A cor da porta certa esta marcada no chao, antes da parede: no
 * facil e um tapete de 3x3 iluminado; no insano e um bloco so, jogado num canto. A dificuldade
 * mexe em quanto a pista salta aos olhos e em quantas paredes seguidas o jogador enfrenta, nao
 * em quanto e chute.
 */
public final class GeradorPortas implements Gerador {

    private record Cor(Material la, String nome) {
    }

    private static final Cor[] CORES = {
            new Cor(Material.RED_WOOL, "vermelho"),
            new Cor(Material.BLUE_WOOL, "azul"),
            new Cor(Material.YELLOW_WOOL, "amarelo"),
            new Cor(Material.LIME_WOOL, "verde"),
            new Cor(Material.PURPLE_WOOL, "roxo"),
            new Cor(Material.ORANGE_WOOL, "laranja")
    };
    private static final int PORTAS_POR_PAREDE = 3;

    @Override
    public String id() {
        return "portas";
    }

    @Override
    public String nome() {
        return "Sala das Portas";
    }

    @Override
    public boolean aceita(Dificuldade dificuldade) {
        return true;
    }

    @Override
    public void construir(Contexto ctx) {
        Molde molde = ctx.molde();
        Caixa sala = ctx.interior();
        Random rnd = ctx.rnd();

        int camadas = ctx.dificuldade().entre(1, 3);
        // Cada camada ocupa tres linhas em Z (parede, vao, parede) e precisa de espaco antes e
        // depois; se a sala for curta demais, cabe menos camada do que a dificuldade pediria.
        int espaco = sala.comprimento() / (camadas + 1);
        if (espaco < 5) {
            camadas = Math.max(1, (sala.comprimento() - 4) / 5);
            espaco = sala.comprimento() / (camadas + 1);
        }

        Material parede = Material.POLISHED_DEEPSLATE;

        for (int camada = 0; camada < camadas; camada++) {
            int zParede = sala.z1() + espaco * (camada + 1);
            if (zParede + 2 > sala.z2() - 1) {
                break;
            }
            int certa = rnd.nextInt(PORTAS_POR_PAREDE);
            Cor corCerta = CORES[rnd.nextInt(CORES.length)];

            molde.preencher(new Caixa(sala.x1(), ctx.pisoY(), zParede,
                    sala.x2(), ctx.tetoY(), zParede), parede);
            molde.preencher(new Caixa(sala.x1(), ctx.pisoY(), zParede + 1,
                    sala.x2(), ctx.tetoY(), zParede + 1), parede);
            molde.preencher(new Caixa(sala.x1(), ctx.pisoY(), zParede + 2,
                    sala.x2(), ctx.tetoY(), zParede + 2), parede);

            for (int porta = 0; porta < PORTAS_POR_PAREDE; porta++) {
                int x = sala.x1() + sala.largura() * (porta + 1) / (PORTAS_POR_PAREDE + 1);
                boolean estaCerta = porta == certa;
                Cor cor = estaCerta ? corCerta : outraCor(corCerta, rnd);

                // Vao na primeira parede, sempre: e por ele que o jogador descobre se errou.
                molde.preencher(new Caixa(x, ctx.pisoY(), zParede, x, ctx.pisoY() + 1, zParede),
                        Material.AIR);
                molde.por(x, ctx.pisoY() + 2, zParede, cor.la());

                molde.preencher(new Caixa(x, ctx.pisoY(), zParede + 1, x, ctx.pisoY() + 1,
                        zParede + 1), Material.AIR);

                if (estaCerta) {
                    molde.preencher(new Caixa(x, ctx.pisoY(), zParede + 2, x, ctx.pisoY() + 1,
                            zParede + 2), Material.AIR);
                } else {
                    // Sem chao: quem entra cai, e a queda devolve ao checkpoint da sala.
                    molde.preencher(new Caixa(x, ctx.fundoPocoY() + 1, zParede + 1,
                            x, ctx.blocoPisoY(), zParede + 1), Material.AIR);
                    molde.por(x, ctx.fundoPocoY(), zParede + 1, Material.SOUL_SAND);
                }
            }

            pista(ctx, molde, sala, zParede, corCerta, rnd);
        }

        placaDeEntrada(ctx, molde, sala);
    }

    /** A marca no chao que diz qual cor seguir. Encolhe conforme a dificuldade sobe. */
    private void pista(Contexto ctx, Molde molde, Caixa sala, int zParede, Cor cor, Random rnd) {
        int zPista = zParede - 2;
        if (zPista <= sala.z1()) {
            zPista = sala.z1() + 1;
        }
        switch (ctx.dificuldade()) {
            case FACIL, NORMAL -> {
                for (int x = ctx.centroX() - 1; x <= ctx.centroX() + 1; x++) {
                    for (int z = zPista - 1; z <= zPista + 1; z++) {
                        molde.por(x, ctx.blocoPisoY(), z, cor.la());
                    }
                }
                if (ctx.dificuldade() == Dificuldade.FACIL) {
                    molde.por(ctx.centroX(), ctx.tetoY(), zPista, Blocos.lanternaPendurada());
                }
            }
            case DIFICIL -> molde.por(ctx.centroX(), ctx.blocoPisoY(), zPista, cor.la());
            case INSANO -> {
                int x = rnd.nextBoolean() ? sala.x1() : sala.x2();
                molde.por(x, ctx.blocoPisoY(), zPista, cor.la());
            }
        }
    }

    private void placaDeEntrada(Contexto ctx, Molde molde, Caixa sala) {
        int x = ctx.centroX() - 2;
        int y = ctx.pisoY() + 1;
        int z = sala.z1();
        molde.por(x, y, z, Blocos.placaDeParede(BlockFace.SOUTH));
        ctx.aposConstruir(mundo -> Blocos.escrever(mundo, x, y, z, List.of(
                Component.text("A cor do chao", NamedTextColor.BLACK),
                Component.text("abre a porta.", NamedTextColor.BLACK),
                Component.text("As outras", NamedTextColor.BLACK),
                Component.text("nao tem chao.", NamedTextColor.BLACK))));
    }

    private Cor outraCor(Cor evitar, Random rnd) {
        Cor escolhida;
        do {
            escolhida = CORES[rnd.nextInt(CORES.length)];
        } while (escolhida.la() == evitar.la());
        return escolhida;
    }
}
