package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.arena.Blocos;
import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.arena.Molde;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Corredor varrido por flechas. Nas paredes ficam olhos — blocos observer, que parecem armadilha
 * e nao abrem menu nenhum — e de tempos em tempos cada um cospe uma flecha para o outro lado.
 *
 * <p>As flechas nao sao redstone: um dispenser de verdade gastaria item, encheria o chao de
 * flecha catavel e dependeria de um relogio funcionando em chunk carregado. Aqui elas nascem
 * pelo plugin, sem gravidade (para atravessarem o corredor na horizontal) e somem sozinhas.
 *
 * <p>As posicoes dos olhos nao vao para o disco: sao recalculadas com {@code rndDe("olhos")}, que
 * depende so da seed da arena e do indice da sala. Construcao e corrida chegam na mesma lista,
 * inclusive depois de o servidor reiniciar.
 */
public final class GeradorDardos implements Gerador {

    private static final int VIDA_DA_FLECHA_EM_TICKS = 60;
    /** Blocos por tick. Atravessa a sala em poucos ticks: da para desviar, nao da para reagir. */
    private static final float VELOCIDADE = 3.0f;
    /** Dano final em pontos de vida, no facil. Dois coracoes cheios; sobe um por dificuldade. */
    private static final double DANO_MINIMO = 4.0;

    @Override
    public String id() {
        return "dardos";
    }

    @Override
    public String nome() {
        return "Corredor dos Dardos";
    }

    @Override
    public boolean aceita(Dificuldade dificuldade) {
        return true;
    }

    @Override
    public void construir(Contexto ctx) {
        Molde molde = ctx.molde();
        Caixa sala = ctx.interior();

        for (int[] olho : olhos(sala, ctx.dificuldade(), ctx.rndDe("olhos"))) {
            BlockFace face = olho[3] > 0 ? BlockFace.EAST : BlockFace.WEST;
            molde.por(olho[0], olho[1], olho[2], Blocos.olho(face));
        }

        abrigos(ctx, molde, sala, ctx.rnd());

        for (int z = sala.z1() + 1; z <= sala.z2(); z += 4) {
            molde.por(ctx.centroX(), ctx.tetoY(), z, Blocos.lanternaPendurada());
        }
    }

    @Override
    public void tick(Ambiente amb) {
        int intervalo = amb.dificuldade().entre(30, 8);
        if (amb.tick() % intervalo != 0) {
            return;
        }
        // O dano final de uma flecha e ceil(velocidade x dano base), entao o dano base tem de ser
        // dividido pela velocidade para o resultado ser o que se quer de fato. Sem essa divisao,
        // deixar a flecha mais rapida ja triplicaria o estrago sem ninguem ter pedido.
        double dano = (DANO_MINIMO + amb.dificuldade().nivel()) / VELOCIDADE;
        for (int[] olho : olhos(amb.interior(), amb.dificuldade(), amb.rndDe("olhos"))) {
            Location saida = new Location(amb.mundo(),
                    olho[0] + 0.5 + olho[3] * 0.8, olho[1] + 0.5, olho[2] + 0.5);
            Arrow flecha = amb.mundo().spawnArrow(saida, new Vector(olho[3], 0, 0), VELOCIDADE, 0f);
            flecha.setGravity(false);
            flecha.setDamage(dano);
            flecha.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            flecha.setPersistent(false);
            amb.plugin().getServer().getScheduler().runTaskLater(amb.plugin(), () -> {
                if (flecha.isValid()) {
                    flecha.remove();
                }
            }, VIDA_DA_FLECHA_EM_TICKS);
        }
        amb.mundo().playSound(amb.jogador().getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.4f, 1.4f);
    }

    @Override
    public void aoSair(Ambiente amb) {
        Caixa sala = amb.interior();
        Location centro = new Location(amb.mundo(), sala.centroX() + 0.5,
                sala.y1() + sala.altura() / 2.0, sala.centroZ() + 0.5);
        double raioX = sala.largura() / 2.0 + 2;
        double raioY = sala.altura() / 2.0 + 2;
        double raioZ = sala.comprimento() / 2.0 + 2;
        for (var entidade : amb.mundo().getNearbyEntities(centro, raioX, raioY, raioZ)) {
            if (entidade instanceof Arrow) {
                entidade.remove();
            }
        }
    }

    /**
     * @return {x, y, z, sentido} de cada olho. O sentido e +1 quando ele esta na parede oeste e
     *         atira para leste, -1 no contrario.
     */
    private static List<int[]> olhos(Caixa sala, Dificuldade dificuldade, Random rnd) {
        int quantos = dificuldade.entre(4, 16);
        List<int[]> lista = new ArrayList<>(quantos);
        for (int i = 0; i < quantos; i++) {
            // Nunca colado nas portas: sair do checkpoint ja levando flecha na cara e injusto.
            int z = sala.z1() + 2 + rnd.nextInt(Math.max(1, sala.comprimento() - 4));
            int y = sala.y1() + rnd.nextInt(2);
            boolean oeste = rnd.nextBoolean();
            lista.add(new int[]{oeste ? sala.x1() - 1 : sala.x2() + 1, y, z, oeste ? 1 : -1});
        }
        return lista;
    }

    /** Colunas para se esconder entre uma salva e outra. Sem elas a sala vira sorte, nao timing. */
    private void abrigos(Contexto ctx, Molde molde, Caixa sala, Random rnd) {
        int quantos = ctx.dificuldade().entre(6, 3);
        for (int i = 0; i < quantos; i++) {
            int z = sala.z1() + 2 + rnd.nextInt(Math.max(1, sala.comprimento() - 4));
            int x = ctx.centroX() + rnd.nextInt(5) - 2;
            if (x < sala.x1() || x > sala.x2()) {
                continue;
            }
            for (int y = ctx.pisoY(); y <= ctx.pisoY() + 2; y++) {
                molde.por(x, y, z, Material.CHISELED_STONE_BRICKS);
            }
        }
    }
}
