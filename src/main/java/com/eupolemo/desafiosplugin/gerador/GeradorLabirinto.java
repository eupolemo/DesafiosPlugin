package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.arena.Molde;
import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Labirinto perfeito: preenche a sala de parede e escava uma arvore de corredores com o
 * algoritmo de backtracking. Perfeito quer dizer sem ciclo — existe exatamente um caminho entre
 * dois pontos quaisquer, entao a saida e sempre alcancavel e nunca da para contornar por fora.
 *
 * <p>A grade e de celulas espacadas de dois em dois: as colunas impares sao corredor, as pares
 * sao a parede que separa. Por isso a sala precisa de largura e comprimento impares — com um
 * numero par sobra uma faixa de parede colada no canto, que so fica feia, nao quebra nada.
 *
 * <p>As paredes tem tres blocos de altura. Nao da para pular uma (o pulo do Minecraft sobe 1,25)
 * e o teto da sala fica acima delas, entao de dentro nao se ve o traçado.
 */
public final class GeradorLabirinto implements Gerador {

    private static final int ALTURA_PAREDE = 3;

    @Override
    public String id() {
        return "labirinto";
    }

    @Override
    public String nome() {
        return "Labirinto";
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

        Material parede = switch (ctx.dificuldade()) {
            case FACIL -> Material.STONE_BRICKS;
            case NORMAL -> Material.MOSSY_STONE_BRICKS;
            case DIFICIL -> Material.DEEPSLATE_BRICKS;
            case INSANO -> Material.POLISHED_BLACKSTONE_BRICKS;
        };

        int yBase = ctx.pisoY();
        int yTopo = yBase + ALTURA_PAREDE - 1;
        molde.preencher(new Caixa(sala.x1(), yBase, sala.z1(), sala.x2(), yTopo, sala.z2()), parede);

        // Celulas nas posicoes impares da sala. Uma sala 15x21 vira uma grade de 7 por 10.
        int colunas = (sala.largura() - 1) / 2;
        int linhas = (sala.comprimento() - 1) / 2;
        if (colunas < 2 || linhas < 2) {
            // Sala pequena demais para uma grade: abre tudo em vez de entregar um bloco solido.
            molde.limpar(new Caixa(sala.x1(), yBase, sala.z1(), sala.x2(), yTopo, sala.z2()));
            return;
        }

        boolean[][] visitada = new boolean[colunas][linhas];
        Deque<int[]> pilha = new ArrayDeque<>();

        int colunaEntrada = Math.min(colunas - 1, Math.max(0, (ctx.centroX() - sala.x1() - 1) / 2));
        pilha.push(new int[]{colunaEntrada, 0});
        visitada[colunaEntrada][0] = true;
        escavar(molde, sala, yBase, yTopo, celulaX(sala, colunaEntrada), celulaZ(sala, 0));

        while (!pilha.isEmpty()) {
            int[] atual = pilha.peek();
            List<int[]> livres = new ArrayList<>(4);
            int[][] passos = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] passo : passos) {
                int c = atual[0] + passo[0];
                int l = atual[1] + passo[1];
                if (c >= 0 && c < colunas && l >= 0 && l < linhas && !visitada[c][l]) {
                    livres.add(new int[]{c, l});
                }
            }
            if (livres.isEmpty()) {
                pilha.pop();
                continue;
            }
            int[] escolhida = livres.get(rnd.nextInt(livres.size()));
            visitada[escolhida[0]][escolhida[1]] = true;

