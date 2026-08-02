package gg.vape.account;

import gg.vape.mapping.mappings.MSession;
import gg.vape.wrapper.Wrapper;
import gg.vape.wrapper.impl.ForgeVersion;
import java.util.UUID;

public class MinecraftSessionWrapper
extends Wrapper {

    public MinecraftSessionWrapper(Object session) {
        super(session);
    }

    public String getUsername() {
        return MinecraftSessionWrapper.vapeInstance.getMappings().hw.s(this.I);
    }

    public UUID getProfileId() {
        if (ForgeVersion.MC_1_20_6.d()) {
            return (UUID)MinecraftSessionWrapper.vapeInstance.getMappings().hw.V(this.I);
        }
        String rawProfileId = (String)MinecraftSessionWrapper.vapeInstance.getMappings().hw.V(this.I);
        if (rawProfileId == null || rawProfileId.isEmpty()) {
            return UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
        return UUID.fromString(rawProfileId.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

}
