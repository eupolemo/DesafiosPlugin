package com.eupolemo.desafiosplugin.arena;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Guarda em disco o que cada arena sobrescreveu, para que o {@code /desafio remover} continue
 * funcionando depois de o servidor reiniciar. Uma arena de sete salas passa por cima de dezenas
 * de milhares de blocos; sem isto, um restart deixaria tudo orfao no mundo.
 *
 * <p>Formato de {@code blocos/<chave>.bin.gz}: cabecalho, UUID do mundo, uma paleta com os
 * estados distintos de bloco e, para cada posicao, o indice na paleta. A paleta importa — uma
 * arena costuma sobrescrever ar e pedra e pouco mais.
 */
public final class RegistroBlocos {

    private static final byte[] ASSINATURA = {'D', 'S', 'A', 'F'};
    private static final int VERSAO = 1;
    private static final String SUFIXO = ".bin.gz";
    /** Chaves viram nome de arquivo; qualquer coisa fora disso e recusada em vez de sanitizada. */
    private static final Pattern CHAVE_VALIDA = Pattern.compile("[a-z0-9._-]{1,64}");

    /** Os blocos de uma arena lidos do disco. */
    public record Salvo(UUID mundo, List<BlocoSalvo> blocos) {
    }

    private final Path pasta;
    private final Logger log;

    public RegistroBlocos(Path pasta, Logger log) {
        this.pasta = pasta;
        this.log = log;
    }

    public static boolean chaveValida(String chave) {
        return CHAVE_VALIDA.matcher(chave).matches();
    }

    public boolean existe(String chave) {
        return Files.isRegularFile(arquivo(chave));
    }

    /**
     * Grava os blocos originais. Escreve num arquivo temporario e so entao substitui o
     * definitivo: se o servidor morrer no meio da escrita, o arquivo anterior continua integro.
     */
    public void salvar(String chave, UUID mundo, List<BlocoSalvo> blocos) {
        if (!chaveValida(chave)) {
            log.warning("Arena '" + chave + "' nao foi salva: a chave nao serve como nome de "
                    + "arquivo. Use apenas letras minusculas, numeros, ponto, hifen e sublinhado.");
            return;
        }
        if (blocos.isEmpty()) {
            apagar(chave);
            return;
        }

        Map<String, Integer> paleta = new LinkedHashMap<>();
        for (BlocoSalvo bloco : blocos) {
            paleta.computeIfAbsent(bloco.dado().getAsString(), k -> paleta.size());
        }
        if (paleta.size() > 65535) {
            // O indice cabe em 16 bits. Nenhuma arena real chega perto disso, mas falhar alto e
            // melhor do que gravar lixo.
            log.severe("Arena '" + chave + "' tem " + paleta.size() + " estados de bloco "
                    + "distintos, acima do limite de 65535. Nao foi salva.");
            return;
        }

        Path destino = arquivo(chave);
        Path temporario = destino.resolveSibling(destino.getFileName() + ".tmp");
        try {
            Files.createDirectories(pasta);
            try (OutputStream saida = Files.newOutputStream(temporario);
                 DataOutputStream dados = new DataOutputStream(
                         new BufferedOutputStream(new GZIPOutputStream(saida)))) {

                dados.write(ASSINATURA);
                dados.writeInt(VERSAO);
                dados.writeLong(mundo.getMostSignificantBits());
                dados.writeLong(mundo.getLeastSignificantBits());

                dados.writeInt(paleta.size());
                for (String estado : paleta.keySet()) {
                    dados.writeUTF(estado);
                }

                dados.writeInt(blocos.size());
                for (BlocoSalvo bloco : blocos) {
                    dados.writeInt(bloco.x());
                    dados.writeInt(bloco.y());
                    dados.writeInt(bloco.z());
                    dados.writeShort(paleta.get(bloco.dado().getAsString()));
                }
            }
            Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.severe("Nao consegui salvar os blocos da arena '" + chave + "': " + e.getMessage()
                    + ". Ela nao vai sobreviver a um restart.");
            try {
                Files.deleteIfExists(temporario);
            } catch (IOException ignorado) {
                // O temporario orfao e inofensivo; nao vale mascarar o erro de cima.
            }
        }
    }

    /** @return os blocos salvos, ou null se nao existirem ou o arquivo estiver corrompido */
    public Salvo carregar(String chave) {
        Path arquivo = arquivo(chave);
        if (!Files.isRegularFile(arquivo)) {
            return null;
        }
        try (InputStream entrada = Files.newInputStream(arquivo);
             DataInputStream dados = new DataInputStream(
                     new BufferedInputStream(new GZIPInputStream(entrada)))) {

            byte[] assinatura = new byte[ASSINATURA.length];
            dados.readFully(assinatura);
            for (int i = 0; i < ASSINATURA.length; i++) {
                if (assinatura[i] != ASSINATURA[i]) {
                    log.warning("Arena '" + chave + "': arquivo nao e do DesafiosPlugin.");
                    return null;
                }
            }
            int versao = dados.readInt();
            if (versao != VERSAO) {
                log.warning("Arena '" + chave + "': formato versao " + versao + ", esperado "
                        + VERSAO + ". Ignorado.");
                return null;
            }

            UUID mundo = new UUID(dados.readLong(), dados.readLong());

            int tamanhoPaleta = dados.readInt();
            BlockData[] paleta = new BlockData[tamanhoPaleta];
            for (int i = 0; i < tamanhoPaleta; i++) {
                String estado = dados.readUTF();
                try {
                    paleta[i] = Bukkit.createBlockData(estado);
                } catch (IllegalArgumentException e) {
                    // Bloco que sumiu numa atualizacao do Minecraft. Perder uma entrada e melhor
                    // do que perder a arena inteira.
                    log.warning("Arena '" + chave + "': bloco desconhecido '" + estado
                            + "'. Essas posicoes ficam como estao.");
                    paleta[i] = null;
                }
            }

            int total = dados.readInt();
            List<BlocoSalvo> blocos = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                int x = dados.readInt();
                int y = dados.readInt();
                int z = dados.readInt();
                BlockData dado = paleta[dados.readUnsignedShort()];
                if (dado != null) {
                    blocos.add(new BlocoSalvo(x, y, z, dado));
                }
            }
            return new Salvo(mundo, blocos);

        } catch (IOException | RuntimeException e) {
            log.severe("Arena '" + chave + "' esta ilegivel: " + e.getMessage()
                    + ". Os blocos dela ficam no mundo.");
            return null;
        }
    }

    public void apagar(String chave) {
        try {
            Files.deleteIfExists(arquivo(chave));
        } catch (IOException e) {
            log.warning("Nao consegui apagar os blocos da arena '" + chave + "': " + e.getMessage());
        }
    }

    private Path arquivo(String chave) {
        return pasta.resolve(chave.toLowerCase(Locale.ROOT) + SUFIXO);
    }
}
