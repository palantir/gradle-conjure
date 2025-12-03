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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.AsmVisitorWrapper.ForDeclaredMethods;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConjureRunnerResource implements BuildService<Params>, Closeable {

    private static final Logger log = LoggerFactory.getLogger(ConjureRunnerResource.class);

    public interface Params extends BuildServiceParameters {
        RegularFileProperty getExecutable();
    }

    private final ConjureRunner delegate;

    @Inject
    protected abstract ExecOperations getExecOperations();

    public ConjureRunnerResource() throws IOException {
        this.delegate =
                createNewRunner(getParameters().getExecutable().getAsFile().get());
    }

    final void invoke(
            WorkerExecutor workerExecutor, String failedTo, List<String> unloggedArgs, List<String> loggedArgs) {
        delegate.invoke(getExecOperations(), workerExecutor, failedTo, unloggedArgs, loggedArgs);
    }

    @Override
    public final void close() throws IOException {
        delegate.close();
    }

    interface ConjureRunner extends Closeable {
        void invoke(
                ExecOperations execOperations,
                WorkerExecutor workerExecutor,
                String failedTo,
                List<String> unloggedArgs,
                List<String> loggedArgs);
    }

    static ConjureRunner createNewRunner(File executable) throws IOException {
        Optional<StartScriptInfo> maybeJava = ReverseEngineerJavaStartScript.maybeParseStartScript(executable.toPath());
        if (maybeJava.isPresent()) {
            return new InProcessConjureRunner(executable, maybeJava.get());
        } else {
            return new ExternalProcessConjureRunner(executable);
        }
    }

    private static final class ExternalProcessConjureRunner implements ConjureRunnerResource.ConjureRunner {

        private final File executable;

        ExternalProcessConjureRunner(File executable) {
            this.executable = executable;
        }

        @Override
        public void invoke(
                ExecOperations execOperations,
                WorkerExecutor _workerExecutor,
                String failedTo,
                List<String> unloggedArgs,
                List<String> loggedArgs) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            List<String> combinedArgs = ImmutableList.<String>builder()
                    .add(executable.getAbsolutePath())
                    .addAll(unloggedArgs)
                    .addAll(loggedArgs)
                    .build();

            log.info("Running external process: {} with args: {}", executable.getName(), loggedArgs);

            ExecResult execResult = execOperations.exec(execSpec -> {
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

        @Override
        public void close() {
            // nop
        }
    }

    // We run java things *in-process* to save JVM startup time and reuse JIT optimization (helpful if there are 100
    // conjure projects)
    private static final class InProcessConjureRunner implements ConjureRunner {

        private final File executable;
        private final StartScriptInfo info;

        InProcessConjureRunner(File executable, StartScriptInfo info) {
            this.executable = executable;
            this.info = info;
        }

        @SuppressWarnings("for-rollout:ExplicitArrayForVarargs")
        @Override
        public void invoke(
                ExecOperations _execOperations,
                WorkerExecutor workerExecutor,
                String failedTo,
                List<String> unloggedArgs,
                List<String> loggedArgs) {
            log.info("Running in-process java: {} with args: {}", executable.getName(), loggedArgs);

            WorkQueue workQueue = workerExecutor.processIsolation(processWorkerSpec -> {
                processWorkerSpec
                        .getForkOptions()
                        .jvmArgs(
                                "--add-exports",
                                "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                                "--add-exports",
                                "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
                                "--add-exports",
                                "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
                                "--add-exports",
                                "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                                "--add-exports",
                                "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
            });

            workQueue.submit(ConjureRunnerWorkAction.class, parameters -> {
                parameters.getExecutableName().set(executable.getName());
                parameters.getClasspathUrls().set(info.classpathUrls());
                parameters.getMainClass().set(info.mainClass());
                parameters.getArgs().addAll(unloggedArgs);
                parameters.getArgs().addAll(loggedArgs);
                parameters.getFailedTo().set(failedTo);
            });
        }

        @Override
        public void close() throws IOException {
            // nop
        }
    }

    public interface ConjureRunnerWorkParameters extends WorkParameters {

        Property<String> getExecutableName();

        ListProperty<URL> getClasspathUrls();

        Property<String> getMainClass();

        ListProperty<String> getArgs();

        Property<String> getFailedTo();
    }

    public abstract static class ConjureRunnerWorkAction implements WorkAction<ConjureRunnerWorkParameters> {

        private static Map<Key, State> CACHE = new ConcurrentHashMap<>();

        private record Key(String mainClass, List<URL> classpathUrls) {

            Key(ConjureRunnerWorkParameters parameters) {
                this(
                        parameters.getMainClass().get(),
                        parameters.getClasspathUrls().get());
            }
        }

        private static record State(URLClassLoader classLoader, Method mainMethod) {

            private static State create(Key key) {
                boolean classLoaderMustBeClosed = true;
                URLClassLoader classLoader = new ChildFirstUrlClassLoader(
                        key.classpathUrls().toArray(URL[]::new), ConjureRunnerWorkAction.class.getClassLoader());

                Method mainMethod;
                try {
                    mainMethod = getMainMethod(classLoader, key.mainClass());
                } catch (RuntimeException e) {
                    try {
                        classLoader.close();
                    } catch (IOException ex) {
                        e.addSuppressed(ex);
                    }
                    throw e;
                }

                return new State(classLoader, mainMethod);
            }
        }

        @Override
        public void execute() {
            State state = CACHE.computeIfAbsent(new Key(getParameters()), State::create);

            try {
                String[] args = getParameters().getArgs().get().toArray(new String[] {});
                state.mainMethod().invoke(null, new Object[] {args});
            } catch (Throwable t) {
                Throwable rootCause = Throwables.getRootCause(t);
                if (rootCause instanceof GradleExecStubs.ExitInvoked exitInvoked) {
                    int exitStatus = exitInvoked.getExitStatus();
                    if (exitStatus != 0) {
                        // the error message from a generator attempting to call exit 1 looks pretty gross
                        throw new RuntimeException(String.format(
                                "Failed to %s. The command '%s' with args %s failed with exit code %d. Output above.",
                                getParameters().getFailedTo().get(),
                                getParameters().getExecutableName().get(),
                                getParameters().getArgs().get(),
                                exitStatus));
                    }
                    // Exit status zero, we're good to go!
                } else {
                    throw new RuntimeException(
                            String.format(
                                    "Failed to %s. The command '%s' failed.",
                                    getParameters().getFailedTo().get(),
                                    getParameters().getArgs().get()),
                            t);
                }
            }
        }

        private static Method getMainMethod(URLClassLoader classLoader, String mainClassName) {
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

                return mainClass.getMethod("main", String[].class);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to get main method " + mainClassName, e);
            }
        }
    }
}
