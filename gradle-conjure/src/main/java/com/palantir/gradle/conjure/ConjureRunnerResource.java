/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.conjure;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.palantir.gradle.conjure.ConjureRunnerResource.Params;
import com.palantir.gradle.conjure.ReverseEngineerJavaStartScript.StartScriptInfo;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.AsmVisitorWrapper.ForDeclaredMethods;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.process.ExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConjureRunnerResource implements BuildService<Params>, Closeable {

    private static final Logger log = LoggerFactory.getLogger(ConjureRunnerResource.class);

    public interface Params extends BuildServiceParameters {

        RegularFileProperty getExecutable();
    }

    private final ConjureRunner delegate;

    public ConjureRunnerResource() throws IOException {
        this.delegate =
                createNewRunner(getParameters().getExecutable().getAsFile().get());
    }

    final void invoke(Project project, String failedTo, List<String> unloggedArgs, List<String> loggedArgs) {
        delegate.invoke(project, failedTo, unloggedArgs, loggedArgs);
    }

    @Override
    public final void close() throws IOException {
        delegate.close();
    }

    interface ConjureRunner extends Closeable {

        void invoke(Project project, String failedTo, List<String> unloggedArgs, List<String> loggedArgs);
    }

    static ConjureRunner createNewRunner(File executable) throws IOException {
        Optional<StartScriptInfo> maybeJava = ReverseEngineerJavaStartScript.maybeParseStartScript(executable.toPath());
        if (maybeJava.isPresent()) {
            ReverseEngineerJavaStartScript.StartScriptInfo info = maybeJava.get();
            boolean classLoaderMustBeClosed = true;
            URLClassLoader classLoader =
                    new ChildFirstUrlClassLoader(info.classpathUrls(), ConjureRunnerResource.class.getClassLoader());
            try {
                Optional<Method> mainMethod = getMainMethod(classLoader, info.mainClass());
                if (mainMethod.isPresent()) {
                    classLoaderMustBeClosed = false;
                    return new InProcessConjureRunner(executable, mainMethod.get(), classLoader);
                }
            } finally {
                if (classLoaderMustBeClosed) {
                    classLoader.close();
                }
            }
        }
        return new ExternalProcessConjureRunner(executable);
    }

    private static Optional<Method> getMainMethod(URLClassLoader classLoader, String mainClassName) {
        try {
            ClassFileLocator locator = new ClassFileLocator.ForUrl(classLoader.getURLs());
            TypePool typePool = TypePool.ClassLoading.of(classLoader);
            Class<?> mainClass = new ByteBuddy(ClassFileVersion.ofThisVm(ClassFileVersion.JAVA_V8))
                    .redefine(typePool.describe(mainClassName).resolve(), locator)
                    .name(mainClassName + "RedefinedForGradleConjure")
                    .visit(new ForDeclaredMethods()
                            .invokable(
                                    ElementMatchers.any(),
                                    MemberSubstitution.relaxed()
                                            .method(ElementMatchers.is(System.class.getMethod("exit", int.class)))
                                            .replaceWith(GradleExecStubs.getStubMethod())))
                    .make(typePool)
                    .load(classLoader, ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();

            return Optional.of(mainClass.getMethod("main", String[].class));
        } catch (ReflectiveOperationException e) {
            log.warn("Failed too get main method {}", mainClassName, e);
            return Optional.empty();
        }
    }

    private static final class ExternalProcessConjureRunner implements ConjureRunner {

        private final File executable;

        ExternalProcessConjureRunner(File executable) {
            this.executable = executable;
        }

        @Override
        public void close() {
            // nop
        }

        @Override
        public void invoke(Project project, String failedTo, List<String> unloggedArgs, List<String> loggedArgs) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            List<String> combinedArgs = ImmutableList.<String>builder()
                    .add(executable.getAbsolutePath())
                    .addAll(unloggedArgs)
                    .addAll(loggedArgs)
                    .build();

            ExecResult execResult = project.exec(execSpec -> {
                project.getLogger().info("Running with args: {}", loggedArgs);
                execSpec.commandLine(combinedArgs);
                execSpec.setIgnoreExitValue(true);
                execSpec.setStandardOutput(output);
                execSpec.setErrorOutput(output);
            });

            int exitValue = execResult.getExitValue();
            log.debug("Executable {} completed with status {} output:\n{}", executable.getName(), exitValue, output);

            if (exitValue != 0) {
                throw new RuntimeException(String.format(
                        "Failed to %s. The command '%s' failed with exit code %d. Output:\n%s",
                        failedTo, combinedArgs, exitValue, output.toString(StandardCharsets.UTF_8)));
            }
        }
    }

    // We run java things *in-process* to save JVM startup time and reuse JIT optimization (helpful if there are 100
    // conjure projects)
    private static final class InProcessConjureRunner implements ConjureRunner {

        private final File executable;
        private final Method mainMethod;
        private final URLClassLoader classLoader;

        InProcessConjureRunner(File executable, Method mainMethod, URLClassLoader classLoader) {
            this.executable = executable;
            this.mainMethod = mainMethod;
            this.classLoader = classLoader;
        }

        @Override
        public void invoke(Project project, String failedTo, List<String> unloggedArgs, List<String> loggedArgs) {
            project.getLogger().info("Running in-process java with args: {}", loggedArgs);
            List<String> combinedArgs = ImmutableList.<String>builder()
                    .addAll(unloggedArgs)
                    .addAll(loggedArgs)
                    .build();

            try {
                String[] args = combinedArgs.toArray(new String[] {});
                mainMethod.invoke(null, new Object[] {args});
            } catch (Throwable t) {
                Throwable rootCause = Throwables.getRootCause(t);
                if (rootCause instanceof GradleExecStubs.ExitInvoked) {
                    int exitStatus = ((GradleExecStubs.ExitInvoked) rootCause).getExitStatus();
                    if (exitStatus != 0) {
                        // the error message from a generator attempting to call exit 1 looks pretty gross
                        throw new RuntimeException(String.format(
                                "Failed to %s. The command '%s' with args %s failed with exit code %d. Output above.",
                                failedTo, executable.getName(), combinedArgs, exitStatus));
                    }
                    // Exit status zero, we're good to go!
                } else {
                    throw new RuntimeException(
                            String.format("Failed to %s. The command '%s' failed.", failedTo, combinedArgs), t);
                }
            }
        }

        @Override
        public void close() throws IOException {
            classLoader.close();
        }
    }
}
