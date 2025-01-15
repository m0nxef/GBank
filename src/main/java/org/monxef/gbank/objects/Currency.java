package org.monxef.gbank.objects;

import lombok.Data;
import org.bukkit.Material;

@Data
public class Currency {
    private final String id;
    private final String displayName;
    private final Material displayMaterial;
    private final String symbol;
    private final int slot;

    public Currency(String id, String displayName, Material displayMaterial, String symbol,int slot) {
        this.id = id;
        this.displayName = displayName;
        this.displayMaterial = displayMaterial;
        this.symbol = symbol;
        this.slot = slot;
    }
}