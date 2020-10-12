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
import java.util.Optional;
import java.util.PropertyPermission;
import org.gradle.api.Project;
import org.gradle.process.ExecResult;

final class GradleExecUtils {
    private GradleExecUtils() {}

    static void exec(
            Project project, String failedTo, File executable, List<String> unloggedArgs, List<String> loggedArgs) {
        Optional<ReverseEngineerJavaStartScript.StartScriptInfo> maybeJava =
                ReverseEngineerJavaStartScript.maybeParseStartScript(executable.toPath());
        if (maybeJava.isPresent()) {
            ReverseEngineerJavaStartScript.StartScriptInfo info = maybeJava.get();
            project.getLogger().info("Running java process with args: {}", loggedArgs);

            List<String> combinedArgs = ImmutableList.<String>builder()
                    .addAll(unloggedArgs)
                    .addAll(loggedArgs)
                    .build();
            runJavaCodeInProcess(failedTo, combinedArgs, info);
        } else {

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            ExecResult execResult;
            List<String> combinedArgs = ImmutableList.<String>builder()
                    .add(project.getRootProject().relativePath(executable))
                    .addAll(unloggedArgs)
                    .addAll(loggedArgs)
                    .build();

            execResult = project.exec(execSpec -> {
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

        URL[] jarUrls = info.classpath().stream()
                .map(f -> {
                    try {
                        return f.toURI().toURL();
                    } catch (MalformedURLException e1) {
                        throw new RuntimeException(e1);
                    }
                })
                .toArray(URL[]::new);

        PluginClassLoader classLoader = new PluginClassLoader(jarUrls);
        Policy.setPolicy(new SandboxSecurityPolicy());
        System.setSecurityManager(new SecurityManager());

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
                return;
            }

            throw new RuntimeException(
                    String.format("Failed to %s. The command '%s' failed.", failedTo, combinedArgs), e);
        }
    }

    public static final class PluginClassLoader extends URLClassLoader {
        public PluginClassLoader(URL[] jars) {
            super(jars, null, null);
        }
    }

    public static final class SandboxSecurityPolicy extends Policy {

        @Override
        public PermissionCollection getPermissions(ProtectionDomain domain) {
            if (isPlugin(domain)) {
                return pluginPermissions();
            } else {
                return applicationPermissions();
            }
        }

        private static boolean isPlugin(ProtectionDomain domain) {
            return domain.getClassLoader() instanceof PluginClassLoader;
        }

        private static PermissionCollection pluginPermissions() {
            // No permissions
            Permissions permissions = new Permissions();
            permissions.add(new PropertyPermission("*", "read"));
            permissions.add(new RuntimePermission("accessDeclaredMembers"));
            permissions.add(new RuntimePermission("getClassLoader"));
            permissions.add(new RuntimePermission("getenv.*"));
            permissions.add(new ReflectPermission("suppressAccessChecks"));
            permissions.add(new java.io.FilePermission("<<ALL FILES>>", "read,write"));
            return permissions;
        }

        private static PermissionCollection applicationPermissions() {
            Permissions permissions = new Permissions();
            permissions.add(new AllPermission());
            return permissions;
        }
    }
}
