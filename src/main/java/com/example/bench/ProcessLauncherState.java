/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.bench;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.openjdk.jmh.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

public class ProcessLauncherState {

	private static final String DEFAULT_MAIN = "org.springframework.boot.loader.JarLauncher";

	private static final String APPLICATION_MAIN = "io.projectriff.invoker.JavaFunctionInvokerApplication";

	private static final Logger log = LoggerFactory.getLogger(ProcessLauncherState.class);

	private Process started;
	private List<String> args = new ArrayList<>();
	private static List<String> DEFAULT_JVM_ARGS = Arrays.asList("-cp", "");
	private File home;
	private String mainClass = DEFAULT_MAIN;
	private int length;
	private boolean exploded = false;
	private int classpath = 0;
	private String jar = "/target/java-function-invoker-0.0.7-SNAPSHOT-exec.jar";
	private String projectHome;

	private BufferedReader buffer;

	public ProcessLauncherState(String projectHome, String dir, String... args) {
		if (projectHome.endsWith("/")) {
			projectHome = projectHome.substring(0, projectHome.length() - 1);
		}
		this.projectHome = projectHome;
		this.args.add(System.getProperty("java.home") + "/bin/java");
		this.args.addAll(DEFAULT_JVM_ARGS);
		String vendor = System.getProperty("java.vendor", "").toLowerCase();
		if (vendor.contains("ibm") || vendor.contains("j9")) {
			this.args.addAll(Arrays.asList("-Xms32m", "-Xquickstart", "-Xshareclasses",
					"-Xscmx128m"));
		}
		else {
			this.args.addAll(Arrays.asList("-XX:TieredStopAtLevel=1"));
		}
		this.classpath = this.args.indexOf("-cp") + 1;
		if (System.getProperty("bench.args") != null) {
			this.args.addAll(Arrays.asList(System.getProperty("bench.args").split(" ")));
		}
		this.length = this.args.size();
		this.args.add("--server.port=0");
		this.args.add("--function.uri=file:"
				+ StringUtils.cleanPath(
						new File(projectHome + "/target/test-classes").getAbsolutePath())
				+ ",app:classpath?handler=io.projectriff.functions.Doubler");
		// new File(System.getProperty("user.home") +
		// "/.m2/repository/io/spring/sample/function-sample-pof/1.0.0.BUILD-SNAPSHOT/function-sample-pof-1.0.0.BUILD-SNAPSHOT-exec.jar")
		// .getAbsolutePath()
		// + "?handler=functions.Greeter"
		// + "&main=functions.Application");
		this.args.addAll(Arrays.asList(args));
		this.home = new File(dir);
	}

	public void jvmArgs(String... args) {
		this.args.addAll(1, Arrays.asList(args));
		this.classpath += args.length;
		this.length += args.length;
	}

	public void args(String... args) {
		this.args.addAll(Arrays.asList(args));
	}

	public void setApplicationMain() {
		this.mainClass = APPLICATION_MAIN;
	}

	public void setExploded() {
		this.exploded = true;
	}

	private void unpack(String path, String jar) {
		File home = new File(path);
		ProcessBuilder builder = new ProcessBuilder(getJarExec(), "xf", jar);
		Process started = null;
		try {
			if (home.exists()) {
				FileSystemUtils.deleteRecursively(home);
			}
			home.mkdirs();
			builder.directory(home);
			builder.redirectErrorStream(true);
			started = builder.start();
			started.waitFor();
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed", e);
		}
		finally {
			if (started != null && started.isAlive()) {
				started.destroy();
			}
		}
	}

	private String getJarExec() {
		String home = System.getProperty("java.home");
		String jar = home + "/../bin/jar";
		if (new File(jar).exists()) {
			return jar;
		}
		jar = home + "/../bin/jar.exe";
		if (new File(jar).exists()) {
			return jar;
		}
		return home + "/bin/jar";
	}

	private String getClasspath() {
		if (this.exploded) {
			unpack(new File(this.home, "unpacked").getAbsolutePath(),
					new File(this.projectHome, this.jar).getAbsolutePath());
			String basedir = this.home.getAbsolutePath() + "/unpacked";
			StringBuilder builder = new StringBuilder();
			builder.append(basedir);
			if (!this.mainClass.equals(DEFAULT_MAIN)) {
				builder.append(File.pathSeparator);
				builder.append(basedir + "/BOOT-INF/classes");
				try {
					PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
					Resource[] resolved = resolver
							.getResources("file:" + basedir + "/BOOT-INF/lib/*.jar");
					for (Resource archive : resolved) {
						builder.append(File.pathSeparator);
						builder.append(file(archive.getURL().toString()));
					}
				}
				catch (Exception e) {
					throw new IllegalStateException("Cannot find archive", e);
				}
			}
			log.debug("Classpath: " + builder);
			return builder.toString();
		}
		return new File(this.projectHome + this.jar).getAbsolutePath();
	}

	private String file(String path) {
		if (path.endsWith("!/")) {
			path = path.substring(0, path.length() - 2);
		}
		if (path.startsWith("jar:")) {
			path = path.substring("jar:".length());
		}
		if (path.startsWith("file:")) {
			path = path.substring("file:".length());
		}
		return path;
	}

	public void before() throws Exception {
		String path = getClasspath();
		if (!path.contains(File.pathSeparator) && path.endsWith(".jar")) {
			args.set(this.classpath - 1, "-jar");
		}
		args.set(this.classpath, path);
	}

	public void after() throws Exception {
		if (started != null && started.isAlive()) {
			System.err.println(
					"Stopped " + mainClass + ": " + started.destroyForcibly().waitFor());
		}
	}

	private BufferedReader getBuffer() {
		return this.buffer;
	}

	public void run() throws Exception {
		List<String> args = new ArrayList<>(this.args);
		args.add(this.length, this.mainClass);
		ProcessBuilder builder = new ProcessBuilder(args);
		home.mkdirs();
		builder.directory(home);
		builder.redirectErrorStream(true);
		customize(builder);
		if (!"false".equals(System.getProperty("debug", "false"))) {
			System.err.println("Running: " + Utils.join(args, " "));
		}
		if (this.buffer != null) {
			drain();
			this.buffer.close();
		}
		started = builder.start();
		InputStream stream = started.getInputStream();
		this.buffer = new BufferedReader(new InputStreamReader(stream));
		monitor();
	}

	protected void customize(ProcessBuilder builder) {
	}

	protected void monitor() throws IOException {
		// use this method to wait for an app to start
		output(getBuffer(), ".*Started application.*");
	}

	protected void drain() throws IOException {
		output(getBuffer(), null);
	}

	protected static void output(BufferedReader br, String marker) throws IOException {
		StringBuilder sb = new StringBuilder();
		String line = null;
		if (!"false".equals(System.getProperty("debug", "false"))) {
			System.err
					.println("Scanning for: " + (marker == null ? "<nothing>" : marker));
		}
		while ((marker != null || br.ready()) && (line = br.readLine()) != null
				&& (marker == null || !line.matches(marker))) {
			sb.append(line + System.getProperty("line.separator"));
			if (!"false".equals(System.getProperty("debug", "false"))) {
				System.out.println(line);
			}
			line = null;
		}
		if (line != null) {
			if (!"false".equals(System.getProperty("debug", "false"))) {
				System.out.println(line);
			}
			sb.append(line + System.getProperty("line.separator"));
		}
		System.out.println(sb.toString());
	}

	public File getHome() {
		return home;
	}
}
