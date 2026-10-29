package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.arena.Blocos;
import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.arena.Molde;
import org.bukkit.Material;

import java.util.Random;

/**
 * Piso de gelo azul cheio de buracos. O gelo azul e o bloco mais escorregadio do jogo: o jogador
 * nao para onde quer, e o obstaculo e o excesso de velocidade, nao o salto.
 *
 * <p>A pista e desenhada linha a linha em Z. Cada linha e um trecho continuo de gelo cujo centro
 * anda no maximo um bloco em relacao ao da linha anterior — com isso duas linhas vizinhas sempre
 * se sobrepoem em pelo menos um bloco, e existe caminho da entrada ate a saida por construcao.
 * Sortear buraco por buraco nao daria essa garantia.
 */
public final class GeradorGelo implements Gerador {

    @Override
    public String id() {
        return "gelo";
    }

    @Override
    public String nome() {
        return "Pista de Gelo";
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
        int y = ctx.blocoPisoY();

        molde.preencher(new Caixa(sala.x1(), ctx.fundoPocoY() + 1, sala.z1(),
                sala.x2(), ctx.fundoPocoY() + 1, sala.z2()), Material.POWDER_SNOW);

        int largura = ctx.dificuldade().entre(7, 2);
        int x = ctx.centroX();
        // Guardado para os pilares: eles so entram na borda de uma linha larga, nunca no centro,
        // senao dois pilares vizinhos fechariam a pista e a sala ficaria sem saida.
        int[] centros = new int[sala.comprimento()];
        int[] larguras = new int[sala.comprimento()];

        // Ver o comentario do mesmo calculo no gerador de lava: e o numero de linhas que a
        // pista precisa para voltar ao eixo da porta antes de acabar.
        int zonaPonta = Math.max(3, (sala.largura() / 2 + 3) / 2);

        for (int z = ctx.zEntrada(); z <= ctx.zSaida(); z++) {
            boolean ponta = z <= ctx.zEntrada() + zonaPonta - 1 || z >= ctx.zSaida() - zonaPonta + 1;
            if (ponta) {
                x += Math.clamp(ctx.centroX() - x, -2, 2);
            } else {
                x = Math.clamp(x + rnd.nextInt(3) - 1, sala.x1() + largura / 2,
                        sala.x2() - largura / 2);
            }

            int larguraAqui = ponta ? Math.max(5, largura) : largura;
            centros[z - ctx.zEntrada()] = x;
            larguras[z - ctx.zEntrada()] = larguraAqui;
            int raio = (larguraAqui - 1) / 2;
            for (int px = x - raio; px <= x + raio; px++) {
                if (px < sala.x1() || px > sala.x2()) {
                    continue;
                }
                molde.por(px, y, z, Material.BLUE_ICE);
            }
        }

        obstaculos(ctx, molde, rnd, centros, larguras);
        decorar(ctx, molde, sala);
    }

    /**
     * Pilares de gelo empacotado na beira da pista, a partir do normal. Sao inofensivos parados;
     * o problema e frear a tempo de desviar. Ficam sempre fora da coluna central da linha, entao
     * o caminho continua aberto por construcao.
     */
    private void obstaculos(Contexto ctx, Molde molde, Random rnd, int[] centros, int[] larguras) {
        int quantos = ctx.dificuldade().entre(0, 10);
        for (int i = 0; i < quantos; i++) {
            int indice = 3 + rnd.nextInt(Math.max(1, centros.length - 6));
            if (larguras[indice] < 3) {
                continue;
            }
            int raio = (larguras[indice] - 1) / 2;
            int x = centros[indice] + (rnd.nextBoolean() ? raio : -raio);
            int z = ctx.zEntrada() + indice;
            for (int y = ctx.pisoY(); y < ctx.pisoY() + 3; y++) {
                molde.por(x, y, z, Material.PACKED_ICE);
            }
        }
    }

    private void decorar(Contexto ctx, Molde molde, Caixa sala) {
        for (int z = sala.z1(); z <= sala.z2(); z += 3) {
            molde.por(sala.x1(), ctx.tetoY(), z, Material.SEA_LANTERN);
            molde.por(sala.x2(), ctx.tetoY(), z, Material.SEA_LANTERN);
            if (z % 2 == 0) {
                molde.por(ctx.centroX(), ctx.tetoY(), z, Blocos.lanternaPendurada());
            }
        }
    }
}
