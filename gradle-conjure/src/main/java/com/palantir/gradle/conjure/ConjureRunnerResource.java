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
import com.palantir.gradle.conjure.ReverseEngineerJavaStartScript.StartScriptInfo;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.AsmVisitorWrapper.ForDeclaredMethods;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConjureRunnerResource {

    private static final Logger log = LoggerFactory.getLogger(ConjureRunnerResource.class);

    interface ConjureRunner {

        void invoke(ExecOperations execOperations, String failedTo, List<String> unloggedArgs, List<String> loggedArgs);
    }

    static ConjureRunner createNewRunnerWithCurrentClasspath(File executable) throws IOException {
        Optional<StartScriptInfo> maybeJava = ReverseEngineerJavaStartScript.maybeParseStartScript(executable.toPath());

        if (maybeJava.isPresent()) {
            ReverseEngineerJavaStartScript.StartScriptInfo info = maybeJava.get();
            Optional<Method> mainMethod = getMainMethod(info.mainClass());
            if (mainMethod.isPresent()) {
                return new InProcessConjureRunner(executable, mainMethod.get());
            }
        }
        return new ExternalProcessConjureRunner(executable);
    }

    static WorkQueue getProcessDaemonWorkQueue(WorkerExecutor workerExecutor, File executable) {
        Map<String, String> env = System.getenv();
        return workerExecutor.processIsolation(processWorkerSpec -> {
            processWorkerSpec
                    .getClasspath()
                    .from(conjureExecutableClasspath(executable).orElseGet(List::of));
            processWorkerSpec.getForkOptions().setMaxHeapSize("128m");
            processWorkerSpec.getForkOptions().setEnvironment(env);
        });
    }

    static Optional<List<File>> conjureExecutableClasspath(File executable) {
        Optional<StartScriptInfo> maybeJava = ReverseEngineerJavaStartScript.maybeParseStartScript(executable.toPath());

        if (maybeJava.isPresent()) {
            ReverseEngineerJavaStartScript.StartScriptInfo info = maybeJava.get();
            return Optional.of(info.classpath());
        }
        return Optional.empty();
    }

    private static Optional<Method> getMainMethod(String mainClassName) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(classLoader);
            TypePool typePool = TypePool.ClassLoading.of(classLoader);
            Class<?> mainClass = new ByteBuddy(ClassFileVersion.ofThisVm(ClassFileVersion.JAVA_V8))
                    .redefine(typePool.describe(mainClassName).resolve(), locator)
                    .name(getRedefinedMainMethodName(mainClassName))
                    .visit(new ForDeclaredMethods()
                            .invokable(
                                    ElementMatchers.any(),
                                    MemberSubstitution.relaxed()
                                            .method(ElementMatchers.is(System.class.getMethod("exit", int.class)))
                                            .replaceWith(GradleExecStubs.getStubMethod())))
                    .make()
                    .load(classLoader, Default.INJECTION.allowExistingTypes())
                    .getLoaded();
            return Optional.of(mainClass.getMethod("main", String[].class));

        } catch (ReflectiveOperationException e) {
            log.warn("Failed to get main method {}", mainClassName, e);
            return Optional.empty();
        }
    }

    private static String getRedefinedMainMethodName(String mainClassName) {
        return mainClassName + "RedefinedForGradleConjure";
    }

    private static final class ExternalProcessConjureRunner implements ConjureRunner {

        private final File executable;

        ExternalProcessConjureRunner(File executable) {
            this.executable = executable;
        }

        @Override
        public void invoke(
                ExecOperations execOperations, String failedTo, List<String> unloggedArgs, List<String> loggedArgs) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            List<String> combinedArgs = ImmutableList.<String>builder()
                    .add(executable.getAbsolutePath())
                    .addAll(unloggedArgs)
                    .addAll(loggedArgs)
                    .build();
            ExecResult execResult = execOperations.exec(execSpec -> {
                log.info("Running with args: {}", loggedArgs);
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

        InProcessConjureRunner(File executable, Method mainMethod) {
            this.executable = executable;
            this.mainMethod = mainMethod;
        }

        @Override
        public void invoke(
                ExecOperations _execOperations, String failedTo, List<String> unloggedArgs, List<String> loggedArgs) {
            List<String> combinedArgs = ImmutableList.<String>builder()
                    .addAll(unloggedArgs)
                    .addAll(loggedArgs)
                    .build();
            log.info("Running in-process java with args: {}", loggedArgs);

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
    }
}
