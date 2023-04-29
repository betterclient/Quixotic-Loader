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
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
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

        new Quixotic().launch(new ArrayList<>(List.of(args)));
    }

    public Quixotic() {
        ArrayList<URL> urls = new ArrayList<>(List.of(this.getURLs()));
        try {
            urls.add(jarFile.toURI().toURL());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        classLoader = new QuixoticClassLoader(urls.toArray(URL[]::new), LOGGER);

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

        var method = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        method.setAccessible(true);
        method.invoke(null, MixinEnvironment.Phase.DEFAULT);
        //Mixin trickery end

        LOGGER.debug("State - Mixin Launch");

        this.application.loadApplicationManager(classLoader);

        LOGGER.debug("State - Transform Jar File");

        //Loop through all .class files in jarFile and load all to transform them

        FileInputStream istr = new FileInputStream(jarFile);

        File jarfile2 = File.createTempFile("whodis", ".jar");
        jarfile2.deleteOnExit();

        FileOutputStream sttr = new FileOutputStream(jarfile2);
        FileOutputStream sttrr = new FileOutputStream(saveTo);

        byte[] write = istr.readAllBytes();

        sttr.write(write);
        sttrr.write(write);

        sttrr.close();
        sttr.close();
        istr.close();

        JarFile file = new JarFile(jarfile2);
        Enumeration<JarEntry> entries = file.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            if(!entry.getName().endsWith(".class")) continue;

            String className = entry.getName().substring(0, entry.getName().lastIndexOf("."));
            className = className.replace('/', '.');

            InputStream abc = file.getInputStream(entry);
            byte[] beforeSRC = abc.readAllBytes();
            abc.close();

            Class<?> loadedClass = Class.forName(className, false, classLoader);
            byte[] src = classLoader.cachedClassBytes.get(classLoader.cachedClassNames.indexOf(className));

            if(src == beforeSRC) continue;

            File f = File.createTempFile(className.substring(className.lastIndexOf(".")), ".class");
            f.deleteOnExit();

            FileOutputStream str = new FileOutputStream(f);
            str.write(src);
            str.close();

            JarFile saved = this.addFileToExistingZip(saveTo, f, entry.getName());
            saved.close();
        }

        file.close();

        JarOutputStream out = new JarOutputStream(Files.newOutputStream(saveTo.toPath()));
        JarFile quixoticJar = new JarFile(new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath()));
        Enumeration<JarEntry> entriess = quixoticJar.entries();

        ZipEntry nextEntry;
        while(entriess.hasMoreElements()) { //Add mixin source and your app to final jar
            nextEntry = entriess.nextElement();

            if(nextEntry.getName().startsWith("io/github/betterclient/quixotic") && !nextEntry.getName().contains(".")) continue;

            InputStream str = quixoticJar.getInputStream(nextEntry);

            out.putNextEntry(new ZipEntry(nextEntry.getName()));
            out.write(str.readAllBytes());

            out.closeEntry();
            str.close();
        }

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

    /**
     *
     * @param zipFile file
     * @param versionFile change
     * @return fixed jarfile
     * @throws Exception fuck you
     */
    private JarFile addFileToExistingZip(File zipFile, File versionFile, String file) throws Exception {
        File tempFile = File.createTempFile(zipFile.getName(), null);
        tempFile.delete();

        boolean renameOk=zipFile.renameTo(tempFile);
        if (!renameOk)
        {
            throw new RuntimeException("could not rename the file "+zipFile.getAbsolutePath()+" to "+tempFile.getAbsolutePath());
        }
        byte[] buf = new byte[4096 * 1024];

        JarInputStream zin = new JarInputStream(new FileInputStream(tempFile));
        JarOutputStream out = new JarOutputStream(new FileOutputStream(zipFile));

        ZipEntry entry = zin.getNextEntry();
        while (entry != null) {
            String name = entry.getName();
            boolean toBeDeleted = false;
            if (file.equals(entry.getName())) {
                toBeDeleted = true;
            }

            if(!toBeDeleted){
                out.putNextEntry(new ZipEntry(name));

                int len;
                while ((len = zin.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            entry = zin.getNextEntry();
        }

        zin.close();

        InputStream in = new FileInputStream(versionFile);

        out.putNextEntry(new JarEntry(file));

        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }

        out.closeEntry();
        in.close();

        out.close();
        tempFile.delete();

        return new JarFile(zipFile);
    }
}
