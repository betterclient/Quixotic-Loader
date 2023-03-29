package io.github.betterclient.quixotic;

import java.util.List;

public interface QuixoticApplication {
    String getApplicationName();
    String getApplicationVersion();
    String getMainClass();
    void loadApplicationManager(QuixoticClassLoader classLoader);
    List<String> getMixinConfigurations();
}
