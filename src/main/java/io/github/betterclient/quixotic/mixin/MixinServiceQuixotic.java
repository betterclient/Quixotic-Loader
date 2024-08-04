package io.github.betterclient.quixotic.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.betterclient.quixotic.ClassTransformer;
import io.github.betterclient.quixotic.Quixotic;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.platform.MainAttributes;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;
import org.spongepowered.asm.mixin.throwables.MixinException;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ILegacyClassTransformer;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;
import org.spongepowered.asm.transformers.MixinClassReader;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.asm.util.Files;
import org.spongepowered.asm.util.perf.Profiler;
import org.spongepowered.asm.util.perf.Profiler.Section;

import org.spongepowered.include.com.google.common.collect.ImmutableList;
import org.spongepowered.include.com.google.common.collect.ImmutableList.Builder;
import org.spongepowered.include.com.google.common.collect.Sets;
import org.spongepowered.include.com.google.common.io.ByteStreams;
import org.spongepowered.include.com.google.common.io.Closeables;

/**
 * Mixin service for quixotic
 */
public class MixinServiceQuixotic extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider, ITransformerProvider {
    public static final GlobalProperties.Keys BLACKBOARD_KEY_TWEAKCLASSES = GlobalProperties.Keys.of("TweakClasses");
    public static final GlobalProperties.Keys BLACKBOARD_KEY_TWEAKS = GlobalProperties.Keys.of("Tweaks");

    private static final String MIXIN_TWEAKER_CLASS = MixinServiceAbstract.LAUNCH_PACKAGE + "MixinTweaker";
    private static final String STATE_TWEAKER = MixinServiceAbstract.MIXIN_PACKAGE + "EnvironmentStateTweaker";
    private static final String TRANSFORMER_PROXY_CLASS = "io.github.betterclient.quixotic.mixin.Proxy";

    private static final Set<String> excludeTransformers = Sets.newHashSet();

    private static final Logger logger = LogManager.getLogger();

    private final QuixoticClassLoaderUtil classLoaderUtil;

    private List<ILegacyClassTransformer> delegatedTransformers;