            // Abre a celula nova e a parede entre ela e a atual.
            escavar(molde, sala, yBase, yTopo, celulaX(sala, escolhida[0]), celulaZ(sala, escolhida[1]));
            escavar(molde, sala, yBase, yTopo,
                    (celulaX(sala, atual[0]) + celulaX(sala, escolhida[0])) / 2,
                    (celulaZ(sala, atual[1]) + celulaZ(sala, escolhida[1])) / 2);
            pilha.push(escolhida);
        }

        ligarPorta(molde, sala, yBase, yTopo, ctx.centroX(), sala.z1(),
                celulaX(sala, colunaEntrada), celulaZ(sala, 0));

        int colunaSaida = Math.min(colunas - 1, Math.max(0, (ctx.centroX() - sala.x1() - 1) / 2));
        ligarPorta(molde, sala, yBase, yTopo, ctx.centroX(), sala.z2(),
                celulaX(sala, colunaSaida), celulaZ(sala, linhas - 1));

        iluminar(ctx, molde, sala, yBase, yTopo, colunas, linhas, rnd);
        armadilhas(ctx, molde, sala, yBase, colunas, linhas, rnd);
    }

    private static int celulaX(Caixa sala, int coluna) {
        return sala.x1() + 1 + coluna * 2;
    }

    private static int celulaZ(Caixa sala, int linha) {
        return sala.z1() + 1 + linha * 2;
    }

    private static void escavar(Molde molde, Caixa sala, int yBase, int yTopo, int x, int z) {
        if (x < sala.x1() || x > sala.x2() || z < sala.z1() || z > sala.z2()) {
            return;
        }
        for (int y = yBase; y <= yTopo; y++) {
            molde.por(x, y, z, Material.AIR);
        }
    }

    /** Corredor em L da porta ate a celula da ponta, para a entrada e a saida nunca ficarem muradas. */
    private static void ligarPorta(Molde molde, Caixa sala, int yBase, int yTopo,
                                   int portaX, int portaZ, int celulaX, int celulaZ) {
        int passoZ = Integer.signum(celulaZ - portaZ);
        int z = portaZ;
        escavar(molde, sala, yBase, yTopo, portaX, z);
        while (z != celulaZ && passoZ != 0) {
            z += passoZ;
            escavar(molde, sala, yBase, yTopo, portaX, z);
        }
        int passoX = Integer.signum(celulaX - portaX);
        if (passoX != 0) {
            for (int x = portaX; x != celulaX + passoX; x += passoX) {
                escavar(molde, sala, yBase, yTopo, x, celulaZ);
            }
        }
    }

    /**
     * Luz espalhada pelo chao dos corredores. Quanto mais dificil, menos lanternas — no insano a
     * sala fica praticamente as cegas, e a unica luz vem do que o jogador levar na mao.
     */
    private static void iluminar(Contexto ctx, Molde molde, Caixa sala, int yBase, int yTopo,
                                 int colunas, int linhas, Random rnd) {
        int umACada = ctx.dificuldade().entre(5, 30);
        List<int[]> celulas = new ArrayList<>();
        for (int c = 0; c < colunas; c++) {
            for (int l = 0; l < linhas; l++) {
                celulas.add(new int[]{celulaX(sala, c), celulaZ(sala, l)});
            }
        }
        Collections.shuffle(celulas, rnd);
        int quantas = Math.max(1, celulas.size() / umACada);
        for (int i = 0; i < quantas && i < celulas.size(); i++) {
            int[] celula = celulas.get(i);
            molde.por(celula[0], yTopo + 1, celula[1], Material.SEA_LANTERN);
            molde.por(celula[0], yBase, celula[1], Material.AIR);
        }
    }

    /** Teia nos corredores a partir do normal: atrasa quem corre sem olhar. */
    private static void armadilhas(Contexto ctx, Molde molde, Caixa sala, int yBase,
                                   int colunas, int linhas, Random rnd) {
        int quantas = ctx.dificuldade().entre(0, 12);
        for (int i = 0; i < quantas; i++) {
            int x = celulaX(sala, rnd.nextInt(colunas));
            int z = celulaZ(sala, rnd.nextInt(linhas));
            if (z == sala.z1() || z == sala.z2()) {
                continue;
            }
            molde.por(x, yBase, z, Material.COBWEB);
        }
    }
}
