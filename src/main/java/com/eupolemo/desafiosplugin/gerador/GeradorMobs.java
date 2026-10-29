package com.eupolemo.desafiosplugin.gerador;

import com.eupolemo.desafiosplugin.arena.Blocos;
import com.eupolemo.desafiosplugin.arena.Caixa;
import com.eupolemo.desafiosplugin.arena.Molde;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Sala de guardioes: a porta de saida nasce fechada com grade de ferro e so abre quando todos os
 * mobs caem. Os mobs aparecem quando o jogador entra, nao na construcao — bicho parado numa sala
 * vazia so consome tique do servidor e ainda pode sumir antes de alguem chegar.
 *
 * <p>A grade volta a fechar quando o jogador deixa a sala, para o proximo corredor encontrar o
 * desafio inteiro. Como a grade foi construida pela arena, o {@code /desafio remover} devolve o
 * bloco original de qualquer jeito, aberta ou fechada.
 */
public final class GeradorMobs implements Gerador {

    private static final EntityType[] COMUNS = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER
    };
    private static final EntityType[] PESADOS = {
            EntityType.HUSK, EntityType.STRAY, EntityType.CAVE_SPIDER, EntityType.WITCH
    };

    @Override
    public String id() {
        return "mobs";
    }

    @Override
    public String nome() {
        return "Sala dos Guardioes";
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

        molde.preencher(ctx.arena().portaAntesDe(ctx.indice() + 1), Material.IRON_BARS);

        int colunas = ctx.dificuldade().entre(3, 8);
        for (int i = 0; i < colunas; i++) {
            int x = sala.x1() + 1 + rnd.nextInt(Math.max(1, sala.largura() - 2));
            int z = sala.z1() + 3 + rnd.nextInt(Math.max(1, sala.comprimento() - 6));
            int altura = 2 + rnd.nextInt(3);
            for (int y = ctx.pisoY(); y < ctx.pisoY() + altura; y++) {
                molde.por(x, y, z, Material.COBBLESTONE);
            }
        }

        // Luz fraca: da para ver, mas o canto do fundo continua escuro.
        for (int z = sala.z1() + 2; z <= sala.z2(); z += 7) {
            molde.por(sala.x1(), ctx.tetoY(), z, Blocos.lanternaAlmaPendurada());
            molde.por(sala.x2(), ctx.tetoY(), z, Blocos.lanternaAlmaPendurada());
        }
    }

    @Override
    public void aoEntrar(Ambiente amb) {
        fecharGrade(amb, Material.IRON_BARS);

        int quantos = amb.dificuldade().entre(2, 9);
        Random rnd = new Random();
        List<UUID> guardioes = new ArrayList<>(quantos);
        Caixa sala = amb.interior();

        for (int i = 0; i < quantos; i++) {
            EntityType tipo = amb.dificuldade().nivel() >= Dificuldade.DIFICIL.nivel()
                    && rnd.nextBoolean()
                    ? PESADOS[rnd.nextInt(PESADOS.length)]
                    : COMUNS[rnd.nextInt(COMUNS.length)];

            // Longe da porta de entrada, para ninguem levar dano no primeiro passo.
            Location onde = new Location(amb.mundo(),
                    sala.x1() + 1 + rnd.nextInt(Math.max(1, sala.largura() - 2)) + 0.5,
                    sala.y1(),
                    sala.z1() + sala.comprimento() / 2 + rnd.nextInt(Math.max(1, sala.comprimento() / 2)) + 0.5);

            Entity entidade = amb.mundo().spawnEntity(onde, tipo);
            if (entidade instanceof Mob mob) {
                mob.setRemoveWhenFarAway(false);
                mob.setPersistent(true);
                mob.customName(Component.text("Guardiao", NamedTextColor.RED));
                if (mob.getEquipment() != null) {
                    // Sem isto o esqueleto larga o arco dele e a sala vira loja de equipamento.
                    mob.getEquipment().setItemInMainHandDropChance(0f);
                    mob.getEquipment().setItemInOffHandDropChance(0f);
                    mob.getEquipment().setHelmetDropChance(0f);
                }
                mob.setTarget(amb.jogador());
                guardioes.add(mob.getUniqueId());
            }
        }

        amb.corrida().estado().put(amb.chaveEstado("guardioes"), guardioes);
        amb.jogador().sendMessage(Component.text("Derrote os " + guardioes.size()
                + " guardioes para a grade abrir.", NamedTextColor.RED));
    }

    @Override
    public void tick(Ambiente amb) {
        if (amb.tick() % 10 != 0) {
            return;
        }
        Object bruto = amb.corrida().estado().get(amb.chaveEstado("guardioes"));
        if (!(bruto instanceof List<?> lista) || lista.isEmpty()) {
            return;
        }
        for (Object item : lista) {
            Entity entidade = Bukkit.getEntity((UUID) item);
            if (entidade instanceof LivingEntity vivo && !vivo.isDead()) {
                return;
            }
        }
        amb.corrida().estado().remove(amb.chaveEstado("guardioes"));
        fecharGrade(amb, Material.AIR);
        amb.mundo().playSound(amb.jogador().getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1.2f);
        amb.jogador().sendMessage(Component.text("A grade abriu.", NamedTextColor.GREEN));
    }

    @Override
    public void aoSair(Ambiente amb) {
        Object bruto = amb.corrida().estado().remove(amb.chaveEstado("guardioes"));
        if (bruto instanceof List<?> lista) {
            for (Object item : lista) {
                Entity entidade = Bukkit.getEntity((UUID) item);
                if (entidade != null) {
                    entidade.remove();
                }
            }
        }
        fecharGrade(amb, Material.IRON_BARS);
    }

    private void fecharGrade(Ambiente amb, Material material) {
        Caixa porta = amb.arena().portaAntesDe(amb.indice() + 1);
        for (int x = porta.x1(); x <= porta.x2(); x++) {
            for (int y = porta.y1(); y <= porta.y2(); y++) {
                for (int z = porta.z1(); z <= porta.z2(); z++) {
                    amb.mundo().getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }
}
