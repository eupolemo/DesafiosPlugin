package com.eupolemo.desafiosplugin.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;

/**
 * Uma arena construida no mundo: um corredor de salas encadeadas no sentido +Z. A sala 0 e a
 * entrada, as do meio sao as etapas sorteadas e a ultima e a do premio.
 *
 * <p>Guarda so a origem, as medidas e a lista de etapas — toda a geometria sai daqui por conta,
 * entao o arquivo de arenas fica pequeno e o layout nunca sai de sincronia com o que foi
 * construido. A {@code seed} deixa a mesma arena ser reconstruida igual.
 */
public record Arena(String id, String desafio, UUID mundo, long seed,
                    int x0, int y0, int z0,
                    int largura, int altura, int comprimento,
                    List<String> tipos) {

    /** Quanto um poco de parkour ou de ponte afunda abaixo do piso das salas normais. */
    public static final int PROFUNDIDADE_POCO = 6;
    /** Profundidade do portico de entrada, construido do lado de fora da parede da frente. */
    public static final int PORTICO_FUNDO = 3;
    /** Largura do vao de porta entre duas salas. Sempre impar, para centralizar. */
    public static final int LARGURA_PORTA = 3;
    public static final int ALTURA_PORTA = 3;

    public int totalSalas() {
        return tipos.size() + 2;
    }

    /** @return o gerador da sala, ou "entrada"/"premio" nas duas pontas */
    public String tipoDaSala(int indice) {
        if (indice <= 0) {
            return "entrada";
        }
        if (indice > tipos.size()) {
            return "premio";
        }
        return tipos.get(indice - 1);
    }

    public int indicePremio() {
        return totalSalas() - 1;
    }

    public int zInicio(int indice) {
        return z0 + indice * (comprimento + 1);
    }

    public int zFim(int indice) {
        return zInicio(indice) + comprimento - 1;
    }

    /** O espaco jogavel de uma sala, sem as paredes. */
    public Caixa interior(int indice) {
        return new Caixa(x0, y0, zInicio(indice), x0 + largura - 1, y0 + altura - 1, zFim(indice));
    }

    /** Coluna central: onde ficam as portas, o spawn e os checkpoints. */
    public int centroX() {
        return x0 + largura / 2;
    }

    /** Tudo que a construcao encosta, casca e pocos inclusive. */
    public Caixa caixaTotal() {
        return new Caixa(x0 - 1, y0 - PROFUNDIDADE_POCO - 1, z0 - 1,
                x0 + largura, y0 + altura, zFim(totalSalas() - 1) + 1);
    }

    /** O vao que liga a sala {@code indice} a anterior. Na sala 0 e a porta de fora. */
    public Caixa portaAntesDe(int indice) {
        int z = zInicio(indice) - 1;
        int meio = centroX();
        int lado = LARGURA_PORTA / 2;
        return new Caixa(meio - lado, y0, z, meio + lado, y0 + ALTURA_PORTA - 1, z);
    }

    /**
     * O portico: uma alcova aberta na frente da arena, fora do corredor, com a placa, o botao e a
     * placa de pressao que dao entrada no desafio sem comando nenhum.
     */
    public Caixa portico() {
        int meio = centroX();
        return new Caixa(meio - 2, y0 - 1, z0 - 1 - PORTICO_FUNDO, meio + 2, y0 + 3, z0 - 2);
    }

    /** O botao de entrada, grudado na parede da frente. */
    public int[] posicaoBotao() {
        return new int[]{centroX() + 1, y0 + 1, z0 - 2};
    }

    /** A placa de pressao de entrada, no chao do portico. */
    public int[] posicaoPlaca() {
        return new int[]{centroX(), y0, z0 - 3};
    }

    /** Onde o jogador nasce ao entrar na arena. */
    public Location spawn(World mundo) {
        return checkpoint(mundo, 0);
    }

    /** Logo depois da porta da sala, virado para o fim do corredor. */
    public Location checkpoint(World mundo, int indice) {
        return new Location(mundo, centroX() + 0.5, y0, zInicio(indice) + 1.5, 0f, 0f);
    }

    /**
     * @return o indice da sala que contem a posicao, ou -1 se ela esta fora da arena. As paredes
     *         divisorias contam como a sala seguinte, senao passar pela porta ficaria num limbo.
     */
    public int salaEm(int x, int y, int z) {
        Caixa total = caixaTotal();
        if (!total.contem(x, y, z)) {
            return -1;
        }
        int relativo = z - z0;
        if (relativo < 0) {
            return 0;
        }
        int indice = relativo / (comprimento + 1);
        return Math.min(indice, totalSalas() - 1);
    }

    public int salaEm(Location loc) {
        return salaEm(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * O portico fica fora da {@link #caixaTotal()} de proposito: ele e area de entrada, nao sala.
     * Entra aqui, na protecao, para ninguem arrancar o botao; e fica fora do calculo de salas,
     * porque quem esta la ainda nao comecou a correr.
     */
    public boolean contem(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getUID().equals(mundo)) {
            return false;
        }
        return caixaTotal().contem(loc) || portico().contem(loc);
    }

    /**
     * Altura minima aceitavel: abaixo disso o jogador caiu num poco e volta ao checkpoint.
     *
     * <p>Fica cinco blocos abaixo do nivel das portas, e nao um ou dois, porque o parkour desce
     * plataformas ate tres blocos: com o limite colado no piso, quem pulasse para a plataforma
     * mais baixa seria devolvido como se tivesse caido. Ainda sobram dois blocos ate o fundo do
     * poco, entao a queda de verdade e detectada antes de o jogador encostar la embaixo.
     */
    public int alturaDeQueda() {
        return y0 - 5;
    }
}
