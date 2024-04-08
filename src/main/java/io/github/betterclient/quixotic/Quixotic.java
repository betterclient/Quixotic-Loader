package io.github.betterclient.quixotic;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * chatgpt gave me this name
 */
public class Quixotic {
    public static QuixoticClassLoader classLoader;
    public static Map<String,Object> blackboard;
    public QuixoticApplication application;
    public static Logger LOGGER = LogManager.getLogger("Quixotic");

    static Quixotic instance;

    public static void main(String[] args) throws Exception {
        new Quixotic().launch(new ArrayList<>(List.of(args)));
    }

    public Quixotic() {
        instance = this;

        //The project uses java 17, don't run check
        classLoader = new QuixoticClassLoader(this.getURLs(), LOGGER);
        blackboard = new HashMap<>();
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    public static String getSideName() {
        return instance.application.getSide().name();
    }

    private void launch(List<String> args) throws Exception {
        if(args.contains("--quixoticapp")) {
            int index = args.indexOf("--quixoticapp") + 1;

            this.application = (QuixoticApplication) classLoader.loadClass(args.get(index)).getConstructor().newInstance();

            LOGGER.debug("Application found {}", this.application);

            //Remove first and second args
            args.remove(index - 1);
            args.remove(index - 1);
        } else {
            this.application = (QuixoticApplication) Class.forName("io.github.betterclient.quixotic.test.TestApplication").getConstructor().newInstance();
            LOGGER.warn("No Application argument could be found, using vanilla application.");
        }

        LOGGER.debug("State - Prepare launch");

        this.application.loadApplicationManager(classLoader);

        this.loadMixinContext(args);

        LOGGER.debug("State - Mixin Launch");

        MixinBootstrap.init();
        MixinExtrasBootstrap.init();
        Mixins.addConfigurations(this.application.getMixinConfigurations().toArray(new String[0]));
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);

        //Mixin trickery start
        ((ArrayList<?>) blackboard.get("TweakClasses")).clear();
        ((ArrayList<?>) blackboard.get("Tweaks")).clear();

        MixinBootstrap.getPlatform().inject();

        var method = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        method.setAccessible(true);
        method.invoke(null, MixinEnvironment.Phase.DEFAULT);
        //Mixin trickery end

        LOGGER.debug("State - Load Main Application Class");

        Class<?> applicationClass = Class.forName(this.application.getMainClass(), false, classLoader);
        Method mainMethod = applicationClass.getMethod("main", String[].class); //certified java moment

        LOGGER.info("Launching {} version {} with main class {}.", this.application.getApplicationName(), this.application.getApplicationVersion(), this.application.getMainClass());

        mainMethod.invoke(null, (Object) args.toArray(String[]::new));
    }

    public void loadMixinContext(List<String> args) {
        blackboard.put("TweakClasses", new ArrayList<String>());
        blackboard.put("ArgumentList", args.toString());
        blackboard.put("Tweaks", new ArrayList<>());
    }

    private URL[] getURLs() {
        String cp = System.getProperty("java.class.path");
        String[] elements = cp.split(File.pathSeparator);
        if (elements.length == 0) {
            elements = new String[] { "" };
        }
        URL[] urls = new URL[elements.length];
        for (int i = 0; i < elements.length; i++) {
            try {
                URL url = new File(elements[i]).toURI().toURL();
                urls[i] = url;
            } catch (MalformedURLException ignore) {
                //Malformed file string or class path element does not exist
            }
        }
        return urls;
    }
}
