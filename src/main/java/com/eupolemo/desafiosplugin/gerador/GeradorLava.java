package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.arena.Blocos;
import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.arena.Molde;
import org.bukkit.Material;

import java.util.Random;

/**
 * Lago de lava com uma trilha de pedras. O caminho e sorteado bloco a bloco da entrada ate a
 * saida e desenhado antes da lava, entao ele sempre existe; o que muda com a dificuldade e a
 * largura da trilha, quantos vaos ela tem e quantas pedras sao magma, que queima quem pisa.
 *
 * <p>A lava aqui e cenario perigoso, nao castigo: quem encosta volta ao checkpoint da sala em
 * vez de morrer, se {@code corrida.volta-por-queimadura} estiver ligado.
 */
public final class GeradorLava implements Gerador {

    @Override
    public String id() {
        return "lava";
    }

    @Override
    public String nome() {
        return "Rio de Lava";
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
        int y = ctx.blocoPisoY();

        // Cava um dedo abaixo do piso e enche de lava: a superficie fica no nivel do chao, entao
        // pisar fora da trilha e cair dentro, nao escorregar por cima.
        molde.preencher(new Caixa(sala.x1(), y, sala.z1(), sala.x2(), y, sala.z2()), Material.LAVA);
        molde.preencher(new Caixa(sala.x1(), y - 1, sala.z1(), sala.x2(), y - 1, sala.z2()),
                Material.BLACKSTONE);

        int largura = ctx.dificuldade().entre(3, 1);
        int chanceMagma = ctx.dificuldade().entre(0, 30);
        int chanceVao = ctx.dificuldade() == Dificuldade.FACIL ? 0 : ctx.dificuldade().entre(0, 25);

        // Quantas linhas de cada ponta a trilha usa para voltar ao centro. Ela anda no maximo
        // dois blocos por linha, e o desvio maximo e meia sala: com menos linhas do que isso, a
        // trilha terminaria fora do eixo da porta e a saida ficaria cercada de lava.
        int zonaPonta = Math.max(3, (sala.largura() / 2 + 3) / 2);
        int x = ctx.centroX();
        int semVaoDesde = 0;
        for (int z = ctx.zEntrada(); z <= ctx.zSaida(); z++) {
            boolean ponta = z <= ctx.zEntrada() + zonaPonta - 1 || z >= ctx.zSaida() - zonaPonta + 1;
            if (ponta) {
                x += Math.clamp(ctx.centroX() - x, -2, 2);
            } else {
                x = Math.clamp(x + rnd.nextInt(3) - 1, sala.x1() + 1, sala.x2() - 1);
            }

            int larguraAqui = ponta ? Math.max(3, largura) : largura;
            // Um vao de uma linha so, e nunca dois seguidos: dois viram um buraco de dois blocos
            // com aterrissagem numa pedra de um, o que no insano e salto impossivel.
            boolean vao = !ponta && semVaoDesde >= 2 && rnd.nextInt(100) < chanceVao;
            if (vao) {
                semVaoDesde = 0;
                continue;
            }
            semVaoDesde++;

            int raio = (larguraAqui - 1) / 2;
            for (int px = x - raio; px <= x + raio; px++) {
                if (px < sala.x1() || px > sala.x2()) {
                    continue;
                }
                boolean magma = !ponta && rnd.nextInt(100) < chanceMagma;
                Material pedra = magma ? Material.MAGMA_BLOCK
                        : rnd.nextInt(4) == 0 ? Material.BLACKSTONE : Material.NETHERRACK;
                molde.por(px, y, z, pedra);
            }
        }

        decorar(ctx, molde, sala, rnd);
    }

    /** Colunas de basalto e luz de alma: da para enxergar a trilha e a sala nao fica um corredor liso. */
    private void decorar(Contexto ctx, Molde molde, Caixa sala, Random rnd) {
        for (int z = sala.z1() + 3; z <= sala.z2() - 2; z += 6) {
            int x = rnd.nextInt(2) == 0 ? sala.x1() : sala.x2();
            for (int y = ctx.pisoY(); y <= ctx.tetoY() - 2; y++) {
                molde.por(x, y, z, Material.POLISHED_BASALT);
            }
            molde.por(x, ctx.tetoY(), z, Blocos.lanternaAlmaPendurada());
        }
        for (int z = sala.z1() + 1; z <= sala.z2(); z += 4) {
            molde.por(ctx.centroX(), ctx.tetoY(), z, Blocos.lanternaAlmaPendurada());
        }
    }
}