    public MixinServiceQuixotic() {
        this.classLoaderUtil = new QuixoticClassLoaderUtil(Quixotic.classLoader);

        try {
            Field f = MixinServiceAbstract.class.getDeclaredField("sideName");
            f.setAccessible(true);
            f.set(this, Quixotic.getSideName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "Quixotic";
    }

    @Override
    public boolean isValid() {
        try {
            Quixotic.classLoader.hashCode();
        } catch (Throwable ex) {
            return false;
        }
        return true;
    }

    @Override
    public void prepare() {
        // Only needed in dev, in production this would be handled by the tweaker
        Quixotic.classLoader.addExclusion(MixinServiceAbstract.LAUNCH_PACKAGE);
    }

    @Override
    public Phase getInitialPhase() {
        String command = System.getProperty("sun.java.command");
        if (command != null && command.contains("GradleStart")) {
            System.setProperty("mixin.env.remapRefMap", "true");
        }

        if (MixinServiceQuixotic.findInStackTrace("io.github.betterclient.quixotic.Quixotic", "main") > 53) {
            return Phase.DEFAULT;
        }
        return Phase.PREINIT;
    }

    @Override
    public CompatibilityLevel getMaxCompatibilityLevel() {
        return CompatibilityLevel.JAVA_21;
    }

    @Override
    protected ILogger createLogger(String name) {
        return new LoggerAdapterLog4j2(name);
    }

    @Override
    public void init() {
        if (MixinServiceQuixotic.findInStackTrace("io.github.betterclient.quixotic.Quixotic", "main") < 4) {
            MixinServiceQuixotic.logger.error("MixinBootstrap.doInit() called during a tweak constructor!");
        }

        List<String> tweakClasses = GlobalProperties.get(MixinServiceQuixotic.BLACKBOARD_KEY_TWEAKCLASSES);
        if (tweakClasses != null) {
            tweakClasses.add(MixinServiceQuixotic.STATE_TWEAKER);
        }

        super.init();
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return ImmutableList.of();
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        URI uri;
        try {
            uri = this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            return new ContainerHandleURI(uri);
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
        }
        return new ContainerHandleVirtual(this.getName());
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        Builder<IContainerHandle> list = ImmutableList.builder();
        this.getContainersFromClassPath(list);
        this.getContainersFromAgents(list);
        return list.build();
    }

    private void getContainersFromClassPath(Builder<IContainerHandle> list) {
        URL[] sources = this.getClassPath();
        if (sources != null) {
            for (URL url : sources) {
                try {
                    URI uri = url.toURI();
                    MixinServiceQuixotic.logger.debug("Scanning {} for mixin tweaker", uri);
                    if (!"file".equals(uri.getScheme()) || !Files.toFile(uri).exists()) {
                        continue;
                    }
                    MainAttributes attributes = MainAttributes.of(uri);
                    String tweaker = attributes.get(Constants.ManifestAttributes.TWEAKER);
                    if (MixinServiceQuixotic.MIXIN_TWEAKER_CLASS.equals(tweaker)) {
                        list.add(new ContainerHandleURI(uri));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this.classLoaderUtil;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return Quixotic.classLoader.findClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, Quixotic.classLoader);
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, Quixotic.class.getClassLoader());
    }

    @Override
    public void beginPhase() {
        Quixotic.classLoader.addTransformer(MixinServiceQuixotic.TRANSFORMER_PROXY_CLASS);
        this.delegatedTransformers = null;
    }

    @Override
    public void checkEnv(Object bootSource) {
        if (bootSource.getClass().getClassLoader() != Quixotic.class.getClassLoader()) {
            throw new MixinException("Attempted to init the mixin environment in the wrong classloader");
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return Quixotic.classLoader.getResourceAsStream(name);
    }

    @Override
    @Deprecated
    public URL[] getClassPath() {
        return Quixotic.classLoader.getURLs();
    }

    @Override
    public Collection<ITransformer> getTransformers() {
        List<ClassTransformer> transformers = Quixotic.classLoader.transformers;
        List<ITransformer> wrapped = new ArrayList<ITransformer>(transformers.size());
        for (ClassTransformer transformer : transformers) {
            if (transformer instanceof ITransformer) {
                wrapped.add((ITransformer)transformer);
            } else {
                wrapped.add(new LegacyTransformerHandle(transformer));
            }
        }
        return wrapped;
    }

    @Override
    public List<ITransformer> getDelegatedTransformers() {
        return Collections.unmodifiableList(this.getDelegatedLegacyTransformers());
    }

    private List<ILegacyClassTransformer> getDelegatedLegacyTransformers() {
        if (this.delegatedTransformers == null) {
            this.buildTransformerDelegationList();
        }

        return this.delegatedTransformers;
    }

    private void buildTransformerDelegationList() {
        MixinServiceQuixotic.logger.debug("Rebuilding transformer delegation list:");
        this.delegatedTransformers = new ArrayList<>();
        for (ITransformer transformer : this.getTransformers()) {
            if (!(transformer instanceof ILegacyClassTransformer legacyTransformer)) {
                continue;
            }

            String transformerName = legacyTransformer.getName();
            boolean include = true;
            for (String excludeClass : MixinServiceQuixotic.excludeTransformers) {
                if (transformerName.contains(excludeClass)) {
                    include = false;
                    break;
                }
            }
            if (include && !legacyTransformer.isDelegationExcluded()) {
                MixinServiceQuixotic.logger.debug("  Adding:    {}", transformerName);
                this.delegatedTransformers.add(legacyTransformer);
            } else {
                MixinServiceQuixotic.logger.debug("  Excluding: {}", transformerName);
            }
        }

        MixinServiceQuixotic.logger.debug("Transformer delegation list created with {} entries", this.delegatedTransformers.size());
    }

    @Override
    public void addTransformerExclusion(String name) {
        MixinServiceQuixotic.excludeTransformers.add(name);

        this.delegatedTransformers = null;
    }

    @Deprecated
    public byte[] getClassBytes(String name, String transformedName) throws IOException {
        byte[] classBytes = Quixotic.classLoader.getClassBytes(name);
        if (classBytes != null) {
            return classBytes;
        }

        URLClassLoader appClassLoader;
        if (Quixotic.class.getClassLoader() instanceof URLClassLoader) {
            appClassLoader = (URLClassLoader) Quixotic.class.getClassLoader();
        } else {
            appClassLoader = new URLClassLoader(new URL[]{}, Quixotic.class.getClassLoader());
        }

        InputStream classStream = null;
        try {
            final String resourcePath = transformedName.replace('.', '/').concat(".class");
            classStream = appClassLoader.getResourceAsStream(resourcePath);
            return ByteStreams.toByteArray(classStream);
        } catch (Exception ex) {
            return null;
        } finally {
            Closeables.closeQuietly(classStream);
        }
    }

    @Deprecated
    public byte[] getClassBytes(String className, boolean runTransformers) throws ClassNotFoundException, IOException {
        String transformedName = className.replace('/', '.');
        String name = this.unmapClassName(transformedName);

        Profiler profiler = Profiler.getProfiler("mixin");
        Section loadTime = profiler.begin(Profiler.ROOT, "class.load");
        byte[] classBytes = this.getClassBytes(name, transformedName);
        loadTime.end();

        if (runTransformers) {
            Section transformTime = profiler.begin(Profiler.ROOT, "class.transform");
            classBytes = this.applyTransformers(name, transformedName, classBytes, profiler);
            transformTime.end();
        }

        if (classBytes == null) {
            throw new ClassNotFoundException(String.format("The specified class '%s' was not found", transformedName));
        }

        return classBytes;
    }

    private byte[] applyTransformers(String name, String transformedName, byte[] basicClass, Profiler profiler) {
        if (this.classLoaderUtil.isClassExcluded(name, transformedName)) {
            return basicClass;
        }

        for (ILegacyClassTransformer transformer : this.getDelegatedLegacyTransformers()) {
            this.lock.clear();

            int pos = transformer.getName().lastIndexOf('.');
            String simpleName = transformer.getName().substring(pos + 1);
            Section transformTime = profiler.begin(Profiler.FINE, simpleName.toLowerCase(Locale.ROOT));
            transformTime.setInfo(transformer.getName());
            basicClass = transformer.transformClassBytes(name, transformedName, basicClass);
            transformTime.end();

            if (this.lock.isSet()) {
                this.addTransformerExclusion(transformer.getName());

                this.lock.clear();
                MixinServiceQuixotic.logger.info("A re-entrant transformer '{}' was detected and will no longer process meta class data",
                        transformer.getName());
            }
        }

        return basicClass;
    }

    private String unmapClassName(String className) {
        return className;
    }

    @Override
    public ClassNode getClassNode(String className) throws ClassNotFoundException, IOException {
        return this.getClassNode(className, this.getClassBytes(className, true), ClassReader.EXPAND_FRAMES);
    }

    @Override
    public ClassNode getClassNode(String className, boolean runTransformers) throws ClassNotFoundException, IOException {
        return this.getClassNode(className, this.getClassBytes(className, runTransformers), ClassReader.EXPAND_FRAMES);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
        return this.getClassNode(name, this.getClassBytes(name, runTransformers), readerFlags);
    }

    private ClassNode getClassNode(String className, byte[] classBytes, int flags) {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new MixinClassReader(classBytes, className);
        classReader.accept(classNode, flags);
        return classNode;
    }

    private static int findInStackTrace(String className, String methodName) {
        Thread currentThread = Thread.currentThread();

        if (!"main".equals(currentThread.getName())) {
            return 0;
        }

        StackTraceElement[] stackTrace = currentThread.getStackTrace();
        for (StackTraceElement s : stackTrace) {
            if (className.equals(s.getClassName()) && methodName.equals(s.getMethodName())) {
                return s.getLineNumber();
            }
        }

        return 0;
    }
}