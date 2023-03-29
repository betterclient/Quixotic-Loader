package io.github.betterclient.quixotic.mixin;

import io.github.betterclient.quixotic.ClassTransformer;
import org.spongepowered.asm.service.ILegacyClassTransformer;
import org.spongepowered.asm.service.MixinService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class Proxy implements ClassTransformer, ILegacyClassTransformer {
    private static List<Proxy> proxies = new ArrayList<>();
    private static Object transformer;

    static {
        try {
            var ccc = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
            ccc.setAccessible(true);
            transformer = ccc.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    private boolean isActive = true;

    public Proxy() {
        for (Proxy proxy : proxies) {
            proxy.isActive = false;
        }

        proxies.add(this);
        MixinService.getService().getLogger("mixin").debug("Adding new mixin transformer proxy #{}", proxies.size());
    }

    @Override
    public byte[] transform(String name, byte[] basicClass) {
        if (this.isActive) {
            try {
                Method method = transformer.getClass().getMethod("transformClassBytes", String.class, String.class, byte[].class);
                method.setAccessible(true);
                return (byte[]) method.invoke(transformer, name, name, basicClass);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }
        }

        return basicClass;
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public boolean isDelegationExcluded() {
        return true;
    }

    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
        if (this.isActive) {
            try {
                Method method = transformer.getClass().getMethod("transformClassBytes", String.class, String.class, byte[].class);
                method.setAccessible(true);
                return (byte[]) method.invoke(transformer, name, name, basicClass);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }
        }

        return basicClass;
    }

}
