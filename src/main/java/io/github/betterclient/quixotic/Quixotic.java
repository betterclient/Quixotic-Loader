package io.github.betterclient.quixotic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * chatgpt gave me this name
 */
public class Quixotic {
    public static QuixoticClassLoader classLoader;
    public static Map<String,Object> blackboard;
    public QuixoticApplication application;
    public static Logger LOGGER = LogManager.getLogger("Quixotic");

    public static File jarFile;
    public static File saveTo;

    public static void main(String[] args) throws Exception {
        if(args[0].equals("--jarfile"))
            jarFile = new File(args[1]);

        new Quixotic().launch(new ArrayList<>(Arrays.asList(args)));
    }

    public Quixotic() throws Exception {
        ArrayList<URL> urls = new ArrayList<>();

        if(this.getClass().getClassLoader() instanceof URLClassLoader) {
            urls.addAll(Arrays.asList(((URLClassLoader) this.getClass().getClassLoader()).getURLs()));
        } else {
            urls.addAll(Arrays.asList(getURLs()));
        }

        urls.add(jarFile.toURI().toURL());

        classLoader = new QuixoticClassLoader((URL[]) urls.toArray(), LOGGER);

        blackboard = new HashMap<>();
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    private void launch(List<String> args) throws Exception {
        if(args.contains("--quixoticapp")) {
            int index = args.indexOf("--quixoticapp") + 1;

            this.application = (QuixoticApplication) classLoader.loadClass(args.get(index)).getConstructor().newInstance();

            LOGGER.debug("Application found {}", this.application);
        } else {
            this.application = (QuixoticApplication) Class.forName("io.github.betterclient.quixotic.app.MinecraftVanillaApplication").getConstructor().newInstance();
            LOGGER.warn("No Application argument could be found, using vanilla application.");
        }

        if(args.contains("--savefile")) {
            int index = args.indexOf("--savefile") + 1;

            saveTo = new File(args.get(index));

            if(saveTo.exists())
                saveTo.delete();

            saveTo.createNewFile();

            LOGGER.debug("Application found {}", this.application);
        } else {
            throw new RuntimeException("Missing savefile");
        }

        LOGGER.debug("State - Prepare launch");

        this.loadMixinContext(args);

        MixinBootstrap.init();
        Mixins.addConfigurations(this.application.getMixinConfigurations().toArray(new String[0]));
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);

        //Mixin trickery start
        ((ArrayList<?>) blackboard.get("TweakClasses")).clear();
        ((ArrayList<?>) blackboard.get("Tweaks")).clear();

        MixinBootstrap.getPlatform().inject();

        Method method = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        method.setAccessible(true);
        method.invoke(null, MixinEnvironment.Phase.DEFAULT);
        //Mixin trickery end

        LOGGER.debug("State - Mixin Launch");

        this.application.loadApplicationManager(classLoader);

        LOGGER.debug("State - Transform Jar File");

        //Loop through all .class files in jarFile and load all to transform them

        JarFile file = new JarFile(jarFile);
        Enumeration<JarEntry> entries = file.entries();
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(saveTo));
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            if(!entry.getName().endsWith(".class")) {
                InputStream str = file.getInputStream(entry);

                jarOutputStream.putNextEntry(new ZipEntry(entry.getName()));
                byte[] read = new byte[str.available()];
                str.read(read);
                jarOutputStream.write(read);

                str.close();
                jarOutputStream.closeEntry();

                continue;
            }

            String className = entry.getName().substring(0, entry.getName().lastIndexOf("."));
            className = className.replace('/', '.');

            InputStream str = file.getInputStream(entry);
            byte[] src = new byte[str.available()];
            str.read(src);
            str.close();

            src = classLoader.transformClass(className, src);

            jarOutputStream.putNextEntry(new ZipEntry(entry.getName()));
            jarOutputStream.write(src);
            jarOutputStream.closeEntry();
        }

        file.close();

        if(args.contains("--skipmixindep")) {
            LOGGER.debug("Done!");
            System.exit(0);
            return;
        }

        JarFile quixoticJar = new JarFile(new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath()));
        Enumeration<JarEntry> entriess = quixoticJar.entries();

        ZipEntry nextEntry;
        while(entriess.hasMoreElements()) { //Add mixin source and your app to final jar
            nextEntry = entriess.nextElement();

            if(!nextEntry.getName().startsWith("org/spongepowered/asm") || !nextEntry.getName().contains(".")) continue;

            InputStream str = quixoticJar.getInputStream(nextEntry);

            jarOutputStream.putNextEntry(new ZipEntry(nextEntry.getName()));
            byte[] read = new byte[str.available()];
            str.read(read);
            jarOutputStream.write(read);

            jarOutputStream.closeEntry();
            str.close();
        }

        jarOutputStream.close();
        quixoticJar.close();

        LOGGER.debug("Done!");
        System.exit(0);
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
