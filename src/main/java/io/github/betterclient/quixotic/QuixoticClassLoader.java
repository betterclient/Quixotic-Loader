package io.github.betterclient.quixotic;

import io.github.betterclient.quixotic.mixin.Proxy;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class QuixoticClassLoader extends URLClassLoader {
    private final ClassLoader parent = getClass().getClassLoader();
    public List<ClassTransformer> transformers = new ArrayList<>();
    public List<String> excludeFromSearch = new ArrayList<>();
    public List<String> unExcludeFromSearch = new ArrayList<>();
    public List<String> excludeFromTransform = new ArrayList<>();
    public List<String> nonLoadableClasses = new ArrayList<>();
    public List<String> cachedClassNames = new ArrayList<>();
    public List<Class<?>> cachedClasses = new ArrayList<>();
    private final List<String> negativeResourceCache = new ArrayList<>();
    private final Map<String,byte[]> resourceCache = new ConcurrentHashMap<>(1000);

    public static Logger LOGGER;

    static {
        registerAsParallelCapable();
    }

    public QuixoticClassLoader(URL[] urls, Logger logger) {
        super(urls, null);
        LOGGER = logger;

        this.addExclusion("java.");
        this.addExclusion("sun.");
        this.addExclusion("org.lwjgl.");
        this.addExclusion("org.apache.logging.");
        this.addExclusion("io.github.betterclient.quixotic.");
        this.addExclusion("net.minecraft.launchwrapper.");

        this.addUnExclusion("io.github.betterclient.quixotic.test"); //Dont exclude the test package

        this.addTransformerExclusion("javax.");
        this.addTransformerExclusion("argo.");
        this.addTransformerExclusion("org.objectweb.asm.");
        this.addTransformerExclusion("com.google.common.");
        this.addTransformerExclusion("org.bouncycastle.");
    }

    public void addExclusion(String exclusion) {
        excludeFromSearch.add(exclusion);
    }

    public void addUnExclusion(String exclusion) {
        unExcludeFromSearch.add(exclusion);
    }

    public void addTransformerExclusion(String exclusion) {
        excludeFromTransform.add(exclusion);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    public void addTransformer(String name) {
        try {
            ClassTransformer transformer = (ClassTransformer) loadClass(name).getConstructor().newInstance();
            transformers.add(transformer);
        } catch (Exception e) {
            LOGGER.error("Critical error occurred while registering ClassTransformer: \n{}", e.getMessage());
        }
    }

    public void addPlainTransformer(ClassTransformer transformer) {
        transformers.add(transformer);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        if(nonLoadableClasses.contains(name))
            return parent.loadClass(name);

        if(this.cachedClassNames.contains(name))
            return this.cachedClasses.get(this.cachedClassNames.indexOf(name));

        for (String fromSearch : this.excludeFromSearch) {
            if(name.startsWith(fromSearch)) {
                for (String unexclude : this.unExcludeFromSearch) {
                    if(!name.startsWith(unexclude)) {
                        return this.parent.loadClass(name);
                    }
                }
            }
        }

        for (final String fromTransform : this.excludeFromTransform) {
            if (name.startsWith(fromTransform)) {
                try {
                    final Class<?> clazz = parent.loadClass(name);
                    this.cachedClasses.add(clazz);
                    this.cachedClassNames.add(name);
                    return clazz;
                } catch (ClassNotFoundException e) {
                    this.nonLoadableClasses.add(name);
                    throw e;
                }
            }
        }

        try {
            final int lastDot = name.lastIndexOf('.');
            final String packageName = lastDot == -1 ? "" : name.substring(0, lastDot);
            final String fileName = name.replace('.', '/').concat(".class"); //This is an url loader after all
            URLConnection urlConnection = findCodeSourceConnectionFor(fileName);

            if (lastDot > -1 && !name.startsWith("net.minecraft.")) {
                if (urlConnection instanceof final JarURLConnection jarURLConnection) {
                    final JarFile jarFile = jarURLConnection.getJarFile();

                    if (jarFile != null && jarFile.getManifest() != null) {
                        final Manifest manifest = jarFile.getManifest();
                        Package pkg = getDefinedPackage(packageName);
                        if (pkg == null) definePackage(packageName, manifest, jarURLConnection.getJarFileURL());
                    }
                } else {
                    Package pkg = getDefinedPackage(packageName);
                    if (pkg == null) {
                        definePackage(packageName, null, null, null, null, null, null, null);
                    }
                }
            }

            byte[] transformedClass = this.findClassBytes(name, true);

            //Use no signing
            CodeSource codeSource = urlConnection == null ? null : new CodeSource(urlConnection.getURL(), (Certificate[]) null);
            Class<?> clazz = defineClass(name, transformedClass, 0, transformedClass.length, codeSource);

            this.cachedClasses.add(clazz);
            this.cachedClassNames.add(name);

            return clazz;
        } catch (Exception e) {
            this.nonLoadableClasses.add(name);
            throw new ClassNotFoundException("Couldn't find class with name " + name, e);
        }
    }

    private byte[] transformClass(String name, byte[] classBytes, boolean transformAll) {
        for(ClassTransformer transformer : this.transformers) {
            if (transformer instanceof Proxy && !transformAll) continue;

            byte[] output = transformer.transform(name, classBytes);
            classBytes = (output == null || output.length == 0) ? classBytes : output; //Java ClassFileTransformer behaviour
        }

        return classBytes;
    }

    //Utility methods:
    /*------------------------------------------------------------------------*/

    private URLConnection findCodeSourceConnectionFor(final String name) {
        final URL resource = findResource(name);
        if (resource != null) {
            try {
                return resource.openConnection();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    public byte[] findClassBytes(String name, boolean transformAll) throws IOException {
        return this.transformClass(name, getClassBytes(name), transformAll);
    }

    private byte[] getClassBytes(String name) throws IOException {
        if (negativeResourceCache.contains(name)) {
            return null;
        } else if (resourceCache.containsKey(name)) {
            return resourceCache.get(name);
        }
        if (name.indexOf('.') == -1) {
            for (final String reservedName : new String[]{"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"}) {
                if (name.toUpperCase().startsWith(reservedName)) {
                    final byte[] data = getClassBytes("_" + name);
                    if (data != null) {
                        resourceCache.put(name, data);
                        return data;
                    }
                }
            }
        }

        InputStream classStream = null;
        try {
            final String resourcePath = name.replace('.', '/').concat(".class");
            final URL classResource = findResource(resourcePath);

            if (classResource == null) {
                negativeResourceCache.add(name);
                return null;
            }
            classStream = classResource.openStream();

            byte[] data = classStream.readAllBytes();
            resourceCache.put(name, data);
            return data;
        } finally {
            if(classStream != null)
                classStream.close();
        }
    }
}
