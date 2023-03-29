package io.github.betterclient.quixotic.app;

import io.github.betterclient.quixotic.QuixoticApplication;
import io.github.betterclient.quixotic.QuixoticClassLoader;

import java.util.ArrayList;
import java.util.List;

public class MinecraftVanillaApplication implements QuixoticApplication {

    @Override
    public String getApplicationName() {
        return "Minecraft Vanilla";
    }

    @Override
    public String getApplicationVersion() {
        return "unknown";
    }

    @Override
    public String getMainClass() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public void loadApplicationManager(QuixoticClassLoader classLoader) {}

    @Override
    public List<String> getMixinConfigurations() {
        return new ArrayList<>();
    }
}