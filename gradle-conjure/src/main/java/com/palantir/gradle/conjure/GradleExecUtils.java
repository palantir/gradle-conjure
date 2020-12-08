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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
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
import org.gradle.process.ExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GradleExecUtils {
    private static final Logger log = LoggerFactory.getLogger(GradleExecUtils.class);

    static void exec(
            Project project, String failedTo, File executable, List<String> unloggedArgs, List<String> loggedArgs) {

        // We run java things *in-process* to save ~1sec JVM startup time (helpful if there are 100 conjure projects)
        Optional<ReverseEngineerJavaStartScript.StartScriptInfo> maybeJava =
                ReverseEngineerJavaStartScript.maybeParseStartScript(executable.toPath());
        if (maybeJava.isPresent()) {
            ReverseEngineerJavaStartScript.StartScriptInfo info = maybeJava.get();
            project.getLogger().info("Running in-process java with args: {}", loggedArgs);

            try (URLClassLoader classLoader =
                    new ChildFirstUrlClassLoader(info.classpathUrls(), GradleExecUtils.class.getClassLoader())) {
                Optional<Method> mainMethod = getMainMethod(classLoader, info.mainClass());
                if (mainMethod.isPresent()) {
                    List<String> combinedArgs = ImmutableList.<String>builder()
                            .addAll(unloggedArgs)
                            .addAll(loggedArgs)
                            .build();

                    runJavaCodeInProcess(failedTo, combinedArgs, mainMethod.get());
                    return;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        execNormally(project, failedTo, executable, unloggedArgs, loggedArgs);
    }

    private static void execNormally(
            Project project, String failedTo, File executable, List<String> unloggedArgs, List<String> loggedArgs) {
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

        if (execResult.getExitValue() != 0) {
            throw new RuntimeException(String.format(
                    "Failed to %s. The command '%s' failed with exit code %d. Output:\n%s",
                    failedTo, combinedArgs, execResult.getExitValue(), output.toString()));
        }
    }

    private static void runJavaCodeInProcess(String failedTo, List<String> combinedArgs, Method mainMethod) {
        try {
            String[] args = combinedArgs.toArray(new String[] {});
            mainMethod.invoke(null, new Object[] {args});
        } catch (Throwable t) {
            Throwable rootCause = Throwables.getRootCause(t);
            if (rootCause instanceof GradleExecStubs.ExitInvoked) {
                int exitStatus = ((GradleExecStubs.ExitInvoked) rootCause).getExitStatus();
                if (exitStatus == 0) {
                    // Exit status zero, we're good to go!
                    return;
                }
                // the error message from a generator attempting to call exit 1 looks pretty gross
                throw new RuntimeException(String.format(
                        "Failed to %s. The command '%s' failed with exit code 1. Output above.",
                        failedTo, combinedArgs));
            }
            throw new RuntimeException(
                    String.format("Failed to %s. The command '%s' failed.", failedTo, combinedArgs), t);
        }
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

    private GradleExecUtils() {}
}
