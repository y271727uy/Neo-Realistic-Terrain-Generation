package com.y271727uy.rtg.data;

import com.tterrag.registrate.AbstractRegistrate;

public class AequoraRegistrate extends AbstractRegistrate<AequoraRegistrate> {
    public static AequoraRegistrate create(String modid) {
        AequoraRegistrate registrate = new AequoraRegistrate(modid);
        registrate.registerEventListeners(registrate.getModEventBus());
        return registrate;
    }

    protected AequoraRegistrate(String modid) {
        super(modid);
    }
}
