package io.github.betterclient.quixotic.test.mixin;

import io.github.betterclient.quixotic.test.TestMain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TestMain.class)
public class TestMixin {
    @Inject(method = "start", at = @At("HEAD"))
    public void hi(CallbackInfo ci) {
        System.out.println("Hello!");
    }
}
