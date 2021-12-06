package juuxel.loomquiltflower.api;

import juuxel.loomquiltflower.impl.DeprecatedQuiltflowerExtension;
import juuxel.loomquiltflower.impl.PreferenceScanner;
import juuxel.loomquiltflower.impl.QuiltflowerExtensionImpl;
import juuxel.loomquiltflower.impl.QuiltflowerResolving;
import juuxel.loomquiltflower.impl.ReflectionUtil;
import juuxel.loomquiltflower.impl.legacy.LegacyQuiltflowerDecompiler;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class LoomQuiltflowerPlugin implements Plugin<Project> {
    private static final List<String> LOOMS = Arrays.asList("fabric-loom", "dev.architectury.loom");
    private boolean applied = false;

    @Override
    public void apply(Project target) {
        // Use create to allow Gradle to decorate our extension
        var extension = target.getExtensions().create(QuiltflowerExtension.class, "quiltflower", QuiltflowerExtensionImpl.class, target);
        // Add the deprecated 'loomQuiltflower' extension
        target.getExtensions().create(QuiltflowerExtension.class, "loomQuiltflower", DeprecatedQuiltflowerExtension.class, target, extension);

        // Scan for preferences declared in gradle.properties
        PreferenceScanner.scan(target, extension);

        // Setup resolving and runtime classpath
        QuiltflowerResolving.setup(target, (QuiltflowerExtensionImpl) extension);

        for (String loomId : LOOMS) {
            target.getPluginManager().withPlugin(loomId, p -> {
                var loom = (LoomGradleExtensionAPI) target.getExtensions().getByName("loom");

                if (isNewArchLoom()) {
                    try {
                        Class<?> archLoomDecompiler = Class.forName("net.fabricmc.loom.api.decompilers.architectury.ArchitecturyLoomDecompiler");
                        Method addArchDecompiler = LoomGradleExtensionAPI.class.getMethod("addArchDecompiler", archLoomDecompiler);
                        addArchDecompiler.invoke(loom, archLoomDecompiler.getConstructor(QuiltflowerExtension.class).newInstance(extension));
                    } catch (ReflectiveOperationException e) {
                        throw new GradleException("Could not add Quiltflower decompiler", e);
                    }
                } else if (isOldLoom()) {
                    loom.addDecompiler(new LegacyQuiltflowerDecompiler(target, extension));
                } else {
                    String message = "loom-quiltflower is not supported on this Loom version!\nReplace with loom-quiltflower-mini: https://github.com/Juuxel/loom-quiltflower-mini";
                    target.getLogger().error(message);
                    throw new UnsupportedOperationException(message);
                }

                applied = true;
            });
        }

        target.afterEvaluate(p -> {
            if (!applied) {
                throw new GradleException("loom-quiltflower requires Loom! (One of " + LOOMS + ")");
            }
        });
    }

    private static boolean isOldLoom() {
        return ReflectionUtil.classExists("net.fabricmc.loom.decompilers.fernflower.AbstractForkedFFExecutor");
    }

    private static boolean isNewArchLoom() {
        return ReflectionUtil.classExists("net.fabricmc.loom.api.decompilers.architectury.ArchitecturyLoomDecompiler");
    }
}
