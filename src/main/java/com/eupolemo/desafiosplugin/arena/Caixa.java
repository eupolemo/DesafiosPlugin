package com.eupolemo.desafiosplugin.arena;

import org.bukkit.Location;

/**
 * Uma caixa de blocos com os dois cantos inclusive. Toda a geometria da arena — sala, piso,
 * vao de porta, poco — e descrita com uma destas.
 */
public record Caixa(int x1, int y1, int z1, int x2, int y2, int z2) {

    public Caixa {
        if (x1 > x2 || y1 > y2 || z1 > z2) {
            throw new IllegalArgumentException("Caixa invertida: " + x1 + "," + y1 + "," + z1
                    + " ate " + x2 + "," + y2 + "," + z2);
        }
    }

    public int largura() {
        return x2 - x1 + 1;
    }

    public int altura() {
        return y2 - y1 + 1;
    }

    public int comprimento() {
        return z2 - z1 + 1;
    }

    public int centroX() {
        return x1 + largura() / 2;
    }

    public int centroZ() {
        return z1 + comprimento() / 2;
    }

    public long volume() {
        return (long) largura() * altura() * comprimento();
    }

    /** @return a caixa crescida em {@code n} blocos para cada lado */
    public Caixa expandir(int n) {
        return new Caixa(x1 - n, y1 - n, z1 - n, x2 + n, y2 + n, z2 + n);
    }

    public boolean contem(int x, int y, int z) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    /** Ignora o mundo: quem chama ja sabe em qual mundo a arena esta. */
    public boolean contem(Location loc) {
        return contem(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
