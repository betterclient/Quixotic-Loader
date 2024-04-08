package io.github.betterclient.quixotic.test;

import io.github.betterclient.quixotic.QuixoticApplication;
import io.github.betterclient.quixotic.QuixoticClassLoader;
import io.github.betterclient.quixotic.Side;

import java.util.ArrayList;
import java.util.List;

public class TestApplication implements QuixoticApplication {

    @Override
    public String getApplicationName() {
        return "Test";
    }

    @Override
    public String getApplicationVersion() {
        return "unknown";
    }

    @Override
    public String getMainClass() {
        return "io.github.betterclient.quixotic.test.TestMain";
    }

    @Override
    public void loadApplicationManager(QuixoticClassLoader classLoader) {}

    @Override
    public List<String> getMixinConfigurations() {
        return List.of("test.mixins.json");
    }

    @Override
    public Side getSide() {
        return Side.CLIENT;
    }
}