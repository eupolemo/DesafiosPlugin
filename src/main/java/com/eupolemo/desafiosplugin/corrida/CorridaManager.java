package com.eupolemo.desafiosplugin.corrida;

import com.eupolemo.desafiosplugin.DesafiosPlugin;
import com.eupolemo.desafiosplugin.arena.Arena;
import com.eupolemo.desafiosplugin.desafio.Desafio;
import com.eupolemo.desafiosplugin.desafio.Premio;
import com.eupolemo.desafiosplugin.gerador.Ambiente;
import com.eupolemo.desafiosplugin.gerador.Gerador;
import com.eupolemo.desafiosplugin.gerador.Geradores;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * O jogo em si: quem esta correndo, em que sala, ha quanto tempo, e o que acontece quando cai,
 * queima, chega ao fim ou estoura o tempo.
 *
 * <p>Tudo passa por um unico tique a cada dois ticks do servidor. Um {@code PlayerMoveEvent}
 * daria resposta mais fina, mas dispara varias vezes por tick por jogador e obrigaria a varrer as
 * arenas em cada um; a cada dois ticks o atraso e de um decimo de segundo, que ninguem sente, e o
 * custo nao depende de quanto o jogador se mexe.
 */
public final class CorridaManager {

    /** Por que uma corrida acabou. So a vitoria entrega premio, e so ela entra no ranking. */
    public enum Fim {
        VITORIA, DESISTIU, TEMPO, SAIU_DA_ARENA, MORREU, SERVIDOR
    }

    private static final long PERIODO_EM_TICKS = 2L;

    private final DesafiosPlugin plugin;
    private final Recordes recordes;
    private final Map<UUID, Corrida> corridas = new HashMap<>();
    private BukkitTask tarefa;
    private int tique;

    public CorridaManager(DesafiosPlugin plugin, Recordes recordes) {
        this.plugin = plugin;
        this.recordes = recordes;
    }

