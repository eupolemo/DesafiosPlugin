package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.arena.Blocos;
import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.arena.Molde;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Ponte estreita sobre o vazio. A largura cai de tres blocos no facil para um no insano, e a
 * partir do dificil o corredor tem vento: a cada poucos segundos uma rajada empurra quem esta em
 * cima para um dos lados.
 *
 * <p>A rajada e um empurrao de velocidade lateral, avisado por som e por texto antes de bater —
 * ela existe para forcar o jogador a parar e esperar, nao para derrubar quem nao tinha como
 * saber. Os vaos da ponte tem no maximo dois blocos, que e salto com corrida garantido.
 */
public final class GeradorPonte implements Gerador {

    private static final int INTERVALO_VENTO = 70;

    @Override
    public String id() {
        return "ponte";
    }

    @Override
    public String nome() {
        return "Ponte do Vazio";
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

        int largura = ctx.dificuldade().entre(3, 1);
        int chanceVao = ctx.dificuldade() == Dificuldade.FACIL ? 0 : ctx.dificuldade().entre(0, 22);
        boolean guardaCorpo = ctx.dificuldade() == Dificuldade.FACIL;

        // Linhas reservadas em cada ponta para a ponte voltar ao eixo da porta. Sem elas a ponte
        // pode terminar encostada na parede lateral, longe do vao de saida.
        int zonaPonta = Math.max(3, (sala.largura() / 2 + 3) / 2);
        int x = ctx.centroX();
        int semVaoDesde = 0;
        for (int z = ctx.zEntrada(); z <= ctx.zSaida(); z++) {
            boolean ponta = z <= ctx.zEntrada() + zonaPonta - 1 || z >= ctx.zSaida() - zonaPonta + 1;
            if (ponta) {
                x += Math.clamp(ctx.centroX() - x, -2, 2);
            } else if (rnd.nextInt(3) == 0) {
                x = Math.clamp(x + (rnd.nextBoolean() ? 1 : -1),
                        sala.x1() + largura, sala.x2() - largura);
            }

            // Dois vaos colados virariam um buraco de dois blocos com aterrissagem em tabua de um
            // bloco; um vao a cada tres linhas, no maximo, mantem o salto dentro do possivel.
            boolean vao = !ponta && semVaoDesde >= 2 && rnd.nextInt(100) < chanceVao;
            if (vao) {
                semVaoDesde = 0;
                continue;
            }
            semVaoDesde++;

            int larguraAqui = ponta ? Math.max(3, largura) : largura;
            int raio = (larguraAqui - 1) / 2;
            for (int px = x - raio; px <= x + raio; px++) {
                if (px < sala.x1() || px > sala.x2()) {
                    continue;
                }
                molde.por(px, y, z, Material.DEEPSLATE_TILES);
            }
            if (guardaCorpo) {
                molde.por(x - raio - 1, y + 1, z, Material.OAK_FENCE);
                molde.por(x + raio + 1, y + 1, z, Material.OAK_FENCE);
            }
        }

        decorar(ctx, molde, sala);
    }

    @Override
    public void tick(Ambiente amb) {
        if (amb.dificuldade().nivel() < Dificuldade.DIFICIL.nivel()) {
            return;
        }
        int fase = amb.tick() % INTERVALO_VENTO;
        if (fase == INTERVALO_VENTO - 20) {
            amb.jogador().sendActionBar(Component.text("O vento esta vindo...",
                    NamedTextColor.AQUA));
            amb.mundo().playSound(amb.jogador().getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.6f, 0.6f);
        } else if (fase == 0) {
            double forca = amb.dificuldade() == Dificuldade.INSANO ? 0.62 : 0.42;
            int sentido = amb.rndDe("vento:" + amb.tick() / INTERVALO_VENTO).nextBoolean() ? 1 : -1;
            amb.jogador().setVelocity(amb.jogador().getVelocity()
                    .add(new Vector(sentido * forca, 0.12, 0)));
            amb.mundo().playSound(amb.jogador().getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 0.8f);
        }
    }

    private void decorar(Contexto ctx, Molde molde, Caixa sala) {
        for (int z = sala.z1() + 2; z <= sala.z2(); z += 4) {
            molde.por(ctx.centroX(), ctx.tetoY(), z, Material.CHAIN);
            molde.por(ctx.centroX(), ctx.tetoY() - 1, z, Blocos.lanternaPendurada());
        }
    }
}
