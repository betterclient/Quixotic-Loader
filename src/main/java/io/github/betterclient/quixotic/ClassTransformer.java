package io.github.betterclient.quixotic;

public interface ClassTransformer {
    byte[] transform(String name, byte[] unTransformedClass);
}
