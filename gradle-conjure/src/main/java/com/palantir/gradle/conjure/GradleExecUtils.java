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

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilePermission;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ReflectPermission;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.security.AllPermission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PropertyPermission;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.gradle.api.Project;
import org.gradle.process.ExecResult;

final class GradleExecUtils {

    private GradleExecUtils() {}

    static void exec(
            Project project, String failedTo, File executable, List<String> unloggedArgs, List<String> loggedArgs) {

        // We run java things *in-process* to save ~1sec JVM startup time (helpful if there are 100 conjure projects)
        // and run using already optimized JVM classes rather than go through a cold start interpreted mode over and
        // over again.
        Optional<ReverseEngineerJavaStartScript.StartScriptInfo> maybeJava =
                ReverseEngineerJavaStartScript.maybeParseStartScript(executable.toPath());
        if (maybeJava.isPresent()) {
            ReverseEngineerJavaStartScript.StartScriptInfo info = maybeJava.get();
            project.getLogger().info("Running in-process java with args: {}", loggedArgs);

            List<String> combinedArgs = ImmutableList.<String>builder()
                    .addAll(unloggedArgs)
                    .addAll(loggedArgs)
                    .build();
            runJavaCodeInProcess(failedTo, combinedArgs, info);
        } else {

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
    }

    private static void runJavaCodeInProcess(
            String failedTo, List<String> combinedArgs, ReverseEngineerJavaStartScript.StartScriptInfo info) {

        SandboxClassLoader classLoader = SandboxClassLoader.get(info.classpath());
        PreventSystemExitSecurityPolicy.installForThisJvm();

        try {
            Class<?> mainClass = classLoader.loadClass(info.mainClass());
            Method mainMethod = mainClass.getMethod("main", String[].class);
            String[] args = combinedArgs.toArray(new String[] {});
            mainMethod.invoke(null, new Object[] {args});
        } catch (NoSuchMethodException
                | ClassNotFoundException
                | IllegalAccessException
                | InvocationTargetException e) {
            if (e.getCause() instanceof AccessControlException
                    && e.getCause()
                            .getMessage()
                            .equals("access denied (\"java.lang.RuntimePermission\" \"exitVM.0\")")) {
                // we don't want generators to call System.exit(0) and terminate the entire Gradle daemon!
                return;
            }

            if (e.getCause() instanceof AccessControlException
                    && e.getCause()
                            .getMessage()
                            .equals("access denied (\"java.lang.RuntimePermission\" \"exitVM.1\")")) {
                // the error message from a generator attempting to call exit 1 looks pretty gross
                throw new RuntimeException(String.format(
                        "Failed to %s. The command '%s' failed with exit code 1. Output above.",
                        failedTo, combinedArgs));
            }

            throw new RuntimeException(
                    String.format("Failed to %s. The command '%s' failed.", failedTo, combinedArgs), e);
        }
    }

    private static final class SandboxClassLoader extends URLClassLoader {
        private static final Map<List<File>, SandboxClassLoader> memoizedClassloaders = new ConcurrentHashMap<>();

        private SandboxClassLoader(List<File> jars) {
            super(toUrls(jars), ClassLoader.getSystemClassLoader());
        }

        static SandboxClassLoader get(List<File> jars) {
            return memoizedClassloaders.computeIfAbsent(jars, SandboxClassLoader::new);
        }

        private static URL[] toUrls(List<File> jars) {
            return jars.stream()
                    .map(f -> {
                        try {
                            return f.toURI().toURL();
                        } catch (MalformedURLException e1) {
                            throw new RuntimeException(e1);
                        }
                    })
                    .toArray(URL[]::new);
        }
    }

    /**
     * The {@link java.security.Policy} is set globally for the entire JVM, so we set it once and need to make sure
     * the calling thread is unconstrained, while the sandbox thread is unable to kill the entire process.
     *
     * We're not really defending against adversarial conjure-generators here, I just don't want them
     * to call System.exit and kill the current gradle process.
     */
    private static final class PreventSystemExitSecurityPolicy extends Policy {
        private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
        private static final PreventSystemExitSecurityPolicy INSTANCE = new PreventSystemExitSecurityPolicy();

        private static final PermissionCollection ALLOW_ALL = allowAll();
        private static final PermissionCollection sandboxPerms = lockedDownPerms();

        private PreventSystemExitSecurityPolicy() {}

        static void installForThisJvm() {
            if (INSTALLED.compareAndSet(false, true)) {
                // we just assume that nobody else will overwrite the Policy!
                Policy.setPolicy(INSTANCE);

                // necessary otherwise our fancy new policy will never be checked
                if (System.getSecurityManager() == null) {
                    System.setSecurityManager(new SecurityManager());
                }
            }
        }

        @Override
        public PermissionCollection getPermissions(ProtectionDomain domain) {
            if (isSandboxThread(domain)) {
                return sandboxPerms;
            } else {
                return ALLOW_ALL;
            }
        }

        private static boolean isSandboxThread(ProtectionDomain domain) {
            return domain.getClassLoader() instanceof SandboxClassLoader;
        }

        private static Permissions lockedDownPerms() {
            Permissions lockedDownPerms = new Permissions();

            lockedDownPerms.add(new PropertyPermission("*", "read"));
            lockedDownPerms.add(new FilePermission("<<ALL FILES>>", "read,write"));
            lockedDownPerms.add(new ReflectPermission("suppressAccessChecks"));
            lockedDownPerms.add(new RuntimePermission("getenv.*"));
            lockedDownPerms.add(new RuntimePermission("accessDeclaredMembers"));
            lockedDownPerms.add(new RuntimePermission("getClassLoader"));
            lockedDownPerms.add(new RuntimePermission("modifyThread"));

            return lockedDownPerms;
        }

        private static PermissionCollection allowAll() {
            Permissions permissions = new Permissions();
            permissions.add(new AllPermission());
            return permissions;
        }
    }
}
