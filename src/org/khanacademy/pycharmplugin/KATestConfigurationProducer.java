package org.khanacademy.pycharmplugin;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.TestRunnerService;
import com.jetbrains.python.testing.unittest.PythonUnitTestConfigurationProducer;
import com.jetbrains.python.testing.unittest.PythonUnitTestRunConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Custom run configuration producer for KA unit tests. Even though PyCharm integrates with the
 * unittest framework, KA tests can't be run as regular unit tests since they require certain
 * imports and global state to be set up beforehand. tools/load_tests.py is a unittest-compatible
 * test suite that uses an environment variable to determine which tests to use, so this class
 * creates run configurations that invoke load_tests.py with the right environment variables, as a
 * replacement for the regular run configurations.
 *
 * Since the test loading logic is similar to PythonUnitTestConfigurationProducer and
 * PythonTestConfigurationProducer, we work off of those. Unfortunately, that code doesn't seem to
 * be written with composition in mind, so inheritance it is.
 *
 * Note that the run configurations generated by this code don't survive refactor operations. If we
 * wanted to do that, the best approach would probably be to make a new run config type based off of
 * PythonUnitTestRunConfiguration.
 */
public class KATestConfigurationProducer extends PythonUnitTestConfigurationProducer {

    /**
     * In order to disable the regular Unittest menu item, we get its isAvailable function to return
     * false always by artificially setting the project config to "KAUnittests", which effectively
     * means that no test type is selected. Since this class extends from the Unittest config
     * producer, we inherit the disabling code, but we can avoid that by overriding isAvailable and
     * always returning true.
     *
     * There may be a cleaner way of doing this, such as disabling the other extension by name,
     * reaching into the dependency injector, or something like that.
     */
    @Override
    protected boolean isAvailable(@NotNull Location location) {
        return true;
    }

    /**
     * Unlike the default Unittest runner, we're willing to use any individual folder as a starting
     * point for tests; we just run all tests we end up finding in that folder.
     */
    @Override
    protected boolean isTestFolder(@NotNull VirtualFile virtualFile, @NotNull Project project) {
        return true;
    }

    /**
     * Given a context (e.g. a location in source code), determine if a run
     * configuration can be generated here. If not, return false. If so, fill in
     * the configuration param with the proper values.
     */
    @Override
    protected boolean setupConfigurationFromContext(
            AbstractPythonTestRunConfiguration configuration,
            ConfigurationContext context,
            Ref<PsiElement> sourceElement) {
        // Disable other Unittest menu items; see the isAvailable javadoc.
        Module module = context.getModule();
        if (module == null) {
            return false;
        }
        TestRunnerService.getInstance(module).setProjectConfiguration("KAUnittests");

        boolean result = super.setupConfigurationFromContext(
                configuration, context, sourceElement);
        if (!result) {
            return false;
        }

        // TODO(alan): For test methods, the normal PyCharm code is smart enough to exclude the
        // class name in the menu item (see AbstractPythonTestRunConfiguration.getActionName ), but
        // that logic doesn't work in our case since we switch out the test type (and also probably
        // because we set the name as modified). Probably the right way to solve this is to create
        // a separate run config class for KA tests.
        configuration.setName(configuration.getName().replaceFirst("Unittest", "KA Test"));
        configuration.setNameChangedByUser(true);

        String basePath = context.getProject().getBasePath();
        String testSpec;
        try {
            testSpec = getTestSpec(configuration, basePath);
        } catch (IllegalStateException e) {
            // Swallow and fail: the file was outside of the project.
            return false;
        }
        configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_SCRIPT);
        configuration.setScriptName(basePath + "/tools/load_tests.py");
        configuration.setEnvs(ImmutableMap.of("TEST_SPECS", testSpec, "MAX_TEST_SIZE", "huge"));
        configuration.setWorkingDirectory(basePath);
        return true;
    }

    /**
     * Given a "normal" run config, transform it into a test spec (something that could be passed
     * to tools/runtests.py) that does the equivalent thing.
     *
     * @param configuration the proposed configuration normally computed by PyCharm.
     * @param basePath the root directory of the current project.
     * @throws IllegalStateException if the test is not in the current project.
     */
    private String getTestSpec(AbstractPythonTestRunConfiguration configuration, String basePath) {
        AbstractPythonTestRunConfiguration.TestType testType = configuration.getTestType();
        switch (testType) {
            case TEST_FOLDER:
                return getPathSpec(configuration.getFolderName(), basePath);
            case TEST_SCRIPT:
                return getPathSpec(configuration.getScriptName(), basePath);
            case TEST_CLASS:
                return getPathSpec(configuration.getScriptName(), basePath) +
                        "." + configuration.getClassName();
            case TEST_METHOD:
                return getPathSpec(configuration.getScriptName(), basePath) +
                        "." + configuration.getClassName() + "." + configuration.getMethodName();
            case TEST_FUNCTION:
                return getPathSpec(configuration.getScriptName(), basePath) +
                        "." + configuration.getMethodName();
            default:
                throw new IllegalArgumentException("Unexpected test type: " + testType);
        }
    }

    /**
     * Turn an absolute path to a file or directory into a dot-separated path. For example,
     * "/Users/alan/khan/webapp/bigbingo/config_test.py" turns into "bigbingo.config_test".
     *
     * @throws IllegalStateException if the path is not contained in the basePath.
     */
    private String getPathSpec(String path, String basePath) {
        if (!path.substring(0, basePath.length()).equals(basePath)) {
            throw new IllegalStateException("Path " + path + " is out of bounds.");
        }
        String relativePath = path.substring(basePath.length());
        if (relativePath.startsWith(File.separator)) {
            relativePath = relativePath.substring(File.separator.length());
        }
        if (relativePath.endsWith(".py")) {
            relativePath = relativePath.substring(0, relativePath.length() - 3);
        }
        return relativePath.replaceAll(File.separator, ".");
    }

    /**
     * Decide whether the given pre-existing run config matches the current code point (to avoid
     * generating a new run config for every test we run). Instead of implementing separate logic
     * based on the code structure, just generate another config and see if the test spec matches
     * up.
     */
    @Override
    public boolean isConfigurationFromContext(
            AbstractPythonTestRunConfiguration configuration, ConfigurationContext context) {
        PythonUnitTestRunConfiguration newConfig = (PythonUnitTestRunConfiguration)
                cloneTemplateConfiguration(context).getConfiguration();
        setupConfigurationFromContext(newConfig, context, null);
        return configuration.getScriptName().equals(newConfig.getScriptName()) &&
                configuration.getEnvs().equals(newConfig.getEnvs());
    }
}