    public void ligar() {
        tarefa = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tique,
                PERIODO_EM_TICKS, PERIODO_EM_TICKS);
    }

    /** Encerra tudo que esta em andamento. Sem isso, um restart deixaria jogador preso em aventura. */
    public void desligar() {
        if (tarefa != null) {
            tarefa.cancel();
        }
        for (UUID id : new ArrayList<>(corridas.keySet())) {
            encerrar(id, Fim.SERVIDOR);
        }
    }

    public Corrida de(Player jogador) {
        return corridas.get(jogador.getUniqueId());
    }

    public boolean correndo(Player jogador) {
        return corridas.containsKey(jogador.getUniqueId());
    }

    /**
     * Faz as checagens de entrada e comeca a corrida. E o caminho unico: o comando e o botao do
     * portico chamam este metodo, para as duas portas de entrada seguirem as mesmas regras.
     *
     * @return false se a corrida nao comecou; a explicacao ja foi para o jogador
     */
    public boolean tentarIniciar(Player jogador, Desafio desafio, Arena arena) {
        if (correndo(jogador)) {
            jogador.sendMessage(Component.text("Voce ja esta num desafio. Use /desafio sair para "
                    + "abandonar.", NamedTextColor.YELLOW));
            return false;
        }
        // Uma corrida por arena. As salas guardam estado no proprio mundo — a grade da sala dos
        // guardioes, os mobs vivos —, entao dois corredores ao mesmo tempo abririam a grade um do
        // outro. Para correr em paralelo, construa a arena de outro desafio.
        for (Corrida outra : corridas.values()) {
            if (outra.arena().id().equals(arena.id())) {
                Player quem = plugin.getServer().getPlayer(outra.jogador());
                jogador.sendMessage(Component.text((quem != null ? quem.getName() : "Alguem")
                        + " esta correndo essa arena agora. Espere terminar.",
                        NamedTextColor.YELLOW));
                return false;
            }
        }
        iniciar(jogador, desafio, arena);
        return correndo(jogador);
    }

    public void iniciar(Player jogador, Desafio desafio, Arena arena) {
        World mundo = plugin.getServer().getWorld(arena.mundo());
        if (mundo == null) {
            jogador.sendMessage(Component.text("O mundo da arena nao esta carregado.",
                    NamedTextColor.RED));
            return;
        }

        BossBar barra = BossBar.bossBar(Component.text(desafio.nome()), 0f,
                BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        Corrida corrida = new Corrida(jogador.getUniqueId(), desafio, arena,
                jogador.getLocation(), jogador.getGameMode(), barra,
                List.copyOf(jogador.getActivePotionEffects()), jogador.getAllowFlight());
        corridas.put(jogador.getUniqueId(), corrida);

        jogador.teleport(arena.spawn(mundo));
        aplicarModoDeJogo(jogador);
        desarmar(jogador);
        jogador.showBossBar(barra);
        jogador.showTitle(Title.title(
                Component.text(desafio.nome(), desafio.dificuldade().cor()),
                Component.text(desafio.etapas() + " etapas ate o premio"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2),
                        Duration.ofMillis(500))));
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
        atualizarBarra(corrida);
    }

    /**
     * Criativo e espectador valem por todos os buffs juntos: voa, atravessa e nao toma dano. Por
     * isso quem esta num deles cai para sobrevivencia mesmo com {@code arena.modo-aventura}
     * desligado — a opcao escolhe entre aventura e sobrevivencia, nao entre correr e sobrevoar.
     */
    private void aplicarModoDeJogo(Player jogador) {
        if (plugin.getConfig().getBoolean("arena.modo-aventura", true)) {
            jogador.setGameMode(GameMode.ADVENTURE);
            return;
        }
        if (jogador.getGameMode() == GameMode.CREATIVE
                || jogador.getGameMode() == GameMode.SPECTATOR) {
            jogador.setGameMode(GameMode.SURVIVAL);
        }
    }

    /**
     * Tira tudo que faria o jogador pular o obstaculo em vez de atravessa-lo: efeito de pocao,
     * voo, elitro e montaria. Os efeitos sao devolvidos no fim da corrida — eles foram
     * emprestados ao desafio, nao confiscados.
     *
     * <p>Vem depois de trocar o modo de jogo: trocar o modo remete a permissao de voo, entao
     * fazer na ordem inversa devolveria o voo que acabou de ser tirado.
     */
    private void desarmar(Player jogador) {
        for (PotionEffect efeito : List.copyOf(jogador.getActivePotionEffects())) {
            jogador.removePotionEffect(efeito.getType());
        }
        jogador.setFlying(false);
        jogador.setAllowFlight(false);
        jogador.setGliding(false);
        if (jogador.getVehicle() != null) {
            jogador.leaveVehicle();
        }
        jogador.setFireTicks(0);
        jogador.setFallDistance(0f);
    }

    /** Devolve o que {@link #desarmar} tirou. */
    private void rearmar(Player jogador, Corrida corrida) {
        jogador.setGameMode(corrida.modoAnterior());
        jogador.setAllowFlight(corrida.permitiaVoo());
        for (PotionEffect efeito : corrida.efeitosGuardados()) {
            jogador.addPotionEffect(efeito);
        }
    }

    /** @return false se o jogador nao estava correndo */
    public boolean encerrar(UUID id, Fim motivo) {
        Corrida corrida = corridas.remove(id);
        if (corrida == null) {
            return false;
        }
        Player jogador = plugin.getServer().getPlayer(id);
        World mundo = plugin.getServer().getWorld(corrida.arena().mundo());

        if (jogador != null && mundo != null) {
            Gerador gerador = geradorDaSala(corrida);
            if (gerador != null) {
                gerador.aoSair(ambiente(corrida, jogador, mundo, gerador));
            }
        }
        if (jogador == null) {
            return true;
        }

        jogador.hideBossBar(corrida.barra());
        rearmar(jogador, corrida);
        if (motivo != Fim.MORREU && corrida.voltarPara() != null) {
            // Depois de morrer de verdade o jogador ja vai para o ponto de renascimento; um
            // teleporte por cima disso o largaria dentro da arena com a corrida encerrada.
            jogador.teleport(corrida.voltarPara());
        }

        switch (motivo) {
            case DESISTIU -> jogador.sendMessage(Component.text("Voce abandonou "
                    + corrida.desafio().nome() + ".", NamedTextColor.YELLOW));
            case TEMPO -> jogador.sendMessage(Component.text("O tempo acabou em "
                    + corrida.desafio().nome() + ". Sala " + corrida.sala() + " de "
                    + (corrida.arena().totalSalas() - 1) + ".", NamedTextColor.RED));
            case SAIU_DA_ARENA -> jogador.sendMessage(Component.text(
                    "Voce saiu da arena; a corrida foi encerrada.", NamedTextColor.YELLOW));
            case MORREU -> jogador.sendMessage(Component.text("Voce morreu em "
                    + corrida.desafio().nome() + ".", NamedTextColor.RED));
            case SERVIDOR -> jogador.sendMessage(Component.text(
                    "O servidor encerrou a sua corrida.", NamedTextColor.GRAY));
            case VITORIA -> {
                // A mensagem de vitoria ja saiu com o premio, em vencer().
            }
        }
        return true;
    }

    public boolean encerrar(Player jogador, Fim motivo) {
        return encerrar(jogador.getUniqueId(), motivo);
    }

    /** Devolve o jogador ao inicio da sala em que esta, contando uma queda. */
    public void devolverAoCheckpoint(Corrida corrida, Player jogador, Component motivo) {
        World mundo = plugin.getServer().getWorld(corrida.arena().mundo());
        if (mundo == null) {
            return;
        }
        corrida.contarMorte();
        jogador.setFireTicks(0);
        jogador.setFallDistance(0f);
        jogador.setVelocity(jogador.getVelocity().zero());
        jogador.teleport(corrida.arena().checkpoint(mundo, corrida.sala()));
        var maximo = jogador.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        jogador.setHealth(Math.min(jogador.getHealth() + 6.0,
                maximo != null ? maximo.getValue() : 20.0));
        jogador.sendActionBar(motivo.color(NamedTextColor.RED));
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
    }

    private void tique() {
        tique++;
        for (UUID id : new ArrayList<>(corridas.keySet())) {
            Corrida corrida = corridas.get(id);
            Player jogador = plugin.getServer().getPlayer(id);
            if (corrida == null) {
                continue;
            }
            if (jogador == null || !jogador.isOnline()) {
                encerrar(id, Fim.SERVIDOR);
                continue;
            }
            World mundo = plugin.getServer().getWorld(corrida.arena().mundo());
            if (mundo == null) {
                encerrar(id, Fim.SERVIDOR);
                continue;
            }

            Location onde = jogador.getLocation();
            if (!corrida.arena().contem(onde)) {
                encerrar(id, Fim.SAIU_DA_ARENA);
                continue;
            }
            if (onde.getY() < corrida.arena().alturaDeQueda()) {
                devolverAoCheckpoint(corrida, jogador, Component.text("Voce caiu."));
                continue;
            }

            int sala = corrida.arena().salaEm(onde);
            if (sala > corrida.sala()) {
                avancar(corrida, jogador, mundo, sala);
            }
            if (corrida.sala() >= corrida.arena().indicePremio()) {
                vencer(corrida, jogador);
                continue;
            }

            Gerador gerador = geradorDaSala(corrida);
            if (gerador != null) {
                gerador.tick(ambiente(corrida, jogador, mundo, gerador));
            }

            if (corrida.desafio().temTempoLimite()) {
                long faltam = corrida.segundosRestantes();
                if (faltam <= 0) {
                    encerrar(id, Fim.TEMPO);
                    continue;
                }
                int aviso = plugin.getConfig().getInt("corrida.aviso-tempo-segundos", 30);
                if (faltam <= aviso && !corrida.avisouTempo()) {
                    corrida.marcarAvisoDeTempo();
                    jogador.showTitle(Title.title(Component.empty(),
                            Component.text(faltam + " segundos!", NamedTextColor.RED)));
                    jogador.playSound(onde, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                }
            }

            if (tique % 5 == 0) {
                atualizarBarra(corrida);
                // Rede de seguranca: algo fora daqui pode ter devolvido voo ou efeito no meio da
                // corrida — um item de outro plugin, um beacon, uma nuvem persistente. O evento
                // que barra isso pode nao cobrir todos, a varredura cobre.
                if (jogador.getAllowFlight()) {
                    jogador.setFlying(false);
                    jogador.setAllowFlight(false);
                }
                for (org.bukkit.potion.PotionEffect efeito
                        : List.copyOf(jogador.getActivePotionEffects())) {
                    jogador.removePotionEffect(efeito.getType());
                }
            }
        }
    }

    private void avancar(Corrida corrida, Player jogador, World mundo, int novaSala) {
        Gerador anterior = geradorDaSala(corrida);
        if (anterior != null) {
            anterior.aoSair(ambiente(corrida, jogador, mundo, anterior));
        }
        corrida.sala(novaSala);

        if (novaSala >= corrida.arena().indicePremio()) {
            return;
        }
        Gerador atual = geradorDaSala(corrida);
        String nome = atual != null ? atual.nome() : corrida.arena().tipoDaSala(novaSala);
        jogador.sendMessage(Component.text("Etapa " + novaSala + "/"
                + (corrida.arena().totalSalas() - 2) + " — " + nome, NamedTextColor.AQUA));
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.4f);
        if (atual != null) {
            atual.aoEntrar(ambiente(corrida, jogador, mundo, atual));
        }
    }

    private void vencer(Corrida corrida, Player jogador) {
        long tempo = corrida.tempoMs();
        boolean recorde = recordes.registrar(corrida.desafio().id(), jogador.getUniqueId(),
                jogador.getName(), tempo, corrida.mortes());

        // Premio antes do encerramento: encerrar teleporta de volta, e a entrega tem de
        // acontecer com o jogador ainda de posse da corrida.
        entregarPremio(corrida.desafio().premio(), jogador);
        encerrar(jogador, Fim.VITORIA);

        jogador.showTitle(Title.title(
                Component.text("Desafio vencido!", NamedTextColor.GOLD),
                Component.text(Corrida.formatarTempo(tempo) + " · " + corrida.mortes() + " queda(s)"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3),
                        Duration.ofMillis(600))));
        jogador.sendMessage(Component.text(corrida.desafio().nome() + " — "
                + Corrida.formatarTempo(tempo) + ", " + corrida.mortes() + " queda(s).",
                NamedTextColor.GOLD));
        if (recorde) {
            int posicao = recordes.posicao(corrida.desafio().id(), jogador.getUniqueId());
            jogador.sendMessage(Component.text("Novo recorde seu. Posicao " + posicao
                    + " no ranking.", NamedTextColor.GREEN));
        }
        jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        fogos(jogador);

        if (plugin.getConfig().getBoolean("corrida.anunciar-vitoria", true)) {
            plugin.getServer().broadcast(Component.text(jogador.getName() + " venceu "
                    + corrida.desafio().nome() + " em " + Corrida.formatarTempo(tempo) + ".",
                    NamedTextColor.GOLD));
        }
    }

    private void entregarPremio(Premio premio, Player jogador) {
        if (!premio.mensagem().isBlank()) {
            jogador.sendMessage(Component.text(premio.mensagem(), NamedTextColor.YELLOW));
        }
        if (premio.xp() > 0) {
            jogador.giveExp(premio.xp());
        }
        for (ItemStack item : premio.itens()) {
            Map<Integer, ItemStack> sobra = jogador.getInventory().addItem(item.clone());
            // Inventario cheio nao pode custar o premio: o que nao coube cai aos pes.
            for (ItemStack resto : sobra.values()) {
                jogador.getWorld().dropItemNaturally(jogador.getLocation(), resto);
            }
        }
        for (String comando : premio.comandos()) {
            String pronto = comando.replace("%jogador%", jogador.getName());
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pronto);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Comando de premio falhou: '" + pronto + "': "
                        + e.getMessage());
            }
        }
    }

    private void fogos(Player jogador) {
        Random rnd = new Random();
        for (int i = 0; i < 3; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!jogador.isOnline()) {
                    return;
                }
                jogador.getWorld().spawn(jogador.getLocation(), Firework.class, foguete -> {
                    FireworkMeta meta = foguete.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(Color.fromRGB(rnd.nextInt(0xFFFFFF)))
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .withFlicker()
                            .build());
                    meta.setPower(1);
                    foguete.setFireworkMeta(meta);
                });
            }, i * 12L);
        }
    }

    private void atualizarBarra(Corrida corrida) {
        Arena arena = corrida.arena();
        int etapas = Math.max(1, arena.totalSalas() - 2);
        float progresso = Math.clamp(corrida.sala() / (float) etapas, 0f, 1f);
        String tempo = corrida.desafio().temTempoLimite()
                ? Corrida.formatarTempo(Math.max(0, corrida.segundosRestantes()) * 1000)
                : Corrida.formatarTempo(corrida.tempoMs());

        corrida.barra().name(Component.text(corrida.desafio().nome() + " · etapa "
                + Math.min(corrida.sala(), etapas) + "/" + etapas + " · " + tempo));
        corrida.barra().progress(progresso);
        corrida.barra().color(corrida.desafio().temTempoLimite() && corrida.segundosRestantes() < 30
                ? BossBar.Color.RED : BossBar.Color.YELLOW);
    }

    private Gerador geradorDaSala(Corrida corrida) {
        return Geradores.porId(corrida.arena().tipoDaSala(corrida.sala()));
    }

    private Ambiente ambiente(Corrida corrida, Player jogador, World mundo, Gerador gerador) {
        int indice = corrida.sala();
        return new Ambiente(plugin, mundo, corrida.arena(), indice,
                corrida.arena().interior(indice), corrida.desafio().dificuldade(), corrida,
                jogador, tique, new Random(corrida.arena().seed() * 31L + indice));
    }

    public List<Corrida> emAndamento() {
        return List.copyOf(corridas.values());
    }
}
