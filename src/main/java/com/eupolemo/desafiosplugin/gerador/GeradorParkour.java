package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.arena.Blocos;
import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.arena.Molde;
import org.bukkit.Material;

import java.util.Random;

/**
 * Salto em salto sobre um poco. Cada plataforma nasce de um passo sorteado a partir da anterior,
 * dentro do que o jogador consegue alcancar de fato — a tabela abaixo sai da fisica do jogo, nao
 * do gosto do gerador:
 *
 * <ul>
 *   <li>pulo com corrida cobre 4 blocos de distancia entre centros com folga, e 5 no limite;</li>
 *   <li>subir 1 bloco corta o alcance para 3;</li>
 *   <li>descer devolve alcance, entao queda nunca e um problema de distancia.</li>
 * </ul>
 *
 * <p>Passar do limite gera um salto impossivel e trava a sala. Por isso o passo e sempre
 * conferido antes de virar plataforma, e os ultimos metros sao costurados por um caminho que so
 * usa passos curtos, garantindo que a ultima plataforma encoste na porta de saida.
 */
public final class GeradorParkour implements Gerador {

    /** Quanto a plataforma pode subir ou descer em relacao ao nivel da porta. */
    private static final int AMPLITUDE_Y = 3;

    @Override
    public String id() {
        return "parkour";
    }

    @Override
    public String nome() {
        return "Parkour";
    }

    @Override
    public boolean aceita(Dificuldade dificuldade) {
        return true;
    }

    @Override
    public boolean pisoSolido() {
        return false;
    }

    @Override
    public void construir(Contexto ctx) {
        Molde molde = ctx.molde();
        Caixa sala = ctx.interior();
        Random rnd = ctx.rnd();

        Material fundo = ctx.dificuldade() == Dificuldade.FACIL ? Material.WATER : Material.LAVA;
        molde.preencher(new Caixa(sala.x1(), ctx.fundoPocoY() + 1, sala.z1(),
                sala.x2(), ctx.fundoPocoY() + 1, sala.z2()), fundo);

        int yPorta = ctx.blocoPisoY();
        int x = ctx.centroX();
        int y = yPorta;
        int z = ctx.zEntrada();
        plataforma(ctx, molde, sala, x, y, z, 3);

        int maxX = ctx.dificuldade() == Dificuldade.FACIL ? 1 : 2;
        int maxZ = ctx.dificuldade() == Dificuldade.FACIL ? 3 : 4;
        int alcance = ctx.dificuldade().nivel() >= Dificuldade.DIFICIL.nivel() ? 5 : 4;

        while (ctx.zSaida() - z > 6) {
            int dz = 2 + rnd.nextInt(maxZ - 1);
            int dx = rnd.nextInt(maxX * 2 + 1) - maxX;
            int dy = switch (ctx.dificuldade()) {
                case FACIL -> rnd.nextInt(3) == 0 ? 1 : 0;
                case NORMAL -> rnd.nextInt(3) - 1;
                default -> rnd.nextInt(4) - 2;
            };

            // Corta o passo ate ele caber no que da para pular. Encolher o avanco em Z e o que
            // menos estraga o traçado; mexer no dx mudaria o lado para onde a trilha vai.
            int limite = dy > 0 ? 3 : alcance;
            while (dz + Math.abs(dx) > limite && dz > 1) {
                dz--;
            }
            if (dz + Math.abs(dx) > limite) {
                dx = Integer.signum(dx) * Math.max(0, limite - dz);
            }

            int novoX = Math.clamp(x + dx, sala.x1() + 1, sala.x2() - 1);
            int novoY = Math.clamp(y + dy, yPorta - AMPLITUDE_Y, yPorta + AMPLITUDE_Y);
            int novoZ = Math.min(z + dz, ctx.zSaida());

            x = novoX;
            y = novoY;
            z = novoZ;
            plataforma(ctx, molde, sala, x, y, z, tamanho(ctx, rnd));
        }

        costurar(ctx, molde, sala, x, y, z, ctx.centroX(), yPorta, ctx.zSaida());
        iluminar(ctx, molde, sala);
    }

    /**
     * Fecha o trecho final com passos curtos garantidos. Sem isto a ultima plataforma sorteada
     * poderia ficar a um salto impossivel da porta, e a sala nao teria saida.
     */
    private void costurar(Contexto ctx, Molde molde, Caixa sala,
                          int x, int y, int z, int alvoX, int alvoY, int alvoZ) {
        int guarda = 0;
        while ((x != alvoX || y != alvoY || z != alvoZ) && guarda++ < 64) {
            int dx = Math.clamp(alvoX - x, -1, 1);
            int dy = Math.clamp(alvoY - y, -1, 1);
            // O avanco em Z e o ultimo a ser decidido, porque e ele que absorve o limite: com um
            // degrau de subida o alcance total cai para 3, e o desvio lateral ja comeu parte
            // dele. Decidir o Z antes disso foi o que gerou salto de 4 subindo — impossivel.
            int limite = (dy > 0 ? 3 : 4) - Math.abs(dx);
            int dz = Math.clamp(alvoZ - z, 0, Math.max(1, limite));
            if (dz == 0 && dx == 0 && dy == 0) {
                break;
            }
            x += dx;
            y += dy;
            z += dz;
            plataforma(ctx, molde, sala, x, y, z, 2);
        }
        plataforma(ctx, molde, sala, alvoX, alvoY, alvoZ, 3);
    }

    private int tamanho(Contexto ctx, Random rnd) {
        return switch (ctx.dificuldade()) {
            case FACIL -> 3;
            case NORMAL -> rnd.nextInt(10) < 6 ? 3 : 1;
            case DIFICIL -> rnd.nextInt(10) < 3 ? 3 : 1;
            case INSANO -> 1;
        };
    }

    /** Plataforma quadrada de lado impar, centrada no ponto e cortada pelas paredes da sala. */
    private void plataforma(Contexto ctx, Molde molde, Caixa sala, int x, int y, int z, int lado) {
        int raio = Math.max(0, (lado - 1) / 2);
        Material material = switch (ctx.dificuldade()) {
            case FACIL -> Material.STONE_BRICKS;
            case NORMAL -> Material.POLISHED_ANDESITE;
            case DIFICIL -> Material.POLISHED_DEEPSLATE;
            case INSANO -> Material.OBSIDIAN;
        };
        for (int px = x - raio; px <= x + raio; px++) {
            for (int pz = z - raio; pz <= z + raio; pz++) {
                if (px < sala.x1() || px > sala.x2() || pz < sala.z1() || pz > sala.z2()) {
                    continue;
                }
                molde.por(px, y, pz, material);
            }
        }
    }

    /** Sem luz o jogador nao ve a proxima plataforma; a lava do fundo sozinha nao alcanca. */
    private void iluminar(Contexto ctx, Molde molde, Caixa sala) {
        for (int z = sala.z1() + 2; z <= sala.z2(); z += 5) {
            molde.por(sala.x1(), ctx.tetoY(), z, Material.SEA_LANTERN);
            molde.por(sala.x2(), ctx.tetoY(), z, Material.SEA_LANTERN);
            molde.por(ctx.centroX(), ctx.tetoY(), z, Blocos.lanternaPendurada());
        }
    }
}
